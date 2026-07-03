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
                get("/rate-limit-retry") { throw RateLimitException("Vent litt", retryAfterSeconds = 30) }
                get("/auth") { throw AuthenticationException("Ugyldig token") }
                get("/github-api") { throw GitHubApiException("GitHub svarte 503", statusCode = 503) }
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
        fun `RateLimitException med retryAfterSeconds setter Retry-After-header`() = testApplication {
            setupApp()
            val response = client.get("/rate-limit-retry")
            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertEquals("30", response.headers[HttpHeaders.RetryAfter])
        }

        @Test
        fun `AuthenticationException gir 401`() = testApplication {
            setupApp()
            val response = client.get("/auth")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Autentisering feilet")
        }

        @Test
        fun `GitHubApiException gir 502`() = testApplication {
            setupApp()
            val response = client.get("/github-api")
            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertContains(response.bodyAsText(), "GitHub API er midlertidig utilgjengelig")
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

        @Test
        fun `500-feil i produksjonsmodus skjuler feildetaljer`() = testApplication {
            application {
                install(ContentNegotiation) { json() }
                install(StatusPages) { grunnmurExceptionHandlers(isProduction = true) }
                routing {
                    get("/unexpected") { throw RuntimeException("Hemmelig intern feil") }
                }
            }
            val response = client.get("/unexpected")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertContains(body, "En feil oppstod")
            assert(!body.contains("Hemmelig intern feil")) {
                "Produksjonsmodus skal ikke eksponere feildetaljer, men fikk: $body"
            }
        }
    }
}
