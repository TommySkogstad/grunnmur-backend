package no.grunnmur

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource

@Tag("integration")
@Testcontainers
class FlywayMigrationIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    private fun dataSource(): DataSource = PGSimpleDataSource().apply {
        setUrl(postgres.jdbcUrl)
        user = postgres.username
        setPassword(postgres.password)
    }

    @Test
    fun `configure returnerer gyldig Flyway-instans mot ekte PostgreSQL`() {
        val flyway = FlywayMigration.configure(dataSource())
        assertNotNull(flyway)
        assertTrue(flyway.configuration.isBaselineOnMigrate)
        assertTrue(flyway.configuration.isCleanDisabled)
        assertEquals("0", flyway.configuration.baselineVersion.version)
    }

    @Test
    fun `migrate returnerer 0 uten migrasjoner mot ekte PostgreSQL`() {
        val count = FlywayMigration.migrate(dataSource())
        assertEquals(0, count)
    }

    @Test
    fun `migrate kjoerer PostgreSQL-spesifikk DDL med IDENTITY JSONB og TIMESTAMPTZ`() {
        val flyway = FlywayMigration.configure(
            dataSource(),
            locations = listOf("classpath:db/pg-testmigration")
        )
        val result = flyway.migrate()
        assertEquals(1, result.migrationsExecuted)
    }
}
