package no.grunnmur

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubIssueServiceTest {

    private val config = GitHubIssueService.Config(
        token = "test-token",
        repo = "TestOrg/test-repo",
        uploadDir = "uploads",
        publicBaseUrl = "https://example.com"
    )

    private val service = GitHubIssueService(config)

    @Test
    fun `buildBody inkluderer avsender-info`() {
        val body = service.buildBody(
            senderName = "Ola Nordmann",
            senderEmail = "ola@example.com",
            description = "Noe er galt med systemet"
        )

        assertContains(body, "Ola Nordmann")
        assertContains(body, "ola@example.com")
        assertContains(body, "Noe er galt med systemet")
    }

    @Test
    fun `buildBody saniterer all input via InputSanitizer`() {
        val body = service.buildBody(
            senderName = "<script>alert('xss')</script>Ola",
            senderEmail = "ola@example.com",
            description = "Sjekk [denne linken](https://evil.com) og @admin"
        )

        // Script-tags og markdown-lenker skal vaere fjernet
        assertFalse(body.contains("<script>"))
        assertFalse(body.contains("</script>"))
        assertFalse(body.contains("[denne linken](https://evil.com)"))
        // Mentions skal vaere beskyttet
        assertContains(body, "`@admin`")
    }

    @Test
    fun `buildBody maskerer secrets i beskrivelse`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Token: ghp_abc123def456ghi789jkl012mno345"
        )

        assertFalse(body.contains("ghp_abc123def456ghi789jkl012mno345"))
        assertContains(body, "[MASKERT]")
    }

    @Test
    fun `buildBody inkluderer konsoll-logg i sammenleggbar seksjon`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Feilmelding",
            consoleLogs = "Error: Something went wrong\nat line 42"
        )

        assertContains(body, "<details>")
        assertContains(body, "</details>")
        assertContains(body, "Konsoll-logg")
        assertContains(body, "Error: Something went wrong")
    }

    @Test
    fun `buildBody utelater konsoll-seksjon naar logs er null`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Feilmelding"
        )

        assertFalse(body.contains("<details>"))
        assertFalse(body.contains("Konsoll-logg"))
    }

    @Test
    fun `buildBody inkluderer bilde-lenker`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Se vedlegg",
            imageFilenames = listOf("screenshot1.png", "screenshot2.jpg")
        )

        assertContains(body, "![screenshot1.png](https://example.com/uploads/screenshot1.png)")
        assertContains(body, "![screenshot2.jpg](https://example.com/uploads/screenshot2.jpg)")
        assertContains(body, "Vedlegg")
    }

    @Test
    fun `buildBody utelater vedlegg-seksjon naar ingen bilder`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Ingen vedlegg"
        )

        assertFalse(body.contains("Vedlegg"))
    }

    @Test
    fun `buildBody saniterer konsoll-logg`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Feil",
            consoleLogs = "Token: ghp_abc123def456ghi789jkl012mno345 ble lekket"
        )

        assertFalse(body.contains("ghp_abc123def456ghi789jkl012mno345"))
    }

    @Test
    fun `buildBody trunkerer lang beskrivelse`() {
        val longDescription = "A".repeat(3000)
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = longDescription
        )

        // Beskrivelsen skal vaere trunkert til MAX_DESCRIPTION_LENGTH
        assertFalse(body.contains("A".repeat(3000)))
    }

    @Test
    fun `buildBody trunkerer lange konsoll-logger`() {
        val longLogs = "X".repeat(15000)
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Feil",
            consoleLogs = longLogs
        )

        assertFalse(body.contains("X".repeat(15000)))
    }

    @Test
    fun `buildBody haandterer tom bildliste`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Test",
            imageFilenames = emptyList()
        )

        assertFalse(body.contains("Vedlegg"))
    }

    @Test
    fun `buildBody saniterer bilde-filnavn`() {
        val body = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Test",
            imageFilenames = listOf("<script>alert(1)</script>.png")
        )

        assertFalse(body.contains("<script>"))
    }

    @Test
    fun `buildBody med filnavn fra opplastings-URLer gir riktig vedleggsseksjon`() {
        // Simulerer flyten i ruten: bilde-URLer returneres fra ImageUploadService,
        // filnavn ekstraheres, og buildBody kalles med disse for aa oppdatere issue.
        val imageUrls = listOf(
            "https://example.com/uploads/42/abc-123.png",
            "https://example.com/uploads/42/def-456.jpg"
        )
        val filenames = imageUrls.map { it.substringAfterLast("/") }

        val updatedBody = service.buildBody(
            senderName = "Ola",
            senderEmail = "ola@example.com",
            description = "Se vedlegg",
            imageFilenames = filenames
        )

        assertContains(updatedBody, "### Vedlegg")
        assertContains(updatedBody, "![abc-123.png](https://example.com/uploads/abc-123.png)")
        assertContains(updatedBody, "![def-456.jpg](https://example.com/uploads/def-456.jpg)")
    }
}
