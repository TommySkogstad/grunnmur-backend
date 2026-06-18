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
 *     id BIGSERIAL PRIMARY KEY,
 *     user_id INTEGER,
 *     user_email VARCHAR(255) NOT NULL DEFAULT 'system',
 *     action VARCHAR(100) NOT NULL,
 *     entity_type VARCHAR(100) NOT NULL,
 *     entity_id BIGINT,
 *     details TEXT,
 *     ip_address VARCHAR(45),
 *     created_at TIMESTAMP NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);
 * CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
 * ```
 *
 * Oppgradering av eksisterende installasjoner (id var tidligere SERIAL/INTEGER):
 * `id` er nå `BIGINT` (BIGSERIAL) for å unngå overflow ved langtidsdrift. Apper
 * med en eldre `audit_logs`-tabell opprettet med `id SERIAL PRIMARY KEY` har
 * fortsatt en INTEGER-kolonne (tak ~2,1 mrd) i databasen. Exposed leser INTEGER
 * via `long("id")` uten feil (Int→Long widening), så oppgraderingen krasjer
 * ikke — men for faktisk å få BIGINT-taket må hver app kjøre en Flyway-migrasjon:
 * ```sql
 * ALTER SEQUENCE audit_logs_id_seq AS bigint;  -- PostgreSQL 10+
 * ALTER TABLE audit_logs ALTER COLUMN id TYPE BIGINT;
 * ```
 */
object AuditLogs : Table("audit_logs") {
    val id = long("id").autoIncrement()
    val userId = integer("user_id").nullable()
    val userEmail = varchar("user_email", 255).default("system")
    val action = varchar("action", 100)
    val entityType = varchar("entity_type", 100)
    val entityId = long("entity_id").nullable()
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
