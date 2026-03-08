package no.grunnmur

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun `foerste forsoek er tillatt`() {
        val limiter = RateLimiter(maxAttempts = 5)
        assertTrue(limiter.isAllowed("test-key"))
    }

    @Test
    fun `forsoek opp til maxAttempts er tillatt`() {
        val limiter = RateLimiter(maxAttempts = 3)
        assertTrue(limiter.isAllowed("key1"))
        assertTrue(limiter.isAllowed("key1"))
        assertTrue(limiter.isAllowed("key1"))
    }

    @Test
    fun `forsoek over maxAttempts er blokkert`() {
        val limiter = RateLimiter(maxAttempts = 3)
        repeat(3) { limiter.isAllowed("key1") }
        assertFalse(limiter.isAllowed("key1"), "Fjerde forsoek skal vaere blokkert")
        assertFalse(limiter.isAllowed("key1"), "Femte forsoek skal ogsaa vaere blokkert")
    }

    @Test
    fun `reset nullstiller for en key`() {
        val limiter = RateLimiter(maxAttempts = 2)
        repeat(2) { limiter.isAllowed("key1") }
        assertFalse(limiter.isAllowed("key1"), "Skal vaere blokkert foer reset")

        limiter.reset("key1")
        assertTrue(limiter.isAllowed("key1"), "Skal vaere tillatt etter reset")
    }

    @Test
    fun `reset paavirker ikke andre keys`() {
        val limiter = RateLimiter(maxAttempts = 2)
        repeat(2) { limiter.isAllowed("key1") }
        repeat(2) { limiter.isAllowed("key2") }

        limiter.reset("key1")
        assertTrue(limiter.isAllowed("key1"), "key1 skal vaere tillatt etter reset")
        assertFalse(limiter.isAllowed("key2"), "key2 skal fortsatt vaere blokkert")
    }

    @Test
    fun `ulike keys er uavhengige`() {
        val limiter = RateLimiter(maxAttempts = 2)
        repeat(2) { limiter.isAllowed("key1") }
        assertFalse(limiter.isAllowed("key1"), "key1 skal vaere blokkert")
        assertTrue(limiter.isAllowed("key2"), "key2 skal vaere tillatt")
    }

    @Test
    fun `ny window starter etter windowMs`() {
        val limiter = RateLimiter(maxAttempts = 2, windowMs = 100)
        repeat(2) { limiter.isAllowed("key1") }
        assertFalse(limiter.isAllowed("key1"), "Skal vaere blokkert i foerste vindu")

        Thread.sleep(150)
        assertTrue(limiter.isAllowed("key1"), "Skal vaere tillatt etter at vinduet har utloept")
    }

    @Test
    fun `window-utloep gir nye forsoek`() {
        val limiter = RateLimiter(maxAttempts = 1, windowMs = 80)
        assertTrue(limiter.isAllowed("key1"))
        assertFalse(limiter.isAllowed("key1"))

        Thread.sleep(100)
        assertTrue(limiter.isAllowed("key1"), "Nytt vindu skal tillate forsoek igjen")
        assertFalse(limiter.isAllowed("key1"), "Andre forsoek i nytt vindu skal blokkeres")
    }

    @Test
    fun `maxAttempts lik 1 blokkerer etter foerste forsoek`() {
        val limiter = RateLimiter(maxAttempts = 1)
        assertTrue(limiter.isAllowed("key1"))
        assertFalse(limiter.isAllowed("key1"))
    }

    @Test
    fun `remainingAttempts returnerer riktig verdi`() {
        val limiter = RateLimiter(maxAttempts = 3)
        assertEquals(3, limiter.remainingAttempts("key1"))

        limiter.isAllowed("key1")
        assertEquals(2, limiter.remainingAttempts("key1"))

        limiter.isAllowed("key1")
        assertEquals(1, limiter.remainingAttempts("key1"))

        limiter.isAllowed("key1")
        assertEquals(0, limiter.remainingAttempts("key1"))
    }

    @Test
    fun `size returnerer antall aktive entries`() {
        val limiter = RateLimiter(maxAttempts = 5)
        assertEquals(0, limiter.size())

        limiter.isAllowed("key1")
        assertEquals(1, limiter.size())

        limiter.isAllowed("key2")
        assertEquals(2, limiter.size())

        limiter.reset("key1")
        assertEquals(1, limiter.size())
    }
}
