package no.grunnmur

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.security.KeyFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * GitHub App-autentisering med JWT (RS256) og installation token-caching.
 *
 * Installation tokens varer 1 time. Denne klassen cacher tokenet og
 * fornyer det automatisk 5 minutter foer utloep.
 */
open class GitHubAppAuth(
    private val appId: String,
    private val privateKeyPem: String,
    private val installationId: String,
    internal val baseUrl: String = "https://api.github.com"
) : Closeable {
    @Serializable
    private data class InstallationToken(
        val token: String,
        val expires_at: String
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val clientLazy = lazy { HttpClient(CIO) }
    private val client by clientLazy

    private val refreshMutex = Mutex()

    private val privateKey by lazy { parsePrivateKey(privateKeyPem) }

    @Volatile
    protected var cachedToken: String? = null
    @Volatile
    protected var tokenExpiresAt: Long = 0

    override fun close() {
        if (clientLazy.isInitialized()) {
            clientLazy.value.close()
        }
    }

    /**
     * Henter et gyldig installation token. Cacher og fornyer automatisk.
     * Mutex sikrer at kun én coroutine kaller refreshToken() selv under concurrent last.
     */
    suspend fun getToken(): String {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiresAt - 300_000 }?.let { return it }
        return refreshMutex.withLock {
            val nowInner = System.currentTimeMillis()
            cachedToken?.takeIf { nowInner < tokenExpiresAt - 300_000 } ?: refreshToken()
        }
    }

    protected open suspend fun refreshToken(): String {
        val jwt = createJwt()

        val response = client.post("$baseUrl/app/installations/$installationId/access_tokens") {
            header("Authorization", "Bearer $jwt")
            header("Accept", "application/vnd.github+json")
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw GitHubApiException("Kunne ikke hente installation token: ${response.status} — $errorBody", response.status.value)
        }

        val tokenResponse = json.decodeFromString<InstallationToken>(response.bodyAsText())
        cachedToken = tokenResponse.token
        tokenExpiresAt = try {
            parseExpiresAt(tokenResponse.expires_at)
        } catch (e: java.time.format.DateTimeParseException) {
            throw GitHubApiException("Ugyldig expires_at-format fra GitHub: '${tokenResponse.expires_at}'", null, e)
        }
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

        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        } catch (_: InvalidKeySpecException) {
            val pkcs8Bytes = wrapPkcs1InPkcs8(keyBytes)
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
        }
    }

    /**
     * Wrapper en PKCS1 RSA-noekkel (BEGIN RSA PRIVATE KEY) i PKCS8-format (RFC 5958).
     * Bruker korrekt BER-lengdekoding via asn1EncodeLength.
     */
    private fun wrapPkcs1InPkcs8(pkcs1Bytes: ByteArray): ByteArray {
        val rsaOid = byteArrayOf(
            0x06.toByte(), 0x09.toByte(),
            0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(),
            0xF7.toByte(), 0x0D.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte()
        )
        val algorithmIdentifier = asn1Sequence(rsaOid + byteArrayOf(0x05.toByte(), 0x00.toByte()))
        val version = byteArrayOf(0x02.toByte(), 0x01.toByte(), 0x00.toByte())
        val privateKeyOctetString = asn1OctetString(pkcs1Bytes)
        return asn1Sequence(version + algorithmIdentifier + privateKeyOctetString)
    }

    internal fun asn1EncodeLength(length: Int): ByteArray {
        require(length in 0..0xFFFF) { "ASN.1 lengde støtter maks 65535, fikk $length" }
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte())
        }
    }

    private fun asn1Sequence(content: ByteArray): ByteArray =
        byteArrayOf(0x30.toByte()) + asn1EncodeLength(content.size) + content

    private fun asn1OctetString(content: ByteArray): ByteArray =
        byteArrayOf(0x04.toByte()) + asn1EncodeLength(content.size) + content
}
