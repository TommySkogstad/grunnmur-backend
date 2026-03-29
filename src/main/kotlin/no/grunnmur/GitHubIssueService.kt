package no.grunnmur

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Service for aa opprette GitHub Issues via API.
 * All input saniteres via [InputSanitizer].
 *
 * Stoetter to autentiseringsmetoder:
 * - **PAT**: Personlig access token (Config med token)
 * - **GitHub App**: Installation token via JWT (Config med appAuth)
 */
class GitHubIssueService(private val config: Config) {

    data class Config(
        val token: String? = null,
        val appAuth: GitHubAppAuth? = null,
        val repo: String,
        val uploadDir: String? = null,
        val publicBaseUrl: String? = null
    ) {
        init {
            require(token != null || appAuth != null) {
                "Enten token eller appAuth maa vaere satt"
            }
        }
    }

    @Serializable
    data class CreateIssueRequest(
        val title: String,
        val body: String,
        val labels: List<String> = emptyList()
    )

    @Serializable
    data class GitHubIssueResponse(
        val number: Int,
        val html_url: String
    )

    companion object {
        private const val GITHUB_API_VERSION = "2022-11-28"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun getAuthToken(): String {
        return config.appAuth?.getToken() ?: config.token!!
    }

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@GitHubIssueService.json)
            }
        }
    }

    /**
     * Bygger Markdown-body for en GitHub Issue.
     * All input saniteres via [InputSanitizer].
     */
    fun buildBody(
        senderName: String,
        senderEmail: String,
        description: String,
        consoleLogs: String? = null,
        imageFilenames: List<String>? = null
    ): String = buildString {
        val safeName = InputSanitizer.sanitize(senderName, InputSanitizer.MAX_TITLE_LENGTH)
        val safeEmail = InputSanitizer.sanitize(senderEmail, InputSanitizer.MAX_TITLE_LENGTH)
        val safeDescription = InputSanitizer.sanitize(description, InputSanitizer.MAX_DESCRIPTION_LENGTH)

        appendLine("**Meldt av**: $safeName ($safeEmail)")
        appendLine()
        appendLine(safeDescription)

        if (!consoleLogs.isNullOrBlank()) {
            val safeLogs = InputSanitizer.sanitize(consoleLogs, InputSanitizer.MAX_LOGS_LENGTH)
            appendLine()
            appendLine("<details><summary>Konsoll-logg</summary>")
            appendLine()
            appendLine("```")
            appendLine(safeLogs)
            appendLine("```")
            appendLine()
            appendLine("</details>")
        }

        if (!imageFilenames.isNullOrEmpty() && config.publicBaseUrl != null) {
            val dir = config.uploadDir?.let { "$it/" } ?: ""
            val baseUrl = config.publicBaseUrl.trimEnd('/')
            appendLine()
            appendLine("### Vedlegg")
            for (filename in imageFilenames) {
                val safeFilename = InputSanitizer.sanitize(filename, InputSanitizer.MAX_TITLE_LENGTH)
                appendLine("![${safeFilename}](${baseUrl}/${dir}${safeFilename})")
            }
        }
    }.trimEnd()

    /**
     * Oppretter en GitHub Issue via API.
     * Returnerer [GitHubIssueResponse] med issue-nummer og URL.
     */
    suspend fun createIssue(
        title: String,
        senderName: String,
        senderEmail: String,
        description: String,
        consoleLogs: String? = null,
        imageFilenames: List<String>? = null,
        labels: List<String> = emptyList()
    ): GitHubIssueResponse {
        val safeTitle = InputSanitizer.sanitize(title, InputSanitizer.MAX_TITLE_LENGTH)
        val body = buildBody(senderName, senderEmail, description, consoleLogs, imageFilenames)

        val request = CreateIssueRequest(
            title = safeTitle,
            body = body,
            labels = labels
        )

        val authToken = getAuthToken()
        val response = client.post("https://api.github.com/repos/${config.repo}/issues") {
            header("Authorization", "Bearer $authToken")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Kunne ikke opprette GitHub Issue: ${response.status} — $errorBody")
        }

        return json.decodeFromString<GitHubIssueResponse>(response.bodyAsText())
    }

    /**
     * Oppdaterer body paa en eksisterende GitHub Issue via PATCH API.
     * Brukes for aa legge til vedleggsseksjon etter bildeopplasting.
     */
    suspend fun updateIssueBody(issueNumber: Int, body: String) {
        val authToken = getAuthToken()
        val response = client.patch("https://api.github.com/repos/${config.repo}/issues/$issueNumber") {
            header("Authorization", "Bearer $authToken")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            contentType(ContentType.Application.Json)
            setBody(mapOf("body" to body))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Kunne ikke oppdatere GitHub Issue #$issueNumber: ${response.status} — $errorBody")
        }
    }
}
