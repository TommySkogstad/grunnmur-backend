package no.grunnmur

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RouteUtilsTest {

    private fun ApplicationTestBuilder.setupApp() {
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) { grunnmurExceptionHandlers() }
            routing {
                get("/items/{id}") {
                    val id = call.requireIntParam("id")
                    call.respondText("Item $id")
                }
                get("/slugs/{slug}") {
                    val slug = call.requireParam("slug")
                    call.respondText("Slug: $slug")
                }
                get("/rate-check/{allowed}") {
                    val allowed = call.parameters["allowed"] == "true"
                    call.checkRateLimit(allowed)
                    call.respondText("OK")
                }
                get("/rate-check-custom") {
                    call.checkRateLimit(false, "Du har brukt opp kvoten")
                    call.respondText("OK")
                }
                get("/client-ip") {
                    call.respondText(call.getClientIp())
                }
            }
        }
    }

    @Nested
    inner class RequireIntParam {

        @Test
        fun `gyldig heltall returneres`() = testApplication {
            setupApp()
            val response = client.get("/items/42")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Item 42", response.bodyAsText())
        }

        @Test
        fun `ikke-numerisk gir 400`() = testApplication {
            setupApp()
            val response = client.get("/items/abc")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Ugyldig id")
        }

        @Test
        fun `desimaltall gir 400`() = testApplication {
            setupApp()
            val response = client.get("/items/3.14")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Nested
    inner class RequireParam {

        @Test
        fun `gyldig streng returneres`() = testApplication {
            setupApp()
            val response = client.get("/slugs/hello-world")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Slug: hello-world", response.bodyAsText())
        }
    }

    @Nested
    inner class CheckRateLimit {

        @Test
        fun `tillatt passerer`() = testApplication {
            setupApp()
            val response = client.get("/rate-check/true")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `ikke tillatt gir 429`() = testApplication {
            setupApp()
            val response = client.get("/rate-check/false")
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
        }

        @Test
        fun `egendefinert melding brukes`() = testApplication {
            setupApp()
            val response = client.get("/rate-check-custom")
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertContains(response.bodyAsText(), "Du har brukt opp kvoten")
        }
    }

    @Nested
    inner class GetClientIp {

        @Test
        fun `CF-Connecting-IP prioriteres`() = testApplication {
            setupApp()
            val response = client.get("/client-ip") {
                header("CF-Connecting-IP", "1.2.3.4")
                header("X-Real-IP", "5.6.7.8")
                header("X-Forwarded-For", "9.10.11.12")
            }
            assertEquals("1.2.3.4", response.bodyAsText())
        }

        @Test
        fun `X-Real-IP brukes naar CF-Connecting-IP mangler`() = testApplication {
            setupApp()
            val response = client.get("/client-ip") {
                header("X-Real-IP", "5.6.7.8")
                header("X-Forwarded-For", "9.10.11.12")
            }
            assertEquals("5.6.7.8", response.bodyAsText())
        }

        @Test
        fun `X-Forwarded-For foerste IP brukes`() = testApplication {
            setupApp()
            val response = client.get("/client-ip") {
                header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3")
            }
            assertEquals("1.1.1.1", response.bodyAsText())
        }

        @Test
        fun `fallback til remoteAddress`() = testApplication {
            setupApp()
            val response = client.get("/client-ip")
            assertEquals(HttpStatusCode.OK, response.status)
            val ip = response.bodyAsText()
            assert(ip.isNotBlank()) { "IP skal ikke vaere tom" }
        }
    }
}
