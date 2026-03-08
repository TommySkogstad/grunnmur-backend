package no.grunnmur

import io.ktor.server.application.*

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
    message: String = "For mange forespoersler. Proev igjen senere."
) {
    if (!allowed) {
        throw RateLimitException(message)
    }
}
