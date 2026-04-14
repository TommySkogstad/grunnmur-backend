package no.grunnmur

import java.security.MessageDigest

/**
 * Sammensatt rate limiter som sjekker flere vinduer (alle maa tillate).
 * Brukes for auth-ruter som trenger baade per-minutt og per-time begrensning.
 *
 * Bruk:
 * ```
 * val limiter = CompositeRateLimiter(
 *     RateLimiter(maxAttempts = 5, windowMs = 60_000),   // 5/min
 *     RateLimiter(maxAttempts = 10, windowMs = 3_600_000) // 10/time
 * )
 * if (!limiter.isAllowed(clientIp)) {
 *     throw RateLimitException(retryAfterSeconds = limiter.retryAfterSeconds(clientIp))
 * }
 * ```
 */
class CompositeRateLimiter(private vararg val limiters: RateLimiter) {

    init {
        require(limiters.isNotEmpty()) { "CompositeRateLimiter krever minst én limiter" }
    }

    /**
     * Sjekker om en noekkel er tillatt av ALLE limitere.
     * Registrerer forsoeket i alle limitere uavhengig av resultat.
     */
    fun isAllowed(key: String): Boolean {
        var allowed = true
        for (limiter in limiters) {
            if (!limiter.isAllowed(key)) {
                allowed = false
            }
        }
        return allowed
    }

    /**
     * Nullstiller telleren for en noekkel i alle limitere.
     */
    fun reset(key: String) {
        limiters.forEach { it.reset(key) }
    }

    /**
     * Returnerer minimum gjenstaaende forsoek paa tvers av alle limitere.
     */
    fun remainingAttempts(key: String): Int {
        return limiters.minOf { it.remainingAttempts(key) }
    }

    /**
     * Returnerer maksimum retry-after-tid paa tvers av alle limitere.
     * Returnerer null hvis ingen limiter er blokkert.
     */
    fun retryAfterSeconds(key: String): Long? {
        return limiters.mapNotNull { it.retryAfterSeconds(key) }.maxOrNull()
    }
}

/**
 * Ferdigkonfigurert rate limiter for auth-ruter (send-otp, verify-otp).
 * Standard: 5 forsoek/minutt + 10 forsoek/time per IP.
 *
 * Bruk i Ktor:
 * ```
 * val authLimiter = authRateLimiter()
 *
 * post("/api/auth/send-otp") {
 *     val ip = call.getClientIp()
 *     call.checkRateLimit(
 *         authLimiter.isAllowed(ip),
 *         retryAfterSeconds = authLimiter.retryAfterSeconds(ip)
 *     )
 *     // ...
 * }
 * ```
 */
fun authRateLimiter(
    perMinuteMax: Int = 5,
    perMinuteWindowMs: Long = 60_000,
    perHourMax: Int = 10,
    perHourWindowMs: Long = 3_600_000
): CompositeRateLimiter = CompositeRateLimiter(
    RateLimiter(maxAttempts = perMinuteMax, windowMs = perMinuteWindowMs),
    RateLimiter(maxAttempts = perHourMax, windowMs = perHourWindowMs)
)

/**
 * Ferdigkonfigurert rate limiter for autentiserte API-ruter.
 * Standard: 60 requests/minutt per IP.
 *
 * Bruk i Ktor:
 * ```
 * val apiLimiter = apiRateLimiterAuthenticated()
 *
 * get("/api/data") {
 *     val ip = call.getClientIp()
 *     call.checkRateLimit(apiLimiter.isAllowed(ip))
 *     // ...
 * }
 * ```
 */
fun apiRateLimiterAuthenticated(
    maxRequests: Int = 60,
    windowMs: Long = 60_000
): RateLimiter = RateLimiter(maxAttempts = maxRequests, windowMs = windowMs)

/**
 * Ferdigkonfigurert rate limiter for uautentiserte API-ruter.
 * Standard: 20 requests/minutt per IP.
 *
 * Bruk i Ktor:
 * ```
 * val anonLimiter = apiRateLimiterAnonymous()
 *
 * get("/api/public") {
 *     val ip = call.getClientIp()
 *     call.checkRateLimit(anonLimiter.isAllowed(ip))
 *     // ...
 * }
 * ```
 */
fun apiRateLimiterAnonymous(
    maxRequests: Int = 20,
    windowMs: Long = 60_000
): RateLimiter = RateLimiter(maxAttempts = maxRequests, windowMs = windowMs)

/**
 * Rate limiter som kombinerer IP-basert og identifikator-basert limiting.
 * Begge sjekkes alltid — den strengeste vinner (begge maa tillate).
 * Identifikatorer (telefonnummer/e-post) hashes med SHA-256 saa de ikke lagres i klartekst.
 *
 * Bruk:
 * ```
 * val limiter = authRateLimiterWithIdentifier()
 *
 * post("/api/auth/send-otp") {
 *     val ip = call.getClientIp()
 *     val phone = call.receive<OtpRequest>().phone
 *     call.checkRateLimit(
 *         limiter.isAllowed(ip, phone),
 *         retryAfterSeconds = limiter.retryAfterSeconds(ip, phone)
 *     )
 * }
 * ```
 */
class AuthRateLimiter(
    private val ipLimiter: CompositeRateLimiter,
    private val identifierLimiter: RateLimiter
) {
    private val salt = java.util.UUID.randomUUID().toString()
    /**
     * Sjekker om baade IP og identifikator er tillatt.
     * Registrerer forsoeket i begge uavhengig av resultat.
     */
    fun isAllowed(ip: String, identifier: String): Boolean {
        val hashedId = hashIdentifier(identifier)
        val ipAllowed = ipLimiter.isAllowed(ip)
        val idAllowed = identifierLimiter.isAllowed(hashedId)
        return ipAllowed && idAllowed
    }

    /**
     * Nullstiller tellerne for baade IP og identifikator.
     */
    fun reset(ip: String, identifier: String) {
        ipLimiter.reset(ip)
        identifierLimiter.reset(hashIdentifier(identifier))
    }

    /**
     * Returnerer minimum gjenstaaende forsoek paa tvers av IP og identifikator.
     */
    fun remainingAttempts(ip: String, identifier: String): Int {
        return minOf(
            ipLimiter.remainingAttempts(ip),
            identifierLimiter.remainingAttempts(hashIdentifier(identifier))
        )
    }

    /**
     * Returnerer maksimum retry-after-tid paa tvers av IP og identifikator.
     * Returnerer null hvis ingen er blokkert.
     */
    fun retryAfterSeconds(ip: String, identifier: String): Long? {
        val ipRetry = ipLimiter.retryAfterSeconds(ip)
        val idRetry = identifierLimiter.retryAfterSeconds(hashIdentifier(identifier))
        return listOfNotNull(ipRetry, idRetry).maxOrNull()
    }

    private fun hashIdentifier(identifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$salt:$identifier".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Ferdigkonfigurert rate limiter for auth-ruter med identifikator-basert limiting.
 * Kombinerer IP-basert (5/min + 10/time) og identifikator-basert (5/15min).
 *
 * Bruk i Ktor:
 * ```
 * val authLimiter = authRateLimiterWithIdentifier()
 *
 * post("/api/auth/send-otp") {
 *     val ip = call.getClientIp()
 *     val phone = call.receive<OtpRequest>().phone
 *     call.checkRateLimit(
 *         authLimiter.isAllowed(ip, phone),
 *         retryAfterSeconds = authLimiter.retryAfterSeconds(ip, phone)
 *     )
 * }
 * ```
 */
fun authRateLimiterWithIdentifier(
    perMinuteMax: Int = 5,
    perMinuteWindowMs: Long = 60_000,
    perHourMax: Int = 10,
    perHourWindowMs: Long = 3_600_000,
    perIdentifierMax: Int = 5,
    perIdentifierWindowMs: Long = 900_000
): AuthRateLimiter = AuthRateLimiter(
    ipLimiter = CompositeRateLimiter(
        RateLimiter(maxAttempts = perMinuteMax, windowMs = perMinuteWindowMs),
        RateLimiter(maxAttempts = perHourMax, windowMs = perHourWindowMs)
    ),
    identifierLimiter = RateLimiter(maxAttempts = perIdentifierMax, windowMs = perIdentifierWindowMs)
)
