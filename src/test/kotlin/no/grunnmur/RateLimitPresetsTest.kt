package no.grunnmur

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitPresetsTest {

    // --- CompositeRateLimiter ---

    @Test
    fun `CompositeRateLimiter tillater naar alle limitere tillater`() {
        val limiter = CompositeRateLimiter(
            RateLimiter(maxAttempts = 5, windowMs = 60_000),
            RateLimiter(maxAttempts = 10, windowMs = 3_600_000)
        )
        assertTrue(limiter.isAllowed("ip1"))
    }

    @Test
    fun `CompositeRateLimiter blokkerer naar strengeste limiter er naadd`() {
        val limiter = CompositeRateLimiter(
            RateLimiter(maxAttempts = 2, windowMs = 60_000),
            RateLimiter(maxAttempts = 10, windowMs = 3_600_000)
        )
        assertTrue(limiter.isAllowed("ip1"))
        assertTrue(limiter.isAllowed("ip1"))
        assertFalse(limiter.isAllowed("ip1"), "Skal blokkeres av per-minutt limiter")
    }

    @Test
    fun `CompositeRateLimiter blokkerer naar time-limiter er naadd`() {
        val shortWindow = RateLimiter(maxAttempts = 3, windowMs = 50)
        val hourWindow = RateLimiter(maxAttempts = 5, windowMs = 3_600_000)
        val limiter = CompositeRateLimiter(shortWindow, hourWindow)

        // Bruk opp 3 i kort vindu (ogsaa 3 i time-vinduet)
        repeat(3) { assertTrue(limiter.isAllowed("ip1")) }
        // 4. kall: blokkert av kort vindu, men time-vindu registrerer ogsaa (4 totalt)
        assertFalse(limiter.isAllowed("ip1"), "Blokkert av kort vindu")

        // Vent til kort vindu utloeper
        Thread.sleep(80)

        // Time-vinduet har 4 forsoek, 1 igjen
        assertTrue(limiter.isAllowed("ip1"), "5. forsoek i time-vindu — siste tillatte")
        assertFalse(limiter.isAllowed("ip1"), "6. forsoek — blokkert av time-limiter")
    }

    @Test
    fun `CompositeRateLimiter reset nullstiller alle limitere`() {
        val limiter = CompositeRateLimiter(
            RateLimiter(maxAttempts = 2, windowMs = 60_000),
            RateLimiter(maxAttempts = 5, windowMs = 3_600_000)
        )
        repeat(2) { limiter.isAllowed("ip1") }
        assertFalse(limiter.isAllowed("ip1"))

        limiter.reset("ip1")
        assertTrue(limiter.isAllowed("ip1"), "Skal vaere tillatt etter reset")
    }

    @Test
    fun `CompositeRateLimiter remainingAttempts returnerer minimum`() {
        val minuteLimiter = RateLimiter(maxAttempts = 3, windowMs = 60_000)
        val hourLimiter = RateLimiter(maxAttempts = 10, windowMs = 3_600_000)
        val limiter = CompositeRateLimiter(minuteLimiter, hourLimiter)

        assertEquals(3, limiter.remainingAttempts("ip1"), "Minimum av 3 og 10 er 3")

        limiter.isAllowed("ip1")
        assertEquals(2, limiter.remainingAttempts("ip1"), "Minimum av 2 og 9 er 2")
    }

    // --- authRateLimiter preset ---

    @Test
    fun `authRateLimiter blokkerer etter 5 forsoek per minutt`() {
        val limiter = authRateLimiter()
        repeat(5) { assertTrue(limiter.isAllowed("ip1"), "Forsoek ${it + 1} skal vaere tillatt") }
        assertFalse(limiter.isAllowed("ip1"), "6. forsoek skal blokkeres")
    }

    @Test
    fun `authRateLimiter blokkerer etter 10 forsoek per time`() {
        // Bruk kort vindu for minutt-limiter saa vi kan teste time-limiter isolert
        val limiter = authRateLimiter(
            perMinuteMax = 5,
            perMinuteWindowMs = 50,
            perHourMax = 10,
            perHourWindowMs = 3_600_000
        )

        // Foerste bolk: 5 forsoek, vent til minutt-vindu utloeper
        repeat(5) { assertTrue(limiter.isAllowed("ip1")) }
        Thread.sleep(80)

        // Andre bolk: 5 forsoek til (totalt 10 i time-vindu)
        repeat(5) { assertTrue(limiter.isAllowed("ip1")) }
        Thread.sleep(80)

        // Naa har vi brukt 10 i time-vinduet — skal blokkeres
        assertFalse(limiter.isAllowed("ip1"), "11. forsoek skal blokkeres av time-limiter")
    }

    // --- apiRateLimiterAuthenticated preset ---

    @Test
    fun `apiRateLimiterAuthenticated tillater 60 requests per minutt`() {
        val limiter = apiRateLimiterAuthenticated()
        repeat(60) { assertTrue(limiter.isAllowed("ip1"), "Request ${it + 1} skal vaere tillatt") }
        assertFalse(limiter.isAllowed("ip1"), "61. request skal blokkeres")
    }

    // --- apiRateLimiterAnonymous preset ---

    @Test
    fun `apiRateLimiterAnonymous tillater 20 requests per minutt`() {
        val limiter = apiRateLimiterAnonymous()
        repeat(20) { assertTrue(limiter.isAllowed("ip1"), "Request ${it + 1} skal vaere tillatt") }
        assertFalse(limiter.isAllowed("ip1"), "21. request skal blokkeres")
    }

    // --- retryAfterSeconds ---

    @Test
    fun `retryAfterSeconds returnerer sekunder til vindu utloeper`() {
        val limiter = RateLimiter(maxAttempts = 1, windowMs = 60_000)
        limiter.isAllowed("ip1")
        assertFalse(limiter.isAllowed("ip1"))

        val retryAfter = limiter.retryAfterSeconds("ip1")
        assertNotNull(retryAfter)
        assertTrue(retryAfter in 1..60, "Retry-After skal vaere mellom 1 og 60 sekunder, var $retryAfter")
    }

    @Test
    fun `retryAfterSeconds returnerer null naar ikke blokkert`() {
        val limiter = RateLimiter(maxAttempts = 5, windowMs = 60_000)
        val retryAfter = limiter.retryAfterSeconds("ip1")
        assertEquals(null, retryAfter, "Skal returnere null naar key ikke er blokkert")
    }

    @Test
    fun `CompositeRateLimiter retryAfterSeconds returnerer maksimum av alle limitere`() {
        val shortLimiter = RateLimiter(maxAttempts = 1, windowMs = 30_000)
        val longLimiter = RateLimiter(maxAttempts = 1, windowMs = 3_600_000)
        val composite = CompositeRateLimiter(shortLimiter, longLimiter)

        composite.isAllowed("ip1")
        assertFalse(composite.isAllowed("ip1"))

        val retryAfter = composite.retryAfterSeconds("ip1")
        assertNotNull(retryAfter)
        // Skal vaere maks av de to, altsaa naer 3600 sekunder
        assertTrue(retryAfter > 30, "Skal returnere maks retry-after, var $retryAfter")
    }

    // --- Edge cases ---

    @Test
    fun `blokkert kall i limiter A registreres fortsatt i limiter B`() {
        val minuteLimiter = RateLimiter(maxAttempts = 2, windowMs = 60_000)
        val hourLimiter = RateLimiter(maxAttempts = 5, windowMs = 3_600_000)
        val limiter = CompositeRateLimiter(minuteLimiter, hourLimiter)

        // Bruk opp minutt-limiteren
        assertTrue(limiter.isAllowed("ip1"))  // minutt: 1/2, time: 1/5
        assertTrue(limiter.isAllowed("ip1"))  // minutt: 2/2, time: 2/5

        // 3. kall: minutt blokkerer (returnerer false uten aa oeke), time registrerer (3/5)
        assertFalse(limiter.isAllowed("ip1"))

        // Verifiser at time-limiteren har registrert forsoekets
        assertEquals(2, hourLimiter.remainingAttempts("ip1"), "Time-limiter skal ha 3 brukt, 2 igjen")
        // Minutt-limiter har IKKE registrert det blokkerte forsoekets (count forblir 2)
        assertEquals(0, minuteLimiter.remainingAttempts("ip1"), "Minutt-limiter skal ha 0 igjen")
    }

    @Test
    fun `CompositeRateLimiter krever minst en limiter`() {
        try {
            CompositeRateLimiter()
            assertTrue(false, "Skal kaste IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("minst"), "Feilmelding skal nevne 'minst'")
        }
    }

    // --- RateLimitException med retryAfterSeconds ---

    @Test
    fun `RateLimitException har retryAfterSeconds property`() {
        val ex = RateLimitException(retryAfterSeconds = 42)
        assertEquals(42L, ex.retryAfterSeconds)
    }

    @Test
    fun `RateLimitException har null retryAfterSeconds som default`() {
        val ex = RateLimitException()
        assertEquals(null, ex.retryAfterSeconds)
    }

    // --- checkRateLimit med retryAfterSeconds ---

    @Test
    fun `checkRateLimit kaster exception med retryAfterSeconds`() {
        try {
            checkRateLimitWithRetryAfter(allowed = false, retryAfterSeconds = 30)
            assertTrue(false, "Skal kaste RateLimitException")
        } catch (e: RateLimitException) {
            assertEquals(30L, e.retryAfterSeconds)
        }
    }
}

/**
 * Hjelpefunksjon for aa teste checkRateLimit-logikken uten Ktor ApplicationCall.
 * Speiler logikken i RouteUtils.checkRateLimit.
 */
private fun checkRateLimitWithRetryAfter(
    allowed: Boolean,
    message: String = "For mange forespoersler. Proev igjen senere.",
    retryAfterSeconds: Long? = null
) {
    if (!allowed) {
        throw RateLimitException(message, retryAfterSeconds)
    }
}
