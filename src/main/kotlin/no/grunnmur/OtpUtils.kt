package no.grunnmur

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * Verktoy for OTP-haandtering (One-Time Password).
 * Samler felles OTP-logikk som brukes paa tvers av alle apper.
 *
 * Genererer 6-sifrede koder med SecureRandom, hasher med SHA-256,
 * og verifiserer med stoette for utlop, forsoksgrense og dev-modus.
 *
 * Lagring og utsending av OTP haandteres av den enkelte app.
 */
object OtpUtils {
    private const val CODE_LENGTH = 6
    private const val CODE_MIN = 100000
    private const val CODE_RANGE = 900000
    private const val DEFAULT_MAX_ATTEMPTS = 3
    internal const val DEV_CODE = "123456"

    private val secureRandom = SecureRandom()

    /**
     * Genererer en tilfeldig 6-sifret OTP-kode (100000-999999).
     */
    fun generateCode(): String {
        return (CODE_MIN + secureRandom.nextInt(CODE_RANGE)).toString()
    }

    /**
     * Hasher en OTP-kode med SHA-256.
     * Returnerer 64 hex-tegn (lowercase).
     *
     * OTP-koder skal alltid lagres som hash, aldri i klartekst.
     */
    fun hashCode(code: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifiserer en OTP-kode mot lagret hash.
     *
     * Sjekker i rekkefølge: forsoksgrense, utlop, dev-modus, kode-match.
     *
     * @param code Koden brukeren har oppgitt
     * @param storedHash SHA-256-hash av den opprinnelige koden
     * @param expiresAt Tidspunkt koden utloper
     * @param attempts Antall tidligere forsok
     * @param maxAttempts Maks tillatte forsok (standard 3)
     * @param devMode Om dev-modus er aktivert (kode "123456" fungerer alltid)
     */
    fun verify(
        code: String,
        storedHash: String,
        expiresAt: LocalDateTime,
        attempts: Int,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        devMode: Boolean = false
    ): OtpVerificationResult {
        if (attempts >= maxAttempts) {
            return OtpVerificationResult.TooManyAttempts
        }

        if (TimeUtils.nowOslo().isAfter(expiresAt)) {
            return OtpVerificationResult.Expired
        }

        if (devMode && code == DEV_CODE) {
            return OtpVerificationResult.Success
        }

        return if (hashCode(code) == storedHash) {
            OtpVerificationResult.Success
        } else {
            OtpVerificationResult.InvalidCode
        }
    }
}

/**
 * Resultat av OTP-verifisering.
 */
sealed class OtpVerificationResult {
    data object Success : OtpVerificationResult()
    data object InvalidCode : OtpVerificationResult()
    data object Expired : OtpVerificationResult()
    data object TooManyAttempts : OtpVerificationResult()
}
