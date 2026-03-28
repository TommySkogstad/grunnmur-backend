package no.grunnmur

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Konfigurasjon for issue-ruter.
 */
data class GitHubIssueRoutesConfig(
    val issueService: GitHubIssueService,
    val imageService: ImageUploadService?,
    val rateLimiter: RateLimiter,
    val webhookSecret: String
)

@Serializable
internal data class WebhookPayload(
    val action: String = "",
    val issue: WebhookIssue? = null
)

@Serializable
internal data class WebhookIssue(
    val number: Int
)

@Serializable
data class CreateIssueResponse(
    val issueNumber: Int,
    val issueUrl: String,
    val imageUrls: List<String> = emptyList()
)

private val webhookJson = Json { ignoreUnknownKeys = true }

/**
 * Verifiserer GitHub webhook-signatur (HMAC-SHA256).
 * Sammenligner signaturen med beregnet HMAC i konstant tid.
 */
fun verifyWebhookSignature(payload: ByteArray, signature: String, secret: String): Boolean {
    if (!signature.startsWith("sha256=")) return false

    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    val expected = "sha256=" + mac.doFinal(payload).joinToString("") { "%02x".format(it) }

    return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
}

/**
 * Parser webhook-payload og returnerer (action, issueNumber) eller null.
 */
fun parseWebhookAction(payload: String): Pair<String, Int>? {
    return try {
        val parsed = webhookJson.decodeFromString<WebhookPayload>(payload)
        val issue = parsed.issue ?: return null
        Pair(parsed.action, issue.number)
    } catch (_: Exception) {
        null
    }
}

/**
 * Registrerer ruter for issue-opprettelse og GitHub webhook.
 *
 * - `POST /api/issues` — Oppretter issue med valgfrie bilder (multipart)
 * - `POST /api/issues/webhook` — GitHub webhook for opprydding ved issue-lukking
 */
fun Route.gitHubIssueRoutes(config: GitHubIssueRoutesConfig) {

    post("/api/issues") {
        val clientIp = call.getClientIp()
        call.checkRateLimit(config.rateLimiter.isAllowed(clientIp))

        val multipart = call.receiveMultipart()
        var title = ""
        var senderName = ""
        var senderEmail = ""
        var description = ""
        var consoleLogs: String? = null
        var labels = emptyList<String>()
        val imageData = mutableListOf<Pair<ByteArray, String>>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "title" -> title = part.value
                        "senderName" -> senderName = part.value
                        "senderEmail" -> senderEmail = part.value
                        "description" -> description = part.value
                        "consoleLogs" -> consoleLogs = part.value
                        "labels" -> labels = part.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                }
                is PartData.FileItem -> {
                    if (part.name == "images") {
                        val bytes = part.provider().toByteArray()
                        val filename = part.originalFileName ?: "image"
                        imageData.add(bytes to filename)
                    }
                }
                else -> {}
            }
            part.dispose()
        }

        if (title.isBlank()) throw BadRequestException("Tittel er paakrevd")
        if (senderName.isBlank()) throw BadRequestException("Avsendernavn er paakrevd")
        if (senderEmail.isBlank()) throw BadRequestException("Avsender-epost er paakrevd")
        if (description.isBlank()) throw BadRequestException("Beskrivelse er paakrevd")

        // Opprett issue — legg alltid til "brukerrapportert" label slik at
        // issue-triage ikke auto-fikser brukerrapporterte feil
        val issueResponse = config.issueService.createIssue(
            title = title,
            senderName = senderName,
            senderEmail = senderEmail,
            description = description,
            consoleLogs = consoleLogs,
            labels = (labels + "brukerrapportert").distinct()
        )

        // Last opp bilder til issue-nummeret
        val imageUrls = mutableListOf<String>()
        if (config.imageService != null && imageData.isNotEmpty()) {
            for ((bytes, filename) in imageData) {
                val result = config.imageService.uploadImage(issueResponse.number, bytes, filename)
                result.onSuccess { url -> imageUrls.add(url) }
                result.onFailure { /* Bildeopplasting feilet, fortsetter uten */ }
            }
        }

        // Oppdater issue-body med bilde-lenker etter opplasting
        if (imageUrls.isNotEmpty()) {
            val filenames = imageUrls.map { it.substringAfterLast("/") }
            val updatedBody = config.issueService.buildBody(
                senderName, senderEmail, description, consoleLogs,
                imageFilenames = filenames
            )
            config.issueService.updateIssueBody(issueResponse.number, updatedBody)
        }

        call.respond(HttpStatusCode.Created, CreateIssueResponse(
            issueNumber = issueResponse.number,
            issueUrl = issueResponse.html_url,
            imageUrls = imageUrls
        ))
    }

    post("/api/issues/webhook") {
        val clientIp = call.getClientIp()
        call.checkRateLimit(config.rateLimiter.isAllowed(clientIp))

        val payload = call.receive<ByteArray>()
        val signature = call.request.header("X-Hub-Signature-256")
            ?: throw ForbiddenException("Mangler webhook-signatur")

        if (!verifyWebhookSignature(payload, signature, config.webhookSecret)) {
            throw ForbiddenException("Ugyldig webhook-signatur")
        }

        val (action, issueNumber) = parseWebhookAction(String(payload))
            ?: throw BadRequestException("Ugyldig webhook-payload")

        if (action == "closed") {
            config.imageService?.deleteIssueImages(issueNumber)
        }

        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}
