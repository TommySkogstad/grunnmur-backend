package no.grunnmur

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Konfigurasjon for SMTP-tilkobling.
 *
 * @param host SMTP-server hostname
 * @param port SMTP-port (default 587 for STARTTLS)
 * @param user Brukernavn for autentisering
 * @param password Passord for autentisering
 * @param from Avsender-e-postadresse
 * @param fromName Avsendernavn (vises i e-postklient)
 * @param requireAuth Om SMTP-autentisering kreves
 * @param devMode I dev-modus logges e-post i stedet for aa sendes
 * @param timeoutMs Timeout for SMTP-tilkobling og sending (default 10s)
 * @param minIntervalMs Minimum intervall mellom sendinger i ms (rate limiting, default 100ms)
 */
data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val user: String,
    val password: String,
    val from: String,
    val fromName: String = "",
    val requireAuth: Boolean = true,
    val devMode: Boolean = false,
    val timeoutMs: Int = 10_000,
    val minIntervalMs: Long = 100
)

/**
 * E-postmelding som skal sendes.
 *
 * @param to Mottakers e-postadresse
 * @param subject Emne
 * @param body Ren tekst-innhold
 * @param htmlBody Valgfri HTML-versjon (oppretter multipart/alternative)
 * @param replyTo Valgfri Reply-To-adresse
 * @param inReplyTo Valgfri Message-ID for traad-kobling (setter In-Reply-To og References)
 * @param attachments Liste med vedlegg
 * @param from Valgfri avsenderadresse som overstyrer config.from hvis satt
 * @param fromName Valgfritt avsendernavn som overstyrer config.fromName hvis satt
 */
data class EmailMessage(
    val to: String,
    val subject: String,
    val body: String,
    val htmlBody: String? = null,
    val replyTo: String? = null,
    val inReplyTo: String? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val from: String? = null,
    val fromName: String? = null
)

/**
 * Vedlegg til e-post.
 *
 * @param filename Filnavn som vises for mottaker
 * @param content Filinnhold som ByteArray
 * @param contentType MIME-type (f.eks. "application/pdf", "image/png")
 */
data class EmailAttachment(
    val filename: String,
    val content: ByteArray,
    val contentType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmailAttachment) return false
        return filename == other.filename &&
                content.contentEquals(other.content) &&
                contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}

/**
 * Resultat fra e-postsending.
 *
 * @param success Om sendingen var vellykket
 * @param messageId Message-ID fra SMTP-server (eller dev-modus-ID)
 * @param error Feilmelding hvis sending feilet
 */
data class SendResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

/**
 * Lav-nivaa SMTP-klient for sending av e-post via Jakarta Mail.
 *
 * Bruk:
 * ```
 * val smtp = SmtpClient(SmtpConfig(
 *     host = "smtp.example.com",
 *     port = 587,
 *     user = "bruker",
 *     password = "passord",
 *     from = "noreply@example.com",
 *     fromName = "Min App"
 * ))
 *
 * val result = smtp.send(EmailMessage(
 *     to = "mottaker@example.com",
 *     subject = "Hei",
 *     body = "Innhold"
 * ))
 * ```
 *
 * @param config SMTP-konfigurasjon
 * @param transportAction Valgfri overstyring av faktisk sending (for testing)
 */
class SmtpClient(
    private val config: SmtpConfig,
    private val transportAction: ((MimeMessage) -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(SmtpClient::class.java)
    private val sendLock = ReentrantLock()

    @Volatile
    private var lastSendTime = 0L

    /**
     * Sender en e-post.
     *
     * @param message E-postmelding
     * @param forceDelivery Tving sending selv i dev-modus (for OTP, invitasjoner o.l.)
     * @return SendResult med status og messageId
     */
    fun send(message: EmailMessage, forceDelivery: Boolean = false): SendResult {
        return doSend(message, forceDelivery, customMessageId = null)
    }

    /**
     * Sender en e-post med egendefinert Message-ID for traad-kobling.
     * Setter ogsaa In-Reply-To og References fra [EmailMessage.inReplyTo].
     *
     * Kritisk for biologportal sin e-post-traad-funksjonalitet.
     *
     * @param message E-postmelding
     * @param messageId Egendefinert Message-ID (f.eks. "<abc-123@example.com>")
     * @param forceDelivery Tving sending selv i dev-modus
     * @return SendResult med status og messageId
     */
    fun sendWithMessageId(
        message: EmailMessage,
        messageId: String,
        forceDelivery: Boolean = false
    ): SendResult {
        return doSend(message, forceDelivery, customMessageId = messageId)
    }

    private fun doSend(
        message: EmailMessage,
        forceDelivery: Boolean,
        customMessageId: String?
    ): SendResult {
        if (config.devMode && !forceDelivery) {
            logger.info("[DEV-MODUS] E-post til ${message.to}: ${message.subject}")
            return SendResult(
                success = true,
                messageId = customMessageId ?: "dev-${System.currentTimeMillis()}"
            )
        }

        return try {
            val session = createSession()
            val mimeMessage = buildMimeMessage(session, message, customMessageId)

            sendLock.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastSendTime
                if (elapsed < config.minIntervalMs && lastSendTime > 0) {
                    Thread.sleep(config.minIntervalMs - elapsed)
                }

                mimeMessage.saveChanges()

                if (transportAction != null) {
                    transportAction.invoke(mimeMessage)
                } else {
                    Transport.send(mimeMessage)
                }

                lastSendTime = System.currentTimeMillis()
            }

            val msgId = customMessageId ?: mimeMessage.messageID
            logger.info("E-post sendt til ${message.to}: ${message.subject} (messageId: $msgId)")
            SendResult(success = true, messageId = msgId)
        } catch (e: Exception) {
            logger.error("Feil ved sending av e-post til ${message.to}: ${e.message}", e)
            SendResult(success = false, error = e.message)
        }
    }

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", config.requireAuth.toString())
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.connectiontimeout", config.timeoutMs.toString())
            put("mail.smtp.timeout", config.timeoutMs.toString())
            put("mail.smtp.writetimeout", config.timeoutMs.toString())
        }

        return if (config.requireAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(config.user, config.password)
            })
        } else {
            Session.getInstance(props)
        }
    }

    private fun buildMimeMessage(
        session: Session,
        message: EmailMessage,
        customMessageId: String?
    ): MimeMessage {
        val mime = if (customMessageId != null) {
            object : MimeMessage(session) {
                override fun updateMessageID() {
                    setHeader("Message-ID", customMessageId)
                }
            }
        } else {
            MimeMessage(session)
        }

        val fromAddress = message.from ?: config.from
        val displayName = message.fromName ?: config.fromName
        mime.setFrom(InternetAddress(fromAddress, displayName, "UTF-8"))
        mime.setRecipient(Message.RecipientType.TO, InternetAddress(message.to))
        mime.subject = message.subject
        mime.sentDate = Date()

        message.replyTo?.let {
            mime.replyTo = arrayOf(InternetAddress(it))
        }

        message.inReplyTo?.let {
            mime.setHeader("In-Reply-To", it)
            mime.setHeader("References", it)
        }

        if (message.attachments.isEmpty()) {
            setBodyContent(mime, message)
        } else {
            val mixed = MimeMultipart("mixed")

            val bodyPart = MimeBodyPart()
            if (message.htmlBody != null) {
                val alt = MimeMultipart("alternative")
                alt.addBodyPart(MimeBodyPart().apply { setText(message.body, "UTF-8") })
                alt.addBodyPart(MimeBodyPart().apply {
                    setContent(message.htmlBody, "text/html; charset=UTF-8")
                })
                bodyPart.setContent(alt)
            } else {
                bodyPart.setText(message.body, "UTF-8")
            }
            mixed.addBodyPart(bodyPart)

            for (attachment in message.attachments) {
                val attachPart = MimeBodyPart()
                attachPart.dataHandler = jakarta.activation.DataHandler(
                    ByteArrayDataSource(attachment.content, attachment.contentType)
                )
                attachPart.fileName = attachment.filename
                mixed.addBodyPart(attachPart)
            }

            mime.setContent(mixed)
        }

        return mime
    }

    private fun setBodyContent(mime: MimeMessage, message: EmailMessage) {
        if (message.htmlBody != null) {
            val multipart = MimeMultipart("alternative")
            multipart.addBodyPart(MimeBodyPart().apply { setText(message.body, "UTF-8") })
            multipart.addBodyPart(MimeBodyPart().apply {
                setContent(message.htmlBody, "text/html; charset=UTF-8")
            })
            mime.setContent(multipart)
        } else {
            mime.setText(message.body, "UTF-8")
        }
    }
}
