package no.grunnmur

import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SmtpClientTest {

    private val testConfig = SmtpConfig(
        host = "localhost",
        port = 2525,
        user = "test",
        password = "test",
        from = "test@example.com",
        fromName = "Test Avsender",
        devMode = false
    )

    private val devConfig = testConfig.copy(devMode = true)

    private fun captureClient(config: SmtpConfig = testConfig, messages: MutableList<MimeMessage> = mutableListOf()): Pair<SmtpClient, MutableList<MimeMessage>> {
        val client = SmtpClient(config) { mime -> messages.add(mime) }
        return client to messages
    }

    @Nested
    inner class DevModus {

        @Test
        fun `dev-modus logger i stedet for aa sende`() {
            val (client, messages) = captureClient(devConfig)

            val result = client.send(EmailMessage(
                to = "bruker@example.com",
                subject = "Test",
                body = "Hei"
            ))

            assertTrue(result.success, "Skal returnere suksess i dev-modus")
            assertNotNull(result.messageId, "Skal ha messageId i dev-modus")
            assertTrue(result.messageId!!.startsWith("dev-"), "MessageId skal starte med 'dev-'")
            assertTrue(messages.isEmpty(), "Skal ikke sende e-post i dev-modus")
        }

        @Test
        fun `dev-modus med forceDelivery sender faktisk`() {
            val (client, messages) = captureClient(devConfig)

            val result = client.send(
                EmailMessage(to = "bruker@example.com", subject = "OTP", body = "123456"),
                forceDelivery = true
            )

            assertTrue(result.success, "Skal returnere suksess med forceDelivery")
            assertEquals(1, messages.size, "Skal sende e-post med forceDelivery")
        }

        @Test
        fun `sendWithMessageId i dev-modus returnerer oppgitt messageId`() {
            val (client, messages) = captureClient(devConfig)

            val result = client.sendWithMessageId(
                EmailMessage(to = "bruker@example.com", subject = "Test", body = "Hei"),
                messageId = "<custom-123@example.com>"
            )

            assertTrue(result.success)
            assertEquals("<custom-123@example.com>", result.messageId)
            assertTrue(messages.isEmpty())
        }
    }

    @Nested
    inner class Sending {

        @Test
        fun `send returnerer SendResult med messageId`() {
            val (client, messages) = captureClient()

            val result = client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Hei",
                body = "Testmelding"
            ))

            assertTrue(result.success, "Skal returnere suksess")
            assertNotNull(result.messageId, "Skal ha messageId")
            assertNull(result.error, "Skal ikke ha feil")
            assertEquals(1, messages.size, "Skal sende en e-post")
        }

        @Test
        fun `send setter riktige felter paa MimeMessage`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Viktig melding",
                body = "Innhold her"
            ))

            val mime = messages.first()
            assertEquals("Viktig melding", mime.subject)
            assertTrue(mime.allRecipients.any { it.toString().contains("mottaker@example.com") })
            assertTrue(mime.from.any { it.toString().contains("test@example.com") })
        }

        @Test
        fun `send med from-override bruker message from i stedet for config from`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Override-test",
                body = "Innhold",
                from = "oppdrag@leienbiolog.no"
            ))

            val mime = messages.first()
            assertTrue(mime.from.any { it.toString().contains("oppdrag@leienbiolog.no") })
            assertFalse(mime.from.any { it.toString().contains("test@example.com") })
        }

        @Test
        fun `send uten from-override bruker config from`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Default-test",
                body = "Innhold"
            ))

            val mime = messages.first()
            assertTrue(mime.from.any { it.toString().contains("test@example.com") })
        }

        @Test
        fun `send med htmlBody oppretter multipart alternative`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "HTML-test",
                body = "Ren tekst",
                htmlBody = "<h1>HTML</h1>"
            ))

            val mime = messages.first()
            val content = mime.content
            assertTrue(content is MimeMultipart, "Skal vaere multipart")
            val multipart = content as MimeMultipart
            assertEquals("alternative", multipart.contentType.split(";")[0].substringAfter("/"))
        }

        @Test
        fun `send med replyTo setter Reply-To header`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Svar",
                body = "Test",
                replyTo = "svar@example.com"
            ))

            val mime = messages.first()
            assertTrue(mime.replyTo.any { it.toString().contains("svar@example.com") })
        }

        @Test
        fun `send med feil returnerer failure SendResult`() {
            val client = SmtpClient(testConfig) { throw RuntimeException("SMTP-feil") }

            val result = client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Feil",
                body = "Test"
            ))

            assertFalse(result.success, "Skal returnere feil")
            assertNotNull(result.error, "Skal ha feilmelding")
            assertTrue(result.error!!.contains("SMTP-feil"))
        }
    }

    @Nested
    inner class SendWithMessageId {

        @Test
        fun `sendWithMessageId setter custom Message-ID`() {
            val (client, messages) = captureClient()

            client.sendWithMessageId(
                EmailMessage(to = "mottaker@example.com", subject = "Traad", body = "Test"),
                messageId = "<abc-123@example.com>"
            )

            val mime = messages.first()
            assertEquals("<abc-123@example.com>", mime.getHeader("Message-ID")?.firstOrNull())
        }

        @Test
        fun `sendWithMessageId setter In-Reply-To og References`() {
            val (client, messages) = captureClient()

            client.sendWithMessageId(
                EmailMessage(
                    to = "mottaker@example.com",
                    subject = "Re: Traad",
                    body = "Svar",
                    inReplyTo = "<original-123@example.com>"
                ),
                messageId = "<reply-456@example.com>"
            )

            val mime = messages.first()
            assertEquals("<original-123@example.com>", mime.getHeader("In-Reply-To")?.firstOrNull())
            assertEquals("<original-123@example.com>", mime.getHeader("References")?.firstOrNull())
        }

        @Test
        fun `sendWithMessageId returnerer oppgitt messageId i SendResult`() {
            val (client, _) = captureClient()

            val result = client.sendWithMessageId(
                EmailMessage(to = "mottaker@example.com", subject = "Test", body = "Test"),
                messageId = "<custom@example.com>"
            )

            assertTrue(result.success)
            assertEquals("<custom@example.com>", result.messageId)
        }
    }

    @Nested
    inner class RateLimiting {

        @Test
        fun `rate limiting overolder minInterval mellom sendinger`() {
            val config = testConfig.copy(minIntervalMs = 100)
            val (client, _) = captureClient(config)

            val start = System.currentTimeMillis()
            client.send(EmailMessage(to = "a@test.com", subject = "1", body = "1"))
            client.send(EmailMessage(to = "b@test.com", subject = "2", body = "2"))
            val elapsed = System.currentTimeMillis() - start

            assertTrue(elapsed >= 100, "Skal vente minst 100ms mellom sendinger, brukte ${elapsed}ms")
        }

        @Test
        fun `rate limiting gjelder ikke i dev-modus`() {
            val config = devConfig.copy(minIntervalMs = 500)
            val (client, _) = captureClient(config)

            val start = System.currentTimeMillis()
            repeat(5) {
                client.send(EmailMessage(to = "a@test.com", subject = "Test $it", body = "Test"))
            }
            val elapsed = System.currentTimeMillis() - start

            assertTrue(elapsed < 500, "Dev-modus skal ikke rate-limite, brukte ${elapsed}ms")
        }
    }

    @Nested
    inner class Vedlegg {

        @Test
        fun `send med vedlegg oppretter multipart mixed`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Med vedlegg",
                body = "Se vedlegg",
                attachments = listOf(
                    EmailAttachment("test.txt", "Innhold".toByteArray(), "text/plain")
                )
            ))

            val mime = messages.first()
            val content = mime.content
            assertTrue(content is MimeMultipart, "Skal vaere multipart")
            val multipart = content as MimeMultipart
            assertTrue(multipart.contentType.contains("mixed"), "Skal vaere multipart/mixed")
            assertEquals(2, multipart.count, "Skal ha tekst + 1 vedlegg")
        }

        @Test
        fun `send med flere vedlegg inkluderer alle`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "Flere vedlegg",
                body = "Se vedlegg",
                attachments = listOf(
                    EmailAttachment("a.txt", "A".toByteArray(), "text/plain"),
                    EmailAttachment("b.pdf", "B".toByteArray(), "application/pdf"),
                    EmailAttachment("c.png", "C".toByteArray(), "image/png")
                )
            ))

            val mime = messages.first()
            val multipart = mime.content as MimeMultipart
            assertEquals(4, multipart.count, "Skal ha tekst + 3 vedlegg")
        }

        @Test
        fun `vedlegg med htmlBody har riktig struktur`() {
            val (client, messages) = captureClient()

            client.send(EmailMessage(
                to = "mottaker@example.com",
                subject = "HTML + vedlegg",
                body = "Tekst",
                htmlBody = "<p>HTML</p>",
                attachments = listOf(
                    EmailAttachment("test.txt", "Data".toByteArray(), "text/plain")
                )
            ))

            val mime = messages.first()
            val mixed = mime.content as MimeMultipart
            assertTrue(mixed.contentType.contains("mixed"))
            assertEquals(2, mixed.count, "Skal ha body-del + 1 vedlegg")
        }
    }

    @Nested
    inner class Konfigurasjon {

        @Test
        fun `SmtpConfig har fornuftige standardverdier`() {
            val config = SmtpConfig(
                host = "smtp.example.com",
                port = 587,
                user = "bruker",
                password = "passord",
                from = "noreply@example.com"
            )

            assertEquals("", config.fromName)
            assertTrue(config.requireAuth)
            assertFalse(config.devMode)
            assertEquals(10_000, config.timeoutMs)
            assertEquals(100L, config.minIntervalMs)
        }

        @Test
        fun `EmailAttachment equals og hashCode fungerer med ByteArray`() {
            val a = EmailAttachment("test.txt", "hello".toByteArray(), "text/plain")
            val b = EmailAttachment("test.txt", "hello".toByteArray(), "text/plain")
            val c = EmailAttachment("test.txt", "world".toByteArray(), "text/plain")

            assertEquals(a, b, "Like vedlegg skal vaere like")
            assertEquals(a.hashCode(), b.hashCode())
            assertFalse(a == c, "Ulike vedlegg skal ikke vaere like")
        }
    }
}
