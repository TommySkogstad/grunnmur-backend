package no.grunnmur

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeUtilsTest {

    @Test
    fun `nowOslo returnerer tid i Oslo-sonen`() {
        val now = TimeUtils.nowOslo()
        assertNotNull(now)

        val systemNow = LocalDateTime.now(ZoneId.of("Europe/Oslo"))
        assertTrue(
            java.time.Duration.between(now, systemNow).abs().seconds < 2,
            "Tidene skal vaere innenfor 2 sekunder"
        )
    }

    @Test
    fun `formatDateTime gir riktig format`() {
        val dt = LocalDateTime.of(2026, 3, 7, 14, 30, 0)
        assertEquals("2026-03-07 14:30", TimeUtils.formatDateTime(dt))
    }

    @Test
    fun `formatDateTimeIso gir ISO-format`() {
        val dt = LocalDateTime.of(2026, 3, 7, 14, 30, 0)
        assertEquals("2026-03-07T14:30:00", TimeUtils.formatDateTimeIso(dt))
    }

    @Test
    fun `OSLO_ZONE er Europe Oslo`() {
        assertEquals(ZoneId.of("Europe/Oslo"), TimeUtils.OSLO_ZONE)
    }

    @Test
    fun `parseDateTime parser ISO datetime med T-separator`() {
        val result = TimeUtils.parseDateTime("2026-03-15T14:30:00")
        assertEquals(LocalDateTime.of(2026, 3, 15, 14, 30, 0), result)
    }

    @Test
    fun `parseDateTime parser ISO datetime med sekunder og millisekunder`() {
        val result = TimeUtils.parseDateTime("2026-03-15T14:30:45.123")
        assertEquals(LocalDateTime.of(2026, 3, 15, 14, 30, 45, 123_000_000), result)
    }

    @Test
    fun `parseDateTime parser kun dato og gir start av dagen`() {
        val result = TimeUtils.parseDateTime("2026-03-15")
        assertEquals(LocalDateTime.of(2026, 3, 15, 0, 0, 0), result)
    }

    @Test
    fun `parseDateTime kaster IllegalArgumentException ved ugyldig format`() {
        val ex = assertThrows<IllegalArgumentException> {
            TimeUtils.parseDateTime("ikke-en-dato")
        }
        assertTrue(ex.message!!.contains("Ugyldig datoformat"))
    }

    @Test
    fun `parseDateTime kaster IllegalArgumentException ved tom streng`() {
        assertThrows<IllegalArgumentException> {
            TimeUtils.parseDateTime("")
        }
    }
}
