package no.grunnmur

import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.*

/**
 * Service for sikker håndtering av bildeopplasting.
 * Validerer filtype via magic bytes, håndhever størrelsesbegrensninger,
 * og genererer tilfeldige filnavn for å forhindre path traversal.
 *
 * Bruk:
 * ```
 * val service = ImageUploadService(ImageUploadService.Config(
 *     uploadDir = "/uploads/issues",
 *     baseUrl = "https://example.com/uploads/issues"
 * ))
 * val url = service.uploadImage(issueNumber = 1, data = bytes, originalFilename = "bilde.png")
 * ```
 */
class ImageUploadService(private val config: Config) {

    data class Config(
        val uploadDir: String,
        val baseUrl: String,
        val repo: String = "",
        val maxFileSize: Long = 2 * 1024 * 1024,
        val maxImagesPerIssue: Int = 3
    ) {
        /** Repo-slug for filsti (f.eks. "TommySkogstad/biologportal" -> "biologportal") */
        val repoSlug: String get() = repo.substringAfter("/").ifBlank { "default" }
    }

    private enum class ImageType(val extension: String) {
        PNG("png"),
        JPEG("jpg"),
        WEBP("webp")
    }

    /** Laster opp et bilde for en gitt issue. Returnerer offentlig URL ved suksess. */
    fun uploadImage(issueNumber: Int, data: ByteArray, originalFilename: String): Result<String> {
        if (data.isEmpty()) {
            return Result.failure(IllegalArgumentException("Ugyldig filtype: tom fil"))
        }

        if (data.size > config.maxFileSize) {
            return Result.failure(IllegalArgumentException("Filen er for stor (maks ${config.maxFileSize / 1024 / 1024} MB)"))
        }

        val imageType = detectImageType(data)
            ?: return Result.failure(IllegalArgumentException("Ugyldig filtype: kun PNG, JPG og WEBP er tillatt"))

        val issueDir = Path.of(config.uploadDir, config.repoSlug, issueNumber.toString())
        if (issueDir.exists()) {
            val existingCount = issueDir.listDirectoryEntries().size
            if (existingCount >= config.maxImagesPerIssue) {
                return Result.failure(IllegalArgumentException("Kan ikke laste opp flere bilder (maks ${config.maxImagesPerIssue} per issue)"))
            }
        }

        issueDir.createDirectories()

        val filename = "${UUID.randomUUID()}.${imageType.extension}"
        val targetPath = issueDir.resolve(filename)
        targetPath.writeBytes(data)

        val baseUrl = config.baseUrl.trimEnd('/')
        val url = "$baseUrl/${config.repoSlug}/$issueNumber/$filename"
        return Result.success(url)
    }

    /** Sletter alle bilder for en gitt issue. */
    fun deleteIssueImages(issueNumber: Int) {
        val issueDir = Path.of(config.uploadDir, config.repoSlug, issueNumber.toString())
        if (issueDir.exists()) {
            issueDir.toFile().deleteRecursively()
        }
    }

    /** Sletter bilder for issues som ikke lenger er åpne. */
    fun cleanupClosedIssues(openIssueNumbers: Set<Int>) {
        val repoPath = Path.of(config.uploadDir, config.repoSlug)
        if (!repoPath.exists()) return

        repoPath.listDirectoryEntries().forEach { dir ->
            if (dir.isDirectory()) {
                val issueNumber = dir.name.toIntOrNull()
                if (issueNumber != null && issueNumber !in openIssueNumbers) {
                    dir.toFile().deleteRecursively()
                }
            }
        }
    }

    private fun detectImageType(data: ByteArray): ImageType? {
        if (data.size < 12) return null
        return when {
            isPng(data) -> ImageType.PNG
            isJpeg(data) -> ImageType.JPEG
            isWebp(data) -> ImageType.WEBP
            else -> null
        }
    }

    private fun isPng(data: ByteArray): Boolean {
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return data.size >= 8 && data.sliceArray(0 until 8).contentEquals(pngMagic)
    }

    private fun isJpeg(data: ByteArray): Boolean {
        return data.size >= 3 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
    }

    private fun isWebp(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val riff = data.sliceArray(0 until 4)
        val webp = data.sliceArray(8 until 12)
        return riff.contentEquals("RIFF".toByteArray()) && webp.contentEquals("WEBP".toByteArray())
    }
}
