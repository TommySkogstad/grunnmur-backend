package no.grunnmur

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * Exposed-tabelldefinisjón for audit logging.
 * Alle apper bruker samme tabellstruktur for konsistens.
 *
 * Migrering (SQL):
 * ```sql
 * CREATE TABLE IF NOT EXISTS audit_logs (
 *     id SERIAL PRIMARY KEY,
 *     user_id INTEGER,
 *     user_email VARCHAR(255) NOT NULL DEFAULT 'system',
 *     action VARCHAR(100) NOT NULL,
 *     entity_type VARCHAR(100) NOT NULL,
 *     entity_id INTEGER,
 *     details TEXT,
 *     ip_address VARCHAR(45),
 *     created_at TIMESTAMP NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
 * ```
 */
object AuditLogs : Table("audit_logs") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").nullable()
    val userEmail = varchar("user_email", 255).default("system")
    val action = varchar("action", 100)
    val entityType = varchar("entity_type", 100)
    val entityId = integer("entity_id").nullable()
    val details = text("details").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, entityType, entityId)
        index(isUnique = false, createdAt)
        index(isUnique = false, userId)
    }
}
