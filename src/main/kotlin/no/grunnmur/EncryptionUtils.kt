package no.grunnmur

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Krypteringsverktoy for sensitiv data i databasen.
 * Bruker AES-256-GCM som gir baade konfidensialitet og integritet.
 *
 * Alle nokler er 32 bytes representert som 64 hex-tegn.
 * Output-format: Base64(IV || ciphertext || GCM-tag)
 *
 * Denne klassen har INGEN fallback til plaintext.
 * Dekryptering returnerer null ved feil.
 */
object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12  // 96 bits, anbefalt for GCM
    private const val GCM_TAG_LENGTH = 128 // 128 bits autentiseringstagg
    private const val KEY_LENGTH_BYTES = 32 // 256 bits

    /**
     * Krypterer en tekststreng med AES-256-GCM.
     *
     * @param plaintext Teksten som skal krypteres
     * @param hexKey 64 hex-tegn (32 bytes) krypteringsnokkel
     * @return Base64-kodet streng: IV + ciphertext + GCM-tag
     * @throws IllegalArgumentException hvis hexKey har ugyldig format eller lengde
     */
    fun encrypt(plaintext: String, hexKey: String): String {
        val keySpec = parseKey(hexKey)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Dekrypterer en Base64-kodet AES-256-GCM kryptert streng.
     *
     * @param ciphertext Base64-kodet kryptert streng (IV + ciphertext + GCM-tag)
     * @param hexKey 64 hex-tegn (32 bytes) krypteringsnokkel
     * @return Dekryptert tekst, eller null hvis dekryptering feiler
     * @throws IllegalArgumentException hvis hexKey har ugyldig format eller lengde
     */
    fun decrypt(ciphertext: String, hexKey: String): String? {
        val keySpec = parseKey(hexKey)

        val combined = try {
            Base64.getDecoder().decode(ciphertext)
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (combined.size <= GCM_IV_LENGTH) return null

        return try {
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedData = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Genererer en ny tilfeldig AES-256 krypteringsnokkel.
     *
     * @return 64 hex-tegn (32 bytes) egnet for DB_ENCRYPTION_KEY
     */
    fun generateKey(): String {
        val bytes = ByteArray(KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Konverterer en Base64-kodet nokkel til hex-format.
     * Nyttig for migrering fra Base64-baserte nokler.
     *
     * @param base64Key Base64-kodet nokkel (minst 32 bytes etter dekoding)
     * @return 64 hex-tegn
     * @throws IllegalArgumentException hvis nokkelen er for kort
     */
    fun base64KeyToHex(base64Key: String): String {
        val bytes = Base64.getDecoder().decode(base64Key)
        require(bytes.size >= KEY_LENGTH_BYTES) {
            "Base64-nokkel maa vaere minst $KEY_LENGTH_BYTES bytes etter dekoding, var ${bytes.size} bytes"
        }
        return bytes.copyOf(KEY_LENGTH_BYTES).joinToString("") { "%02x".format(it) }
    }

    private fun parseKey(hexKey: String): SecretKeySpec {
        val cleanHex = hexKey.replace(" ", "").replace(":", "")
        require(cleanHex.length == KEY_LENGTH_BYTES * 2) {
            "Krypteringsnokkel maa vaere ${KEY_LENGTH_BYTES * 2} hex-tegn (${KEY_LENGTH_BYTES} bytes), var ${cleanHex.length} tegn"
        }
        require(cleanHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Krypteringsnokkel inneholder ugyldige tegn — kun hex-tegn (0-9, a-f) er tillatt"
        }
        val keyBytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return SecretKeySpec(keyBytes, "AES")
    }
}
