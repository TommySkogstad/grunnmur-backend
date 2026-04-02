package no.grunnmur

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuthExtensionsTest {

    private val testSecret = "test-secret-key-for-unit-tests"
    private val algorithm = Algorithm.HMAC256(testSecret)

    private fun createToken(userId: Int? = null, email: String? = null): String {
        val builder = JWT.create().withIssuer("test")
        if (userId != null) builder.withClaim("userId", userId)
        if (email != null) builder.withClaim("email", email)
        return builder.sign(algorithm)
    }

    private fun ApplicationTestBuilder.setupApp() {
        install(ContentNegotiation) { json() }
        install(io.ktor.server.plugins.statuspages.StatusPages) { grunnmurExceptionHandlers() }
        install(io.ktor.server.auth.Authentication) {
                jwt("test-auth") {
                    verifier(JWT.require(algorithm).withIssuer("test").build())
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
        }
        routing {
                authenticate("test-auth") {
                    get("/get-user-id") {
                        val userId = call.getUserId()
                        call.respondText("userId=$userId")
                    }
                    get("/require-user-id") {
                        val userId = call.requireUserId()
                        call.respondText("userId=$userId")
                    }
                    get("/get-email") {
                        val email = call.getUserEmail()
                        call.respondText("email=$email")
                    }
                }
                authenticate("test-auth", optional = true) {
                    get("/optional-user-id") {
                        val userId = call.getUserId()
                        call.respondText("userId=$userId")
                    }
                    get("/optional-require") {
                        val userId = call.requireUserId()
                        call.respondText("userId=$userId")
                    }
                }
        }
    }

    @Nested
    inner class GetUserId {

        @Test
        fun `returnerer userId fra gyldig JWT`() = testApplication {
            setupApp()
            val response = client.get("/get-user-id") {
                header(HttpHeaders.Authorization, "Bearer ${createToken(userId = 42)}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("userId=42", response.bodyAsText())
        }

        @Test
        fun `returnerer null naar JWT mangler userId claim`() = testApplication {
            setupApp()
            val response = client.get("/get-user-id") {
                header(HttpHeaders.Authorization, "Bearer ${createToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("userId=null", response.bodyAsText())
        }

        @Test
        fun `returnerer null naar ingen autentisering`() = testApplication {
            setupApp()
            val response = client.get("/optional-user-id")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("userId=null", response.bodyAsText())
        }
    }

    @Nested
    inner class RequireUserId {

        @Test
        fun `returnerer userId fra gyldig JWT`() = testApplication {
            setupApp()
            val response = client.get("/require-user-id") {
                header(HttpHeaders.Authorization, "Bearer ${createToken(userId = 99)}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("userId=99", response.bodyAsText())
        }

        @Test
        fun `kaster AuthenticationException naar userId mangler`() = testApplication {
            setupApp()
            val response = client.get("/optional-require")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        @Test
        fun `kaster AuthenticationException naar JWT har ingen userId`() = testApplication {
            setupApp()
            val response = client.get("/require-user-id") {
                header(HttpHeaders.Authorization, "Bearer ${createToken()}")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Nested
    inner class GetUserEmail {

        @Test
        fun `returnerer email fra gyldig JWT`() = testApplication {
            setupApp()
            val response = client.get("/get-email") {
                header(HttpHeaders.Authorization, "Bearer ${createToken(email = "test@example.com")}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("email=test@example.com", response.bodyAsText())
        }

        @Test
        fun `returnerer null naar email mangler i JWT`() = testApplication {
            setupApp()
            val response = client.get("/get-email") {
                header(HttpHeaders.Authorization, "Bearer ${createToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("email=null", response.bodyAsText())
        }
    }
}
