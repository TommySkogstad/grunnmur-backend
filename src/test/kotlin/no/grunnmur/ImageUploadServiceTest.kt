package no.grunnmur

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class ImageUploadServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private fun createService(
        maxFileSize: Long = 2 * 1024 * 1024,
        maxImagesPerIssue: Int = 3,
        baseUrl: String = "https://example.com/uploads/issues"
    ): ImageUploadService {
        return ImageUploadService(
            ImageUploadService.Config(
                uploadDir = tempDir.toString(),
                baseUrl = baseUrl,
                maxFileSize = maxFileSize,
                maxImagesPerIssue = maxImagesPerIssue
            )
        )
    }

    // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
    private fun pngBytes(size: Int = 100): ByteArray {
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return header + ByteArray(size - header.size)
    }

    // JPEG magic bytes: FF D8 FF
    private fun jpegBytes(size: Int = 100): ByteArray {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        return header + ByteArray(size - header.size)
    }

    // WEBP magic bytes: RIFF....WEBP
    private fun webpBytes(size: Int = 100): ByteArray {
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, // RIFF
            0x00, 0x00, 0x00, 0x00, // file size placeholder
            0x57, 0x45, 0x42, 0x50  // WEBP
        )
        return header + ByteArray(size - header.size)
    }

    @Nested
    inner class Filtypevalidering {

        @Test
        fun `godtar PNG-bilde`() {
            val service = createService()
            val result = service.uploadImage(1, pngBytes(), "test.png")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `godtar JPEG-bilde`() {
            val service = createService()
            val result = service.uploadImage(1, jpegBytes(), "test.jpg")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `godtar WEBP-bilde`() {
            val service = createService()
            val result = service.uploadImage(1, webpBytes(), "test.webp")
            assertTrue(result.isSuccess)
        }

        @Test
        fun `avviser GIF-bilde`() {
            val gifHeader = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) // GIF89a
            val data = gifHeader + ByteArray(94)
            val service = createService()
            val result = service.uploadImage(1, data, "test.gif")
            assertTrue(result.isFailure)
            assertContains(result.exceptionOrNull()!!.message!!, "filtype")
        }

        @Test
        fun `avviser tilfeldig data uten gyldig magic bytes`() {
            val service = createService()
            val result = service.uploadImage(1, ByteArray(100), "test.png")
            assertTrue(result.isFailure)
        }

        @Test
        fun `avviser tom fil`() {
            val service = createService()
            val result = service.uploadImage(1, ByteArray(0), "test.png")
            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class Størrelsesbegrensning {

        @Test
        fun `avviser bilde over maks størrelse`() {
            val service = createService(maxFileSize = 1024)
            val result = service.uploadImage(1, pngBytes(2048), "big.png")
            assertTrue(result.isFailure)
            assertContains(result.exceptionOrNull()!!.message!!, "stor")
        }

        @Test
        fun `godtar bilde på nøyaktig maks størrelse`() {
            val service = createService(maxFileSize = 1024)
            val result = service.uploadImage(1, pngBytes(1024), "exact.png")
            assertTrue(result.isSuccess)
        }
    }

    @Nested
    inner class MaksAntallBilder {

        @Test
        fun `avviser fjerde bilde for samme issue`() {
            val service = createService(maxImagesPerIssue = 3)
            service.uploadImage(1, pngBytes(), "a.png")
            service.uploadImage(1, jpegBytes(), "b.jpg")
            service.uploadImage(1, webpBytes(), "c.webp")
            val result = service.uploadImage(1, pngBytes(), "d.png")
            assertTrue(result.isFailure)
            assertContains(result.exceptionOrNull()!!.message!!, "maks")
        }

        @Test
        fun `teller bilder per issue uavhengig`() {
            val service = createService(maxImagesPerIssue = 3)
            service.uploadImage(1, pngBytes(), "a.png")
            service.uploadImage(1, pngBytes(), "b.png")
            service.uploadImage(1, pngBytes(), "c.png")
            // Issue 2 skal fortsatt ha plass
            val result = service.uploadImage(2, pngBytes(), "d.png")
            assertTrue(result.isSuccess)
        }
    }

    @Nested
    inner class Filnavn {

        @Test
        fun `genererer UUID-filnavn`() {
            val service = createService()
            val result = service.uploadImage(1, pngBytes(), "../../evil.png")
            assertTrue(result.isSuccess)
            val url = result.getOrThrow()
            // URL skal ikke inneholde originalt filnavn
            assertFalse(url.contains("evil"))
            // Skal inneholde riktig extension
            assertTrue(url.endsWith(".png"))
        }

        @Test
        fun `forhindrer path traversal i filnavn`() {
            val service = createService()
            val result = service.uploadImage(1, pngBytes(), "../../../etc/passwd.png")
            assertTrue(result.isSuccess)
            // Filen skal ligge i riktig mappe, ikke utenfor
            val issueDir = tempDir.resolve("1")
            assertTrue(issueDir.exists())
            val files = issueDir.listDirectoryEntries()
            assertEquals(1, files.size)
            assertTrue(files[0].parent == issueDir)
        }

        @Test
        fun `bruker riktig extension basert på filtype`() {
            val service = createService()

            val pngUrl = service.uploadImage(1, pngBytes(), "test.png").getOrThrow()
            assertTrue(pngUrl.endsWith(".png"))

            val jpegUrl = service.uploadImage(1, jpegBytes(), "test.jpg").getOrThrow()
            assertTrue(jpegUrl.endsWith(".jpg"))

            val webpUrl = service.uploadImage(1, webpBytes(), "test.webp").getOrThrow()
            assertTrue(webpUrl.endsWith(".webp"))
        }
    }

    @Nested
    inner class URLGenerering {

        @Test
        fun `returnerer korrekt offentlig URL`() {
            val service = createService(baseUrl = "https://example.com/uploads/issues")
            val url = service.uploadImage(1, pngBytes(), "test.png").getOrThrow()
            assertTrue(url.startsWith("https://example.com/uploads/issues/1/"))
            assertTrue(url.endsWith(".png"))
        }
    }

    @Nested
    inner class Sletting {

        @Test
        fun `deleteIssueImages sletter bildemappe`() {
            val service = createService()
            service.uploadImage(1, pngBytes(), "a.png")
            service.uploadImage(1, jpegBytes(), "b.jpg")
            val issueDir = tempDir.resolve("1")
            assertTrue(issueDir.exists())

            service.deleteIssueImages(1)
            assertFalse(issueDir.exists())
        }

        @Test
        fun `deleteIssueImages feiler ikke for ikke-eksisterende issue`() {
            val service = createService()
            // Skal ikke kaste exception
            service.deleteIssueImages(999)
        }
    }

    @Nested
    inner class Opprydding {

        @Test
        fun `cleanupClosedIssues sletter kun lukkede issues sine bilder`() {
            val service = createService()
            service.uploadImage(1, pngBytes(), "a.png")
            service.uploadImage(2, pngBytes(), "b.png")
            service.uploadImage(3, pngBytes(), "c.png")

            // Issue 1 og 3 er åpne, issue 2 er lukket
            service.cleanupClosedIssues(openIssueNumbers = setOf(1, 3))

            assertTrue(tempDir.resolve("1").exists())
            assertFalse(tempDir.resolve("2").exists())
            assertTrue(tempDir.resolve("3").exists())
        }

        @Test
        fun `cleanupClosedIssues håndterer tom uploadDir`() {
            val service = createService()
            // Skal ikke kaste exception
            service.cleanupClosedIssues(openIssueNumbers = setOf(1))
        }
    }
}
