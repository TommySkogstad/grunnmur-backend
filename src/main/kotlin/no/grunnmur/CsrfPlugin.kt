package no.grunnmur

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * CSRF-beskyttelse plugin for Ktor.
 * Validerer at X-CSRF-Token header matcher csrf_token cookie for muterende operasjoner.
 *
 * Bruk:
 * ```
 * fun Application.configureCsrf() {
 *     install(GrunnmurCsrf) {
 *         exemptPaths = setOf("/api/auth/login", "/api/auth/request-code", "/api/health")
 *         authCookieName = "auth_token"
 *     }
 * }
 * ```
 */
val GrunnmurCsrf = createApplicationPlugin(name = "GrunnmurCsrf", createConfiguration = ::CsrfConfig) {
    val config = pluginConfig
    val modifyingMethods = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Patch)

    onCall { call ->
        val method = call.request.httpMethod
        val path = call.request.path()

        if (method !in modifyingMethods) return@onCall

        if (config.exemptPaths.any { path == it || path.startsWith("$it/") }) return@onCall

        val hasAuthCookie = call.request.cookies[config.authCookieName] != null
        if (!hasAuthCookie) return@onCall

        val csrfCookie = call.request.cookies[config.csrfCookieName]
        val csrfHeader = call.request.headers[config.csrfHeaderName]

        if (csrfCookie == null || csrfHeader == null) {
            call.application.log.warn("CSRF-validering feilet: Mangler token. Path: $path")
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "CSRF-token mangler"))
            return@onCall
        }

        if (csrfCookie != csrfHeader) {
            call.application.log.warn("CSRF-validering feilet: Token mismatch. Path: $path")
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Ugyldig CSRF-token"))
            return@onCall
        }
    }
}

class CsrfConfig {
    /** Stier som er unntatt fra CSRF-validering (f.eks. login, offentlige endepunkter) */
    var exemptPaths: Set<String> = emptySet()

    /** Navnet paa auth-cookien (brukes for aa sjekke om forespoerselen er autentisert) */
    var authCookieName: String = "auth_token"

    /** Navnet paa CSRF-cookien */
    var csrfCookieName: String = "csrf_token"

    /** Navnet paa CSRF-headeren */
    var csrfHeaderName: String = "X-CSRF-Token"
}
