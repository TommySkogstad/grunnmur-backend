package no.grunnmur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Service for revisjonslogging av handlinger.
 * Bruker string-baserte action/entityType for fleksibilitet — apper kan bruke egne enums.
 *
 * Bruk:
 * ```
 * val auditLog = AuditLogService()
 *
 * // Med strenger
 * auditLog.log(userId = 1, userEmail = "admin@example.com", action = "CREATE", entityType = "POST", entityId = 42)
 *
 * // Med app-spesifikke enums
 * auditLog.log(userId = 1, userEmail = "admin@example.com", action = MyAction.CREATE.name, entityType = MyEntity.POST.name)
 * ```
 */
class AuditLogService {
    private val log = LoggerFactory.getLogger(AuditLogService::class.java)

    companion object {
        const val MAX_LIMIT = 1000
    }

    /**
     * Logger en handling til revisjonsloggen.
     * Feil i logging stopper ikke hovedoperasjonen.
     */
    suspend fun log(
        userId: Int?,
        userEmail: String = "system",
        action: String,
        entityType: String,
        entityId: Long? = null,
        details: String? = null,
        ipAddress: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                transaction {
                    AuditLogs.insert {
                        it[AuditLogs.userId] = userId
                        it[AuditLogs.userEmail] = userEmail
                        it[AuditLogs.action] = action
                        it[AuditLogs.entityType] = entityType
                        it[AuditLogs.entityId] = entityId
                        it[AuditLogs.details] = details
                        it[AuditLogs.ipAddress] = ipAddress
                        it[AuditLogs.createdAt] = TimeUtils.nowOslo()
                    }
                }
            } catch (e: Exception) {
                log.error("Kunne ikke logge handling: ${e.message}", e)
            }
        }
    }

    /**
     * Henter revisjonslogger med filtrering og paginering.
     */
    suspend fun findAll(
        action: String? = null,
        entityType: String? = null,
        userId: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int = 100,
        offset: Long = 0
    ): List<AuditLogEntry> {
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)
        return withContext(Dispatchers.IO) {
            transaction {
                val query = AuditLogs.selectAll()
                applyFilters(query, action, entityType, userId, startDate, endDate)
                query
                    .orderBy(AuditLogs.createdAt, SortOrder.DESC)
                    .limit(effectiveLimit).offset(offset)
                    .map { it.toAuditLogEntry() }
            }
        }
    }

    /**
     * Teller revisjonslogger med filtrering.
     */
    suspend fun count(
        action: String? = null,
        entityType: String? = null,
        userId: Int? = null,
        startDate: String? = null,
        endDate: String? = null
    ): Long {
        return withContext(Dispatchers.IO) {
            transaction {
                val query = AuditLogs.selectAll()
                applyFilters(query, action, entityType, userId, startDate, endDate)
                query.count()
            }
        }
    }

    /**
     * Sletter gamle logger basert paa retention-policy.
     * @return Antall slettede rader
     */
    suspend fun cleanupOldLogs(retentionDays: Int = 365): Int {
        val cutoff = TimeUtils.nowOslo().minusDays(retentionDays.toLong())
        return withContext(Dispatchers.IO) {
            transaction {
                val deleted = AuditLogs.deleteWhere { createdAt less cutoff }
                if (deleted > 0) {
                    log.info("Slettet $deleted audit logs eldre enn $retentionDays dager")
                }
                deleted
            }
        }
    }

    private fun applyFilters(
        query: Query,
        action: String?,
        entityType: String?,
        userId: Int?,
        startDate: String?,
        endDate: String?
    ) {
        action?.let { query.andWhere { AuditLogs.action eq it } }
        entityType?.let { query.andWhere { AuditLogs.entityType eq it } }
        userId?.let { query.andWhere { AuditLogs.userId eq it } }
        startDate?.let { dateStr ->
            parseLocalDate(dateStr)?.let { date ->
                val startDateTime = date.atStartOfDay(TimeUtils.OSLO_ZONE).toLocalDateTime()
                query.andWhere { AuditLogs.createdAt greaterEq startDateTime }
            }
        }
        endDate?.let { dateStr ->
            parseLocalDate(dateStr)?.let { date ->
                val endDateTime = date.atStartOfDay(TimeUtils.OSLO_ZONE).toLocalDateTime()
                    .with(java.time.LocalTime.MAX)
                query.andWhere { AuditLogs.createdAt lessEq endDateTime }
            }
        }
    }

    private fun parseLocalDate(dateStr: String): java.time.LocalDate? {
        return try {
            java.time.LocalDate.parse(dateStr)
        } catch (e: Exception) {
            log.warn("Ugyldig datoformat: '$dateStr'", e)
            null
        }
    }

    private fun ResultRow.toAuditLogEntry() = AuditLogEntry(
        id = this[AuditLogs.id],
        userId = this[AuditLogs.userId],
        userEmail = this[AuditLogs.userEmail],
        action = this[AuditLogs.action],
        entityType = this[AuditLogs.entityType],
        entityId = this[AuditLogs.entityId],
        details = this[AuditLogs.details],
        ipAddress = this[AuditLogs.ipAddress],
        timestamp = TimeUtils.formatDateTime(this[AuditLogs.createdAt])
    )
}

/**
 * DTO for en audit-logg-entry.
 */
data class AuditLogEntry(
    val id: Long,
    val userId: Int?,
    val userEmail: String,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val details: String?,
    val ipAddress: String?,
    val timestamp: String
)
