package no.grunnmur

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate limiter med sliding window og automatisk cleanup.
 *
 * Bruk:
 * ```
 * val loginLimiter = RateLimiter(maxAttempts = 5, windowMs = 300_000)
 * val searchLimiter = RateLimiter(maxAttempts = 60, windowMs = 60_000)
 *
 * if (!loginLimiter.isAllowed(clientIp)) {
 *     throw RateLimitException()
 * }
 * ```
 *
 * @param maxAttempts Maks antall forsoek innenfor vinduet
 * @param windowMs Tidsvindu i millisekunder
 * @param maxEntries Maks antall entries foer tvungen cleanup (beskytter mot minnelekkasje)
 */
class RateLimiter(
    private val maxAttempts: Int = 5,
    val windowMs: Long = 300_000,
    private val maxEntries: Int = 10_000
) {
    private data class AttemptRecord(val count: Int, val windowStart: Long)

    private val attempts = ConcurrentHashMap<String, AttemptRecord>()
    @Volatile private var lastCleanup = System.currentTimeMillis()
    private val cleanupIntervalMs = 60_000L

    /**
     * Sjekker om en noekkel er tillatt og registrerer forsoekets.
     * Returnerer true hvis tillatt, false hvis blokkert.
     */
    fun isAllowed(key: String): Boolean {
        val now = System.currentTimeMillis()
        cleanup(now)

        val record = attempts[key]

        if (record == null || now - record.windowStart > windowMs) {
            attempts[key] = AttemptRecord(1, now)
            return true
        }

        if (record.count >= maxAttempts) {
            return false
        }

        attempts[key] = record.copy(count = record.count + 1)
        return true
    }

    /**
     * Nullstiller telleren for en spesifikk noekkel.
     * Typisk brukt etter vellykket login.
     */
    fun reset(key: String) {
        attempts.remove(key)
    }

    /**
     * Returnerer antall gjenstaaende forsoek for en noekkel.
     */
    fun remainingAttempts(key: String): Int {
        val now = System.currentTimeMillis()
        val record = attempts[key] ?: return maxAttempts

        if (now - record.windowStart > windowMs) {
            return maxAttempts
        }

        return (maxAttempts - record.count).coerceAtLeast(0)
    }

    /**
     * Returnerer antall sekunder til vinduet utloeper for en blokkert noekkel.
     * Returnerer null hvis noekkel ikke er blokkert.
     */
    fun retryAfterSeconds(key: String): Long? {
        val now = System.currentTimeMillis()
        val record = attempts[key] ?: return null

        if (now - record.windowStart > windowMs) return null
        if (record.count < maxAttempts) return null

        val windowEnd = record.windowStart + windowMs
        val remainingMs = windowEnd - now
        return (remainingMs / 1000).coerceAtLeast(1)
    }

    /**
     * Nullstiller alle tellere. Kun for testing.
     */
    fun clear() {
        attempts.clear()
    }

    /**
     * Returnerer antall aktive entries (for monitorering).
     */
    fun size(): Int = attempts.size

    @Synchronized
    private fun cleanup(now: Long) {
        if (now - lastCleanup < cleanupIntervalMs) return
        lastCleanup = now

        attempts.entries.removeIf { now - it.value.windowStart > windowMs }

        if (attempts.size > maxEntries) {
            val sorted = attempts.entries.sortedBy { it.value.windowStart }
            val toRemove = sorted.take(attempts.size - maxEntries)
            toRemove.forEach { attempts.remove(it.key) }
        }
    }
}
