package no.grunnmur

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*

/**
 * Hent bruker-ID fra JWT principal.
 * Returnerer null hvis ingen autentisering eller userId-claim mangler.
 */
fun io.ktor.server.application.ApplicationCall.getUserId(): Int? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()

/**
 * Krev bruker-ID fra JWT principal.
 * Kaster AuthenticationException hvis bruker ikke er autentisert eller userId mangler.
 */
fun io.ktor.server.application.ApplicationCall.requireUserId(): Int =
    getUserId() ?: throw AuthenticationException()

/**
 * Hent e-postadresse fra JWT principal.
 * Returnerer null hvis ingen autentisering eller email-claim mangler.
 */
fun io.ktor.server.application.ApplicationCall.getUserEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()
