package no.grunnmur

import org.flywaydb.core.api.configuration.FluentConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource

class FlywayMigrationTest {

    private lateinit var dataSource: DataSource

    @BeforeEach
    fun setUp() {
        dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:testdb_${System.nanoTime()};DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
    }

    @Nested
    inner class Configure {
        @Test
        fun `configure returnerer gyldig Flyway-instans`() {
            val flyway = FlywayMigration.configure(dataSource)
            assertNotNull(flyway)
        }

        @Test
        fun `baselineOnMigrate er aktivert som standard`() {
            val flyway = FlywayMigration.configure(dataSource)
            assertTrue(flyway.configuration.isBaselineOnMigrate)
        }

        @Test
        fun `baselineVersion er 0 som standard`() {
            val flyway = FlywayMigration.configure(dataSource)
            assertEquals("0", flyway.configuration.baselineVersion.version)
        }

        @Test
        fun `cleanDisabled er aktivert som standard`() {
            val flyway = FlywayMigration.configure(dataSource)
            assertTrue(flyway.configuration.isCleanDisabled)
        }

        @Test
        fun `standard location er classpath db migration`() {
            val flyway = FlywayMigration.configure(dataSource)
            val locations = flyway.configuration.locations.map { it.descriptor }
            assertTrue(
                locations.any { it.contains("db/migration") },
                "Forventet 'db/migration' i locations, men fant: $locations"
            )
        }

        @Test
        fun `dataSource er satt korrekt`() {
            val flyway = FlywayMigration.configure(dataSource)
            assertNotNull(flyway.configuration.dataSource)
        }
    }

    @Nested
    inner class Migrate {
        @Test
        fun `migrate returnerer 0 uten migrasjoner`() {
            val count = FlywayMigration.migrate(dataSource)
            assertEquals(0, count)
        }

        @Test
        fun `migrate kjorer tilgjengelige migrasjoner`() {
            // Bruker en location med en testmigrasjon
            val flyway = FlywayMigration.configure(dataSource, locations = listOf("classpath:db/testmigration"))
            val result = flyway.migrate()
            assertEquals(1, result.migrationsExecuted)
        }
    }
}
