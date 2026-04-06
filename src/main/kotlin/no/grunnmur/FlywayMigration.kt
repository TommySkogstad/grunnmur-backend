package no.grunnmur

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Konfigurasjon og kjoring av Flyway-migrasjoner.
 * Gir en felles standard for alle apper i portefoljen.
 */
object FlywayMigration {

    private val logger = LoggerFactory.getLogger(FlywayMigration::class.java)

    /**
     * Konfigurerer og returnerer en Flyway-instans med fornuftige standardverdier.
     *
     * @param dataSource databasetilkoblingen
     * @param locations migrasjonsplasseringer (standard: classpath:db/migration)
     * @return konfigurert Flyway-instans klar til bruk
     */
    fun configure(
        dataSource: DataSource,
        locations: List<String> = listOf("classpath:db/migration")
    ): Flyway {
        val config = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)

        // Flyway 11.20+ skanner classpath:db/callback som standard.
        // Deaktiver dette for å unngå feil i fat JARs.
        try {
            val method = config.javaClass.getMethod("callbackLocations", Array<String>::class.java)
            method.invoke(config, emptyArray<String>())
        } catch (_: Exception) {
            // Eldre Flyway-versjoner har ikke denne metoden
        }

        return config.load()
    }

    /**
     * Kjorer alle ventende migrasjoner mot databasen.
     *
     * @param dataSource databasetilkoblingen
     * @param locations migrasjonsplasseringer (standard: classpath:db/migration)
     * @return antall migrasjoner som ble kjort
     */
    fun migrate(
        dataSource: DataSource,
        locations: List<String> = listOf("classpath:db/migration")
    ): Int {
        val flyway = configure(dataSource, locations)
        val result = flyway.migrate()
        logger.info("Flyway-migrering fullfort: {} migrasjoner kjort", result.migrationsExecuted)
        return result.migrationsExecuted
    }
}
