package no.grunnmur

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime

@Tag("integration")
@Testcontainers
class AuditLogServiceIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    private lateinit var database: Database
    private lateinit var service: AuditLogService

    @BeforeEach
    fun setUp() {
        database = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        transaction(database) {
            SchemaUtils.create(AuditLogs)
        }
        service = AuditLogService()
    }

    @AfterEach
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(AuditLogs)
        }
    }

    private fun insertWithCreatedAt(action: String, createdAt: LocalDateTime) {
        transaction(database) {
            AuditLogs.insert {
                it[AuditLogs.userId] = 1
                it[AuditLogs.userEmail] = "test@test.no"
                it[AuditLogs.action] = action
                it[AuditLogs.entityType] = "T"
                it[AuditLogs.createdAt] = createdAt
            }
        }
    }

    @Test
    fun `findAll startDate filtrerer korrekt mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("GAMMEL", now.minusDays(10))
        insertWithCreatedAt("NY", now)

        val cutoff = now.minusDays(1).toLocalDate().toString()
        val result = service.findAll(startDate = cutoff)

        assertEquals(1, result.size)
        assertEquals("NY", result[0].action)
    }

    @Test
    fun `findAll endDate filtrerer korrekt mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("GAMMEL", now.minusDays(10))
        insertWithCreatedAt("NY", now)

        val cutoff = now.minusDays(1).toLocalDate().toString()
        val result = service.findAll(endDate = cutoff)

        assertEquals(1, result.size)
        assertEquals("GAMMEL", result[0].action)
    }

    @Test
    fun `findAll datoperiode filtrerer korrekt mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("GAMMEL", now.minusDays(7))
        insertWithCreatedAt("MIDT", now.minusDays(3))
        insertWithCreatedAt("NY", now)

        val start = now.minusDays(5).toLocalDate().toString()
        val end = now.minusDays(1).toLocalDate().toString()
        val result = service.findAll(startDate = start, endDate = end)

        assertEquals(1, result.size)
        assertEquals("MIDT", result[0].action)
    }

    @Test
    fun `startDate inkluderer post klokken 00 30 Oslo-tid paa filtrert dato mot ekte PostgreSQL`() {
        val osloDate = LocalDate.of(2026, 5, 7)
        val startOfDayOslo = osloDate.atStartOfDay(TimeUtils.OSLO_ZONE).toLocalDateTime()
        insertWithCreatedAt("TIDLIG_MORGEN", startOfDayOslo.plusMinutes(30))
        insertWithCreatedAt("DAGEN_FOR", startOfDayOslo.minusMinutes(30))

        val result = service.findAll(startDate = "2026-05-07")

        assertEquals(1, result.size)
        assertEquals("TIDLIG_MORGEN", result[0].action)
    }

    @Test
    fun `endDate inkluderer post klokken 23 30 Oslo-tid paa filtrert dato mot ekte PostgreSQL`() {
        val osloDate = LocalDate.of(2026, 5, 7)
        val startOfDayOslo = osloDate.atStartOfDay(TimeUtils.OSLO_ZONE).toLocalDateTime()
        insertWithCreatedAt("SEN_KVELD", startOfDayOslo.plusHours(23).plusMinutes(30))
        insertWithCreatedAt("NESTE_DAG", startOfDayOslo.plusDays(1).plusMinutes(30))

        val result = service.findAll(endDate = "2026-05-07")

        assertEquals(1, result.size)
        assertEquals("SEN_KVELD", result[0].action)
    }

    @Test
    fun `cleanupOldLogs sletter rader eldre enn retentionDays mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("GAMMEL", now.minusDays(400))
        insertWithCreatedAt("NY", now)

        val deleted = service.cleanupOldLogs(retentionDays = 365)

        assertEquals(1, deleted)
        val remaining = service.findAll()
        assertEquals(1, remaining.size)
        assertEquals("NY", remaining[0].action)
    }

    @Test
    fun `cleanupOldLogs bevarer rader innenfor retentionDays mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("FOR_GAMMEL", now.minusDays(100))
        insertWithCreatedAt("NY_NOK", now.minusDays(30))

        val deleted = service.cleanupOldLogs(retentionDays = 60)

        assertEquals(1, deleted)
        val remaining = service.findAll()
        assertEquals(1, remaining.size)
        assertEquals("NY_NOK", remaining[0].action)
    }

    @Test
    fun `cleanupOldLogs returnerer 0 naar ingen rader er eldre enn retention mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("NY", now)

        val deleted = service.cleanupOldLogs(retentionDays = 365)

        assertEquals(0, deleted)
        assertEquals(1, service.findAll().size)
    }

    @Test
    fun `count og findAll gir konsistent resultat med datofilter mot ekte PostgreSQL`() {
        val now = TimeUtils.nowOslo()
        insertWithCreatedAt("GAMMEL", now.minusDays(10))
        insertWithCreatedAt("NY1", now)
        insertWithCreatedAt("NY2", now.minusHours(1))

        val cutoff = now.minusDays(1).toLocalDate().toString()
        val countResult = service.count(startDate = cutoff)
        val listResult = service.findAll(startDate = cutoff)

        assertEquals(2L, countResult)
        assertEquals(2, listResult.size)
        assertEquals(countResult, listResult.size.toLong())
    }
}
