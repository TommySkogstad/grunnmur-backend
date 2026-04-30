package no.grunnmur

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * GitHub App-autentisering med JWT (RS256) og installation token-caching.
 *
 * Installation tokens varer 1 time. Denne klassen cacher tokenet og
 * fornyer det automatisk 5 minutter foer utloep.
 */
class GitHubAppAuth(
    private val appId: String,
    private val privateKeyPem: String,
    private val installationId: String
) : Closeable {
    @Serializable
    private data class InstallationToken(
        val token: String,
        val expires_at: String
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val clientLazy = lazy { HttpClient(CIO) }
    private val client by clientLazy

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiresAt: Long = 0

    override fun close() {
        if (clientLazy.isInitialized()) {
            clientLazy.value.close()
        }
    }

    /**
     * Henter et gyldig installation token. Cacher og fornyer automatisk.
     */
    suspend fun getToken(): String {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now < tokenExpiresAt - 300_000) {
            return cached
        }
        return refreshToken()
    }

    private suspend fun refreshToken(): String {
        val jwt = createJwt()

        val response = client.post("https://api.github.com/app/installations/$installationId/access_tokens") {
            header("Authorization", "Bearer $jwt")
            header("Accept", "application/vnd.github+json")
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw RuntimeException("Kunne ikke hente installation token: ${response.status} — $errorBody")
        }

        val tokenResponse = json.decodeFromString<InstallationToken>(response.bodyAsText())
        cachedToken = tokenResponse.token
        tokenExpiresAt = parseExpiresAt(tokenResponse.expires_at)
        return tokenResponse.token
    }

    internal fun parseExpiresAt(expiresAt: String): Long =
        java.time.Instant.parse(expiresAt).toEpochMilli()

    /**
     * Oppretter en JWT signert med RS256 for GitHub App-autentisering.
     * JWT-en er gyldig i 10 minutter (GitHub-krav: maks 10 min).
     */
    internal fun createJwt(): String {
        val now = System.currentTimeMillis() / 1000
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"iat":${now - 60},"exp":${now + 600},"iss":"$appId"}""".toByteArray())

        val signingInput = "$header.$payload"
        val privateKey = parsePrivateKey(privateKeyPem)
        val signature = java.security.Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray())
        }.sign()

        val encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        return "$header.$payload.$encodedSignature"
    }

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val stripped = pem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(stripped)

        // Proev PKCS8 foerst, deretter PKCS1 (RSA PRIVATE KEY)
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        } catch (_: Exception) {
            // PKCS1 format — wrap i PKCS8
            val pkcs8Bytes = wrapPkcs1InPkcs8(keyBytes)
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
        }
    }

    /**
     * Wrapper en PKCS1 RSA-noekkel i PKCS8-format.
     * GitHub App private keys er typisk i PKCS1 (BEGIN RSA PRIVATE KEY).
     */
    private fun wrapPkcs1InPkcs8(pkcs1Bytes: ByteArray): ByteArray {
        // PKCS8 header for RSA
        val pkcs8Header = byteArrayOf(
            0x30.toByte(), 0x82.toByte(), 0x00.toByte(), 0x00.toByte(), // SEQUENCE (placeholder length)
            0x02.toByte(), 0x01.toByte(), 0x00.toByte(),                // INTEGER 0
            0x30.toByte(), 0x0D.toByte(),                                // SEQUENCE
            0x06.toByte(), 0x09.toByte(),                                // OID
            0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(),
            0xF7.toByte(), 0x0D.toByte(), 0x01.toByte(), 0x01.toByte(),
            0x01.toByte(),                                               // rsaEncryption
            0x05.toByte(), 0x00.toByte(),                                // NULL
            0x04.toByte(), 0x82.toByte(), 0x00.toByte(), 0x00.toByte()  // OCTET STRING (placeholder length)
        )

        val totalLen = pkcs8Header.size - 4 + pkcs1Bytes.size
        val octetLen = pkcs1Bytes.size

        val result = ByteArray(4 + totalLen)
        System.arraycopy(pkcs8Header, 0, result, 0, pkcs8Header.size)
        System.arraycopy(pkcs1Bytes, 0, result, pkcs8Header.size, pkcs1Bytes.size)

        // Fix SEQUENCE length
        result[2] = ((totalLen shr 8) and 0xFF).toByte()
        result[3] = (totalLen and 0xFF).toByte()

        // Fix OCTET STRING length
        val octetLenOffset = pkcs8Header.size - 2
        result[octetLenOffset] = ((octetLen shr 8) and 0xFF).toByte()
        result[octetLenOffset + 1] = (octetLen and 0xFF).toByte()

        return result
    }
}
