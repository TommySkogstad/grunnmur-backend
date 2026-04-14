package no.grunnmur

import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * Henter en heltallsparameter fra URL-path, eller kaster BadRequestException.
 *
 * Bruk:
 * ```
 * val id = call.requireIntParam("id")
 * ```
 */
fun ApplicationCall.requireIntParam(name: String): Int {
    return parameters[name]?.toIntOrNull()
        ?: throw BadRequestException("Ugyldig $name")
}

/**
 * Henter en strengparameter fra URL-path, eller kaster BadRequestException.
 *
 * Bruk:
 * ```
 * val slug = call.requireParam("slug")
 * ```
 */
fun ApplicationCall.requireParam(name: String): String {
    return parameters[name]
        ?: throw BadRequestException("Mangler parameter: $name")
}

/**
 * Sjekker rate limiting og kaster RateLimitException hvis grensen er naadd.
 *
 * Bruk:
 * ```
 * call.checkRateLimit(rateLimiter.isAllowed(clientIp))
 * ```
 */
fun ApplicationCall.checkRateLimit(
    allowed: Boolean,
    message: String = "For mange forespoersler. Proev igjen senere.",
    retryAfterSeconds: Long? = null
) {
    if (!allowed) {
        throw RateLimitException(message, retryAfterSeconds)
    }
}

/**
 * Henter klientens IP-adresse fra proxy-headere.
 *
 * Sjekker i rekkefoelge:
 * 1. CF-Connecting-IP (Cloudflare)
 * 2. X-Real-IP (Nginx)
 * 3. X-Forwarded-For (standard proxy-header, foerste IP)
 * 4. remoteAddress (direkte tilkobling / fallback)
 */
fun ApplicationCall.getClientIp(): String {
    return request.header("CF-Connecting-IP")
        ?: request.header("X-Real-IP")
        ?: request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteAddress
}
