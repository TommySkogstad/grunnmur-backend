package no.grunnmur

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Verktoey for tidssone-haandtering.
 * Bruker Europe/Oslo for aa sikre konsistent norsk tid uavhengig av server-tidssone.
 */
object TimeUtils {
    val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")

    /** ISO dato/tid-formatter (yyyy-MM-dd'T'HH:mm:ss) */
    val isoDateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** ISO dato-formatter (yyyy-MM-dd) */
    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Returnerer naaværende tid i Oslo-tidssone.
     */
    fun nowOslo(): LocalDateTime = LocalDateTime.now(OSLO_ZONE)

    /**
     * Formaterer dato/tid til lesbart format: yyyy-MM-dd HH:mm
     */
    fun formatDateTime(dt: LocalDateTime): String = dt.format(dateTimeFormatter)

    /**
     * Formaterer dato/tid til ISO-format.
     */
    fun formatDateTimeIso(dt: LocalDateTime): String =
        dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * Parser en dato- eller dato/tid-streng til LocalDateTime.
     * Stoetter ISO datetime (2026-03-15T14:30:00) og kun dato (2026-03-15 -> start av dagen).
     *
     * @throws IllegalArgumentException ved ugyldig datoformat
     */
    fun parseDateTime(value: String): LocalDateTime = try {
        if (value.contains('T')) {
            LocalDateTime.parse(value, isoDateTime)
        } else {
            LocalDate.parse(value, isoDate).atStartOfDay()
        }
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Ugyldig datoformat: $value", e)
    }
}
