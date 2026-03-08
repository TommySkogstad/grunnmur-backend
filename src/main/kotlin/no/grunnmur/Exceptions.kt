package no.grunnmur

/**
 * Exception for ugyldig parameter eller foresporsel.
 * Handteres av StatusPages -> 400 Bad Request.
 */
class BadRequestException(message: String = "Ugyldig foresporsel") : Exception(message)

/**
 * Exception for ressurs ikke funnet.
 * Handteres av StatusPages -> 404 Not Found.
 */
class NotFoundException(message: String = "Ressurs ikke funnet") : Exception(message)

/**
 * Exception for manglende tilgang.
 * Handteres av StatusPages -> 403 Forbidden.
 */
class ForbiddenException(message: String = "Ingen tilgang") : Exception(message)

/**
 * Exception for rate limiting.
 * Handteres av StatusPages -> 429 Too Many Requests.
 */
class RateLimitException(message: String = "For mange forespoersler. Proev igjen senere.") : Exception(message)

/**
 * Exception for autentiseringsfeil.
 * Handteres av StatusPages -> 401 Unauthorized.
 */
class AuthenticationException(message: String = "Autentisering feilet") : Exception(message)
