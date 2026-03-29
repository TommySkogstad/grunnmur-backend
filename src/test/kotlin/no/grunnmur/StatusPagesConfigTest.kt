package no.grunnmur

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StatusPagesConfigTest {

    private fun ApplicationTestBuilder.setupApp() {
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) { grunnmurExceptionHandlers() }
            routing {
                get("/bad-request") { throw BadRequestException("Ugyldig verdi") }
                get("/bad-request-default") { throw BadRequestException() }
                get("/not-found") { throw NotFoundException("Fant ikke bruker") }
                get("/forbidden") { throw ForbiddenException("Kun admin") }
                get("/rate-limit") { throw RateLimitException("Vent litt") }
                get("/auth") { throw AuthenticationException("Ugyldig token") }
                get("/illegal-arg") { throw IllegalArgumentException("Ugyldig argument") }
                get("/unexpected") { throw RuntimeException("Noe gikk galt") }
            }
        }
    }

    @Nested
    inner class ExceptionMapping {

        @Test
        fun `BadRequestException gir 400 med melding`() = testApplication {
            setupApp()
            val response = client.get("/bad-request")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Ugyldig verdi")
        }

        @Test
        fun `BadRequestException uten melding bruker standard`() = testApplication {
            setupApp()
            val response = client.get("/bad-request-default")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Ugyldig foresporsel")
        }

        @Test
        fun `NotFoundException gir 404`() = testApplication {
            setupApp()
            val response = client.get("/not-found")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "Fant ikke bruker")
        }

        @Test
        fun `ForbiddenException gir 403`() = testApplication {
            setupApp()
            val response = client.get("/forbidden")
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertContains(response.bodyAsText(), "Kun admin")
        }

        @Test
        fun `RateLimitException gir 429`() = testApplication {
            setupApp()
            val response = client.get("/rate-limit")
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertContains(response.bodyAsText(), "Vent litt")
        }

        @Test
        fun `AuthenticationException gir 401`() = testApplication {
            setupApp()
            val response = client.get("/auth")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Autentisering feilet")
        }

        @Test
        fun `IllegalArgumentException gir 400`() = testApplication {
            setupApp()
            val response = client.get("/illegal-arg")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Ugyldig argument")
        }

        @Test
        fun `Uventet exception gir 500`() = testApplication {
            setupApp()
            val response = client.get("/unexpected")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }

    @Nested
    inner class ProduksjonsModus {

        @Test
        fun `500-feil i dev viser feilmelding`() = testApplication {
            setupApp()
            val response = client.get("/unexpected")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertContains(response.bodyAsText(), "Noe gikk galt")
        }
    }
}
