package no.grunnmur

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubIssueRoutesTest {

    private val secret = "test-webhook-secret"

    private fun computeHmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        return "sha256=$hash"
    }

    @Nested
    inner class VerifyWebhookSignature {

        @Test
        fun `gyldig signatur aksepteres`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, secret)

            assertTrue(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `ugyldig signatur avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val wrongSignature = computeHmac("tampered-payload", secret)

            assertFalse(verifyWebhookSignature(payload.toByteArray(), wrongSignature, secret))
        }

        @Test
        fun `feil hemmelighet avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, "wrong-secret")

            assertFalse(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `signatur uten sha256-prefix avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val signature = computeHmac(payload, secret).removePrefix("sha256=")

            assertFalse(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `tom signatur avvises`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""

            assertFalse(verifyWebhookSignature(payload.toByteArray(), "", secret))
        }

        @Test
        fun `tom payload med gyldig signatur aksepteres`() {
            val payload = ""
            val signature = computeHmac(payload, secret)

            assertTrue(verifyWebhookSignature(payload.toByteArray(), signature, secret))
        }

        @Test
        fun `signatur med feil lengde avvises`() {
            val payload = """{"action":"closed"}"""

            assertFalse(verifyWebhookSignature(payload.toByteArray(), "sha256=abc", secret))
        }
    }

    @Nested
    inner class ParseWebhookAction {

        @Test
        fun `parser closed-action med issue-nummer`() {
            val payload = """{"action":"closed","issue":{"number":42}}"""
            val result = parseWebhookAction(payload)

            assertTrue(result != null)
            assertTrue(result!!.first == "closed")
            assertTrue(result.second == 42)
        }

        @Test
        fun `parser opened-action`() {
            val payload = """{"action":"opened","issue":{"number":7}}"""
            val result = parseWebhookAction(payload)

            assertTrue(result != null)
            assertTrue(result!!.first == "opened")
            assertTrue(result.second == 7)
        }

        @Test
        fun `returnerer null for ugyldig JSON`() {
            val result = parseWebhookAction("not json")

            assertTrue(result == null)
        }

        @Test
        fun `returnerer null for payload uten issue`() {
            val payload = """{"action":"closed"}"""
            val result = parseWebhookAction(payload)

            assertTrue(result == null)
        }
    }
}
