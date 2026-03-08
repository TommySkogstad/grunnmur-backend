package no.grunnmur

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/**
 * Installerer standard exception-handlers for grunnmur-exceptions.
 *
 * Bruk:
 * ```
 * install(StatusPages) {
 *     grunnmurExceptionHandlers()
 *
 *     // App-spesifikke handlers kan legges til her
 *     exception<MyCustomException> { call, cause -> ... }
 * }
 * ```
 */
fun StatusPagesConfig.grunnmurExceptionHandlers() {
    exception<BadRequestException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (cause.message ?: "Ugyldig foresporsel"))
        )
    }

    exception<NotFoundException> { call, cause ->
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (cause.message ?: "Ressurs ikke funnet"))
        )
    }

    exception<ForbiddenException> { call, cause ->
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (cause.message ?: "Ingen tilgang"))
        )
    }

    exception<RateLimitException> { call, cause ->
        call.respond(
            HttpStatusCode.TooManyRequests,
            mapOf("error" to (cause.message ?: "For mange forespoersler"))
        )
    }

    exception<AuthenticationException> { call, cause ->
        call.application.log.warn("Autentiseringsfeil: ${cause.message}")
        call.respond(
            HttpStatusCode.Unauthorized,
            mapOf("error" to "Autentisering feilet")
        )
    }

    exception<IllegalArgumentException> { call, cause ->
        call.application.log.warn("Ugyldig input: ${cause.message}")
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (cause.message ?: "Ugyldig foresporsel"))
        )
    }

    exception<Throwable> { call, cause ->
        call.application.log.error("Uventet feil", cause)
        val isProduction = System.getenv("KTOR_ENV") == "production"
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf(
                "error" to if (isProduction) {
                    "En feil oppstod. Vennligst proev igjen senere."
                } else {
                    cause.message ?: "Intern serverfeil"
                }
            )
        )
    }
}
