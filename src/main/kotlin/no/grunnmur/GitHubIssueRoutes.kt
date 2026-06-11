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
 *
 * [imageRateLimiter] begrenser totalt antall bilder lastet opp per IP på tvers av
 * forespørsler (f.eks. `imageUploadRateLimiter()` = 12/time). Null = ingen separat kvote.
 * [maxImagesPerRequest] avviser forespørsler med for mange bilder med 400.
 */
data class GitHubIssueRoutesConfig(
    val issueService: GitHubIssueService,
    val imageService: ImageUploadService?,
    val rateLimiter: KeyRateLimiter,
    val webhookSecret: String,
    val imageRateLimiter: KeyRateLimiter? = null,
    val maxImagesPerRequest: Int = 3
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
        var imageCountSeen = 0

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
                        imageCountSeen++
                        if (imageCountSeen <= config.maxImagesPerRequest) {
                            val bytes = part.provider().toByteArray()
                            val filename = part.originalFileName ?: "image"
                            imageData.add(bytes to filename)
                        }
                    }
                }
                else -> {}
            }
            part.dispose()
        }

        if (imageCountSeen > config.maxImagesPerRequest) {
            throw BadRequestException("For mange bilder (maks ${config.maxImagesPerRequest} per forespoersel)")
        }

        if (title.isBlank()) throw BadRequestException("Tittel er paakrevd")
        if (senderName.isBlank()) throw BadRequestException("Avsendernavn er paakrevd")
        if (senderEmail.isBlank()) throw BadRequestException("Avsender-epost er paakrevd")
        val emailValidation = Validators.validateEmail(senderEmail)
        if (!emailValidation.isValid) throw BadRequestException(emailValidation.error ?: "Ugyldig e-postadresse")
        if (description.isBlank()) throw BadRequestException("Beskrivelse er paakrevd")

        val issueResponse = config.issueService.createIssue(
            title = title,
            senderName = senderName,
            senderEmail = senderEmail,
            description = description,
            consoleLogs = consoleLogs,
            labels = labels
        )

        // Last opp bilder til issue-nummeret
        val imageUrls = mutableListOf<String>()
        if (config.imageService != null && imageData.isNotEmpty()) {
            for ((bytes, filename) in imageData) {
                if (config.imageRateLimiter != null && !config.imageRateLimiter.isAllowed(clientIp)) {
                    call.application.log.warn("Bildekvote oppbrukt for IP $clientIp ved issue #${issueResponse.number}")
                    break
                }
                val result = config.imageService.uploadImage(issueResponse.number, bytes, filename)
                result.onSuccess { url -> imageUrls.add(url) }
                result.onFailure { e ->
                    call.application.log.warn("Bildeopplasting feilet for issue #${issueResponse.number}: ${e.message}")
                }
            }
        }

        // Oppdater issue-body med bilde-lenker etter opplasting
        if (imageUrls.isNotEmpty()) {
            val updatedBody = config.issueService.buildBody(
                senderName, senderEmail, description, consoleLogs,
                imageFilenames = imageUrls
            )
            try {
                config.issueService.updateIssueBody(issueResponse.number, updatedBody)
            } catch (e: Exception) {
                call.application.log.error("Kunne ikke oppdatere issue-body med bildlenker: ${e.message}")
            }
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
