package no.grunnmur

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val CSRF_TOKEN_BYTES = 32
private const val DEFAULT_COOKIE_MAX_AGE = 30 * 24 * 60 * 60 // 30 dager

/**
 * Genererer et nytt tilfeldig CSRF-token (32 bytes, Base64url uten padding).
 */
fun generateCsrfToken(): String {
    val bytes = ByteArray(CSRF_TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Setter csrf_token-cookien paa responsen. Ikke httpOnly - maa vaere lesbar fra
 * JavaScript slik at frontend kan sende tokenet tilbake som X-CSRF-Token-header.
 *
 * @param secure Sett `Secure`-flagget (kun sendt over HTTPS). Bruk true i produksjon.
 * @param devMode Bruker SameSite=Lax i stedet for Strict (kreves for localhost-utvikling paa tvers av porter).
 * @param maxAge Levetid i sekunder. Default 30 dager (dagens konsensus paa tvers av appene).
 */
fun ApplicationCall.setCsrfCookie(
    token: String,
    secure: Boolean,
    devMode: Boolean = false,
    maxAge: Int = DEFAULT_COOKIE_MAX_AGE
) {
    response.cookies.append(
        Cookie(
            name = "csrf_token",
            value = token,
            httpOnly = false,
            secure = secure,
            path = "/",
            maxAge = maxAge,
            extensions = mapOf("SameSite" to if (devMode) "Lax" else "Strict")
        )
    )
}

/**
 * Setter auth_token-cookien (JWT) paa responsen. HttpOnly - utilgjengelig for JavaScript.
 *
 * @param secure Sett `Secure`-flagget (kun sendt over HTTPS). Bruk true i produksjon.
 * @param devMode Bruker SameSite=Lax i stedet for Strict (kreves for localhost-utvikling paa tvers av porter).
 * @param maxAge Levetid i sekunder. Default 30 dager. Bruk et kortere vindu for midlertidige
 * tokens (f.eks. under en 2FA-mellomsteg).
 */
fun ApplicationCall.setAuthCookie(
    jwt: String,
    secure: Boolean,
    devMode: Boolean = false,
    maxAge: Int = DEFAULT_COOKIE_MAX_AGE
) {
    response.cookies.append(
        Cookie(
            name = "auth_token",
            value = jwt,
            httpOnly = true,
            secure = secure,
            path = "/",
            maxAge = maxAge,
            extensions = mapOf("SameSite" to if (devMode) "Lax" else "Strict")
        )
    )
}

/**
 * Fjerner auth_token- og csrf_token-cookiene (logout). Setter maxAge=0 slik at
 * nettleseren sletter dem umiddelbart.
 */
fun ApplicationCall.clearAuthCookies() {
    response.cookies.append(Cookie(name = "auth_token", value = "", httpOnly = true, path = "/", maxAge = 0))
    response.cookies.append(Cookie(name = "csrf_token", value = "", httpOnly = false, path = "/", maxAge = 0))
}

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

        if (!MessageDigest.isEqual(csrfCookie.toByteArray(Charsets.UTF_8), csrfHeader.toByteArray(Charsets.UTF_8))) {
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
