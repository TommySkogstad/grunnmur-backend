package no.grunnmur

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Gjenbrukbar TOTP-service for tofaktorautentisering (RFC 6238).
 *
 * Haandterer generering, verifisering og backup-koder.
 * Bruker [EncryptionUtils] for AES-256-GCM-kryptering av hemmeligheter.
 *
 * Database-lagring og brukerhaandtering er ansvaret til den konsumerende appen.
 */
object TotpService {
    private val secureRandom = SecureRandom()
    private const val SECRET_BYTES = 20
    private const val DEV_TOTP_CODE = "123456"
    private const val TIME_STEP_SECONDS = 30
    private const val CODE_DIGITS = 6
    private const val WINDOW_SIZE = 2 // ±2 vinduer (2 min toleranse)

    /**
     * Starter TOTP-oppsett — genererer hemmelighet og returnerer kryptert secret + QR-URI.
     *
     * @param encryptionKey 64 hex-tegn AES-256-nokkel for kryptering av hemmeligheten
     * @param issuer Tjenestenavn som vises i autentiserings-appen (f.eks. "Lei en biolog")
     * @param accountName Brukerens identifikator (f.eks. e-postadresse)
     * @return [TotpSetupResult] med kryptert hemmelighet og otpauth-URI
     */
    fun setupTotp(encryptionKey: String, issuer: String, accountName: String): TotpSetupResult {
        val secretBytes = ByteArray(SECRET_BYTES)
        secureRandom.nextBytes(secretBytes)
        val secret = Base64.getEncoder().encodeToString(secretBytes)

        val encryptedSecret = EncryptionUtils.encrypt(secret, encryptionKey)
        val qrUri = buildOtpAuthUri(secretBytes, issuer, accountName)

        return TotpSetupResult(
            secret = encryptedSecret,
            qrUri = qrUri
        )
    }

    /**
     * Bekrefter TOTP-oppsett ved aa verifisere at brukeren har skannet QR-koden korrekt.
     *
     * @param encryptedSecret Kryptert hemmelighet fra [setupTotp]
     * @param encryptionKey 64 hex-tegn AES-256-nokkel
     * @param code 6-sifret TOTP-kode fra autentiserings-appen
     * @return true hvis koden er gyldig
     */
    fun confirmTotp(encryptedSecret: String, encryptionKey: String, code: String): Boolean {
        val secretBytes = decryptSecret(encryptedSecret, encryptionKey) ?: return false
        return verifyCode(secretBytes, code)
    }

    /**
     * Verifiserer en TOTP-kode ved innlogging.
     *
     * @param encryptedSecret Kryptert hemmelighet fra databasen
     * @param encryptionKey 64 hex-tegn AES-256-nokkel
     * @param code 6-sifret TOTP-kode fra autentiserings-appen
     * @param devMode Hvis true, aksepteres "123456" alltid (for utvikling — samme som OtpUtils.DEV_CODE)
     * @return true hvis koden er gyldig
     */
    fun verifyTotp(
        encryptedSecret: String,
        encryptionKey: String,
        code: String,
        devMode: Boolean = false
    ): Boolean {
        if (devMode && code == DEV_TOTP_CODE) return true

        val secretBytes = decryptSecret(encryptedSecret, encryptionKey) ?: return false
        return verifyCode(secretBytes, code)
    }

    /**
     * Genererer backup-koder for gjenoppretting.
     * Kodene er 8 hex-tegn formatert som XXXX-XXXX.
     *
     * @param count Antall koder aa generere (standard 10)
     * @return Liste med backup-koder
     */
    fun generateBackupCodes(count: Int = 10): List<String> {
        return (1..count).map {
            val bytes = ByteArray(4)
            secureRandom.nextBytes(bytes)
            bytes.joinToString("") { b -> "%02X".format(b) }
                .chunked(4).joinToString("-")
        }
    }

    /**
     * Verifiserer en backup-kode og returnerer oppdatert liste (uten den brukte koden).
     *
     * @param code Backup-koden brukeren oppgir
     * @param encryptedCodes Krypterte backup-koder fra databasen (kommaseparert)
     * @param encryptionKey 64 hex-tegn AES-256-nokkel
     * @return Pair(gyldig, oppdaterte krypterte koder). Oppdaterte koder er null hvis ingen koder gjenstaar.
     */
    fun verifyBackupCode(
        code: String,
        encryptedCodes: String,
        encryptionKey: String
    ): Pair<Boolean, String?> {
        val codesStr = EncryptionUtils.decrypt(encryptedCodes, encryptionKey)
            ?: return Pair(false, null)

        val codes = codesStr.split(",").toMutableList()
        val normalizedCode = code.replace("-", "").uppercase()
        val index = codes.indexOfFirst { it.replace("-", "").uppercase() == normalizedCode }

        if (index == -1) return Pair(false, null)

        codes.removeAt(index)
        val updatedEncrypted = if (codes.isEmpty()) null
        else EncryptionUtils.encrypt(codes.joinToString(","), encryptionKey)

        return Pair(true, updatedEncrypted)
    }

    /**
     * Returnerer null-verdier for alle TOTP-felt ved deaktivering.
     * Appen bruker disse til aa nullstille databasefeltene.
     *
     * @return Triple(totpEnabled=false, totpSecret=null, totpBackupCodes=null)
     */
    fun disableTotp(): Triple<Boolean, String?, String?> {
        return Triple(false, null, null)
    }

    /**
     * Krypterer backup-koder for lagring i databasen.
     *
     * @param codes Liste med backup-koder
     * @param encryptionKey 64 hex-tegn AES-256-nokkel
     * @return Kryptert streng med kommaseparerte koder
     */
    fun encryptBackupCodes(codes: List<String>, encryptionKey: String): String {
        return EncryptionUtils.encrypt(codes.joinToString(","), encryptionKey)
    }

    // --- Private hjelpefunksjoner ---

    private fun decryptSecret(encryptedSecret: String, encryptionKey: String): ByteArray? {
        val secret = EncryptionUtils.decrypt(encryptedSecret, encryptionKey) ?: return null
        return Base64.getDecoder().decode(secret)
    }

    private fun verifyCode(secretBytes: ByteArray, code: String): Boolean {
        val config = TimeBasedOneTimePasswordConfig(
            timeStep = TIME_STEP_SECONDS.toLong(),
            timeStepUnit = TimeUnit.SECONDS,
            codeDigits = CODE_DIGITS,
            hmacAlgorithm = HmacAlgorithm.SHA1
        )
        val generator = TimeBasedOneTimePasswordGenerator(secretBytes, config)
        val now = Instant.now()

        for (offset in -WINDOW_SIZE..WINDOW_SIZE) {
            val timestamp = now.plusSeconds(offset * TIME_STEP_SECONDS.toLong())
            val expectedCode = generator.generate(timestamp)
            if (code == expectedCode) return true
        }
        return false
    }

    private fun buildOtpAuthUri(secretBytes: ByteArray, issuer: String, accountName: String): String {
        val base32Secret = base32Encode(secretBytes)
        val encodedIssuer = java.net.URLEncoder.encode(issuer, "UTF-8")
        val encodedAccount = java.net.URLEncoder.encode(accountName, "UTF-8")
        return "otpauth://totp/$encodedIssuer:$encodedAccount?secret=$base32Secret&issuer=$encodedIssuer&algorithm=SHA1&digits=$CODE_DIGITS&period=$TIME_STEP_SECONDS"
    }

    /**
     * RFC 4648 Base32-encoding (A-Z, 2-7, ingen padding).
     */
    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                result.append(alphabet[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            result.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return result.toString()
    }
}
