package no.grunnmur

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class GitHubIssueRoutesTest {

    private val secret = "test-webhook-secret"

    private fun computeHmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        return "sha256=$hash"
    }

    @Nested
    inner class VerifyWebhookSignature {

        @Test
        fun `gyldig signatur aksepteres`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, secret)

            assertTrue(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `ugyldig signatur avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val wrongSignature = computeHmac("tampered-payload", secret)

            assertFalse(verifyWebhookSignature(payload.toByteArray(), wrongSignature, secret))
        }

        @Test
        fun `feil hemmelighet avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, "wrong-secret")

            assertFalse(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `signatur uten sha256-prefix avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, secret).removePrefix("sha256=")

            assertFalse(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `tom signatur avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""

            assertFalse(verifyWebhookSignature(payload.toByteArray(), "", secret))
        }

        @Test
        fun `tom payload med gyldig signatur aksepteres`() {
            val payload = ""
            val signature = computeHmac(payload, secret)

            assertTrue(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `signatur med feil lengde avvises`() {
            val payload = """{"action":"closed"}"""

            assertFalse(verifyWebhookSignature(payload.toByteArray(), "sha256=abc", secret))
        }
    }

    @Nested
    inner class KeyRateLimiterInterface {

        @Test
        fun `GitHubIssueRoutesConfig tar imot CompositeRateLimiter som rateLimiter`() {
            val compositeLimiter = CompositeRateLimiter(
                RateLimiter(maxAttempts = 5, windowMs = 60_000),
                RateLimiter(maxAttempts = 10, windowMs = 3_600_000)
            )
            val fakeService = GitHubIssueService(
                GitHubIssueService.Config(token = "fake-token", repo = "fake/fake")
            )
            val config = GitHubIssueRoutesConfig(
                issueService = fakeService,
                imageService = null,
                rateLimiter = compositeLimiter,
                webhookSecret = "test-secret"
            )
            assertTrue(config.rateLimiter.isAllowed("127.0.0.1"))
        }
    }

    @Nested
    inner class PostIssueValidation {

        private fun ApplicationTestBuilder.setupApp() {
            val fakeService = GitHubIssueService(
                GitHubIssueService.Config(token = "fake-token", repo = "fake/fake")
            )
            application {
                install(ContentNegotiation) { json() }
                install(StatusPages) { grunnmurExceptionHandlers() }
                routing {
                    gitHubIssueRoutes(GitHubIssueRoutesConfig(
                        issueService = fakeService,
                        imageService = null,
                        rateLimiter = RateLimiter(maxAttempts = 100, windowMs = 60_000),
                        webhookSecret = "test-secret"
                    ))
                }
            }
        }

        @Test
        fun `ugyldig e-post gir 400`() = testApplication {
            setupApp()
            val response = client.post("/api/issues") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("title", "Test issue")
                        append("senderName", "Test Bruker")
                        append("senderEmail", "not-an-email")
                        append("description", "En beskrivelse")
                    }
                ))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `gyldig multipart-forespørsel oppretter issue og returnerer 201`() {
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.createContext("/repos/fake/fake/issues") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val responseBody = """{"number":42,"html_url":"https://github.com/fake/fake/issues/42"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(201, responseBody.size.toLong())
                exchange.responseBody.use { it.write(responseBody) }
            }
            server.start()
            val port = server.address.port
            try {
                testApplication {
                    val fakeService = GitHubIssueService(
                        GitHubIssueService.Config(token = "fake-token", repo = "fake/fake"),
                        "http://localhost:$port"
                    )
                    application {
                        install(ContentNegotiation) { json() }
                        install(StatusPages) { grunnmurExceptionHandlers() }
                        routing {
                            gitHubIssueRoutes(GitHubIssueRoutesConfig(
                                issueService = fakeService,
                                imageService = null,
                                rateLimiter = RateLimiter(maxAttempts = 100, windowMs = 60_000),
                                webhookSecret = "test-secret"
                            ))
                        }
                    }
                    val response = client.post("/api/issues") {
                        setBody(MultiPartFormDataContent(
                            formData {
                                append("title", "Testfeil i systemet")
                                append("senderName", "Test Bruker")
                                append("senderEmail", "test@example.com")
                                append("description", "Noe er galt med innloggingen")
                            }
                        ))
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    val responseText = response.bodyAsText()
                    assertContains(responseText, "\"issueNumber\":42")
                    assertContains(responseText, "https://github.com/fake/fake/issues/42")
                }
            } finally {
                server.stop(0)
            }
        }
    }

    @Nested
    inner class ImageRateLimit {

        private fun pngBytes(size: Int = 100): ByteArray {
            val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            return header + ByteArray(size - header.size)
        }

        private fun imagePart(index: Int, bytes: ByteArray) = FormPart(
            "images", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"images\"; filename=\"img$index.png\"")
                append(HttpHeaders.ContentType, "image/png")
            }
        )

        private fun createGitHubServer(issueNumber: Int = 42): HttpServer {
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.createContext("/repos/fake/fake/issues") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val body = """{"number":$issueNumber,"html_url":"https://github.com/fake/fake/issues/$issueNumber"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                val status = if (exchange.requestMethod == "POST") 201 else 200
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            return server
        }

        @Test
        fun `for mange bilder gir 400`() = testApplication {
            val fakeService = GitHubIssueService(
                GitHubIssueService.Config(token = "fake-token", repo = "fake/fake")
            )
            application {
                install(ContentNegotiation) { json() }
                install(StatusPages) { grunnmurExceptionHandlers() }
                routing {
                    gitHubIssueRoutes(GitHubIssueRoutesConfig(
                        issueService = fakeService,
                        imageService = null,
                        rateLimiter = RateLimiter(maxAttempts = 100, windowMs = 60_000),
                        webhookSecret = "test-secret",
                        maxImagesPerRequest = 2
                    ))
                }
            }
            val response = client.post("/api/issues") {
                setBody(MultiPartFormDataContent(formData {
                    append("title", "Test")
                    append("senderName", "Test Bruker")
                    append("senderEmail", "test@example.com")
                    append("description", "Beskrivelse")
                    repeat(3) { append(imagePart(it, pngBytes())) }
                }))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `imageRateLimiter stopper bildeopplasting naar kvoten er oppbrukt`() {
            val uploadDir = Files.createTempDirectory("grunnmur-test-uploads")
            val server = createGitHubServer(issueNumber = 42)
            try {
                testApplication {
                    val fakeService = GitHubIssueService(
                        GitHubIssueService.Config(token = "fake-token", repo = "fake/fake"),
                        "http://localhost:${server.address.port}"
                    )
                    val imageService = ImageUploadService(
                        ImageUploadService.Config(
                            uploadDir = uploadDir.toString(),
                            baseUrl = "https://example.com/uploads",
                            repo = "fake/fake"
                        )
                    )
                    application {
                        install(ContentNegotiation) { json() }
                        install(StatusPages) { grunnmurExceptionHandlers() }
                        routing {
                            gitHubIssueRoutes(GitHubIssueRoutesConfig(
                                issueService = fakeService,
                                imageService = imageService,
                                rateLimiter = RateLimiter(maxAttempts = 100, windowMs = 60_000),
                                webhookSecret = "test-secret",
                                imageRateLimiter = RateLimiter(maxAttempts = 1, windowMs = 60_000)
                            ))
                        }
                    }
                    val response = client.post("/api/issues") {
                        setBody(MultiPartFormDataContent(formData {
                            append("title", "Test")
                            append("senderName", "Test Bruker")
                            append("senderEmail", "test@example.com")
                            append("description", "Beskrivelse")
                            repeat(2) { append(imagePart(it, pngBytes())) }
                        }))
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    val parsed = Json { ignoreUnknownKeys = true }
                        .decodeFromString<CreateIssueResponse>(response.bodyAsText())
                    assertEquals(1, parsed.imageUrls.size, "imageRateLimiter (1/min) skal stoppe etter 1 bilde")
                }
            } finally {
                server.stop(0)
                uploadDir.toFile().deleteRecursively()
            }
        }

        @Test
        fun `uten imageRateLimiter lastes alle bilder opp`() {
            val uploadDir = Files.createTempDirectory("grunnmur-test-uploads")
            val server = createGitHubServer(issueNumber = 99)
            try {
                testApplication {
                    val fakeService = GitHubIssueService(
                        GitHubIssueService.Config(token = "fake-token", repo = "fake/fake"),
                        "http://localhost:${server.address.port}"
                    )
                    val imageService = ImageUploadService(
                        ImageUploadService.Config(
                            uploadDir = uploadDir.toString(),
                            baseUrl = "https://example.com/uploads",
                            repo = "fake/fake"
                        )
                    )
                    application {
                        install(ContentNegotiation) { json() }
                        install(StatusPages) { grunnmurExceptionHandlers() }
                        routing {
                            gitHubIssueRoutes(GitHubIssueRoutesConfig(
                                issueService = fakeService,
                                imageService = imageService,
                                rateLimiter = RateLimiter(maxAttempts = 100, windowMs = 60_000),
                                webhookSecret = "test-secret"
                            ))
                        }
                    }
                    val response = client.post("/api/issues") {
                        setBody(MultiPartFormDataContent(formData {
                            append("title", "Test")
                            append("senderName", "Test Bruker")
                            append("senderEmail", "test@example.com")
                            append("description", "Beskrivelse")
                            repeat(2) { append(imagePart(it, pngBytes())) }
                        }))
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    val parsed = Json { ignoreUnknownKeys = true }
                        .decodeFromString<CreateIssueResponse>(response.bodyAsText())
                    assertEquals(2, parsed.imageUrls.size, "Alle 2 bilder skal lastes opp uten imageRateLimiter")
                }
            } finally {
                server.stop(0)
                uploadDir.toFile().deleteRecursively()
            }
        }
    }

    @Nested
    inner class ParseWebhookAction {

        @Test
        fun `parser closed-action med issue-nummer`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val result = parseWebhookAction(payload)

            assertTrue(result != null)
            assertTrue(result!!.first == "closed")
            assertTrue(result.second == 42)
        }

        @Test
        fun `parser opened-action`() {
            val payload = """{"action":"opened","issue":{"number":7}}"""
            val result = parseWebhookAction(payload)

            assertTrue(result != null)
            assertTrue(result!!.first == "opened")
            assertTrue(result.second == 7)
        }

        @Test
        fun `returnerer null for ugyldig JSON`() {
            val result = parseWebhookAction("not json")

            assertTrue(result == null)
        }

        @Test
        fun `returnerer null for payload uten issue`() {
            val payload = """{"action":"closed"}"""
            val result = parseWebhookAction(payload)

            assertTrue(result == null)
        }
    }
}
