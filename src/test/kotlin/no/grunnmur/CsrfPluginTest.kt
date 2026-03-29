package no.grunnmur

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class CsrfPluginTest {

    private fun ApplicationTestBuilder.setupApp(
        exemptPaths: Set<String> = emptySet(),
        authCookieName: String = "auth_token",
        csrfCookieName: String = "csrf_token",
        csrfHeaderName: String = "X-CSRF-Token"
    ) {
        application {
            install(ContentNegotiation) { json() }
            install(GrunnmurCsrf) {
                this.exemptPaths = exemptPaths
                this.authCookieName = authCookieName
                this.csrfCookieName = csrfCookieName
                this.csrfHeaderName = csrfHeaderName
            }
            routing {
                post("/api/test") { call.respondText("OK") }
                put("/api/test") { call.respondText("OK") }
                delete("/api/test") { call.respondText("OK") }
                patch("/api/test") { call.respondText("OK") }
                get("/api/test") { call.respondText("OK") }
                post("/api/auth/login") { call.respondText("OK") }
                post("/api/auth/login/sub") { call.respondText("OK") }
            }
        }
    }

    @Nested
    inner class ModifyingMethods {

        @Test
        fun `GET passerer uten CSRF-validering`() = testApplication {
            setupApp()
            val response = client.get("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `POST uten auth-cookie passerer`() = testApplication {
            setupApp()
            val response = client.post("/api/test")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `POST med auth-cookie men uten CSRF-tokens gir 403`() = testApplication {
            setupApp()
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertContains(response.bodyAsText(), "CSRF-token mangler")
        }

        @Test
        fun `POST med auth-cookie og mismatchede CSRF-tokens gir 403`() = testApplication {
            setupApp()
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token; csrf_token=token-a")
                header("X-CSRF-Token", "token-b")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertContains(response.bodyAsText(), "Ugyldig CSRF-token")
        }

        @Test
        fun `POST med matchende CSRF-tokens passerer`() = testApplication {
            setupApp()
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token; csrf_token=valid-token")
                header("X-CSRF-Token", "valid-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `PUT valideres paa samme maate`() = testApplication {
            setupApp()
            val response = client.put("/api/test") {
                header("Cookie", "auth_token=jwt-token; csrf_token=valid-token")
                header("X-CSRF-Token", "valid-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `DELETE valideres paa samme maate`() = testApplication {
            setupApp()
            val response = client.delete("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

        @Test
        fun `PATCH valideres paa samme maate`() = testApplication {
            setupApp()
            val response = client.patch("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Nested
    inner class ExemptPaths {

        @Test
        fun `exempt path hopper over CSRF-validering`() = testApplication {
            setupApp(exemptPaths = setOf("/api/auth/login"))
            val response = client.post("/api/auth/login") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `sub-path av exempt path hopper over CSRF-validering`() = testApplication {
            setupApp(exemptPaths = setOf("/api/auth/login"))
            val response = client.post("/api/auth/login/sub") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `ikke-exempt path krever CSRF`() = testApplication {
            setupApp(exemptPaths = setOf("/api/auth/login"))
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Nested
    inner class Konfigurasjon {

        @Test
        fun `egendefinert auth-cookie-navn respekteres`() = testApplication {
            setupApp(authCookieName = "session_id")
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `egendefinert CSRF-cookie og header-navn respekteres`() = testApplication {
            setupApp(csrfCookieName = "my_csrf", csrfHeaderName = "X-My-CSRF")
            val response = client.post("/api/test") {
                header("Cookie", "auth_token=jwt-token; my_csrf=token-123")
                header("X-My-CSRF", "token-123")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
