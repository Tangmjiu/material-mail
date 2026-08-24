package com.materialmail.core.mail.smtp

import com.materialmail.core.model.BodyFormat
import com.materialmail.core.model.Participant
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties
import java.util.UUID

/** 一封待发送的邮件（Composer 输出）。 */
data class OutgoingMessage(
    val from: Participant,
    val to: List<Participant>,
    val cc: List<Participant>,
    val bcc: List<Participant>,
    val subject: String,
    val body: String,
    val bodyFormat: BodyFormat,
    /** 回复时原邮件的 RFC Message-ID。 */
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val attachments: List<OutgoingAttachment> = emptyList(),
)

/** 待发附件（内容已读入内存；大小上限由 Composer 控制）。 */
data class OutgoingAttachment(
    val fileName: String,
    val mimeType: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is OutgoingAttachment && other.fileName == fileName && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * fileName.hashCode() + data.contentHashCode()
}

/**
 * 构造符合 RFC 5322 / MIME 的原始报文：
 * - 自动生成 Message-ID（domain 取发件人地址域名）；
 * - 回复时写 In-Reply-To / References（原 References + 原 Message-ID 追加）；
 * - 正文 UTF-8，quoted-printable 由 Jakarta 负责。
 *
 * 返回原始字节：同一份字节同时用于 SMTP 发送和 IMAP 追加到 Sent。
 */
object MimeMessageBuilder {

    fun build(message: OutgoingMessage): ByteArray {
        val session = Session.getInstance(Properties())
        val mime = MimeMessage(session)

        val domain = message.from.address.substringAfter('@', "materialmail.local")
        val messageId = "<" + UUID.randomUUID().toString() + "@" + domain + ">"
        mime.setHeader("Message-ID", messageId)
        mime.setHeader("MIME-Version", "1.0")

        mime.setFrom(InternetAddress(message.from.address, message.from.name, "UTF-8"))
        mime.setRecipients(
            jakarta.mail.Message.RecipientType.TO,
            message.to.map { it.toInternetAddress() }.toTypedArray(),
        )
        if (message.cc.isNotEmpty()) {
            mime.setRecipients(
                jakarta.mail.Message.RecipientType.CC,
                message.cc.map { it.toInternetAddress() }.toTypedArray(),
            )
        }
        if (message.bcc.isNotEmpty()) {
            mime.setRecipients(
                jakarta.mail.Message.RecipientType.BCC,
                message.bcc.map { it.toInternetAddress() }.toTypedArray(),
            )
        }
        mime.setSubject(message.subject, "UTF-8")
        mime.sentDate = Date()

        if (message.inReplyTo != null) {
            val normalized = message.inReplyTo.withAngles()
            mime.setHeader("In-Reply-To", normalized)
            mime.setHeader(
                "References",
                (message.references + listOf(normalized)).distinct().joinToString(" "),
            )
        }

        if (message.attachments.isEmpty()) {
            when (message.bodyFormat) {
                BodyFormat.PLAIN_TEXT -> mime.setText(message.body, "UTF-8")
                BodyFormat.HTML -> mime.setContent(message.body, "text/html; charset=UTF-8")
            }
        } else {
            // multipart/mixed：正文 + 附件
            val multipart = jakarta.mail.internet.MimeMultipart("mixed")
            val bodyPart = jakarta.mail.internet.MimeBodyPart()
            when (message.bodyFormat) {
                BodyFormat.PLAIN_TEXT -> bodyPart.setText(message.body, "UTF-8")
                BodyFormat.HTML -> bodyPart.setContent(message.body, "text/html; charset=UTF-8")
            }
            multipart.addBodyPart(bodyPart)
            for (attachment in message.attachments) {
                val part = jakarta.mail.internet.MimeBodyPart()
                part.dataHandler = jakarta.activation.DataHandler(
                    object : jakarta.activation.DataSource {
                        override fun getInputStream() = attachment.data.inputStream()
                        override fun getOutputStream() = throw UnsupportedOperationException()
                        override fun getContentType() = attachment.mimeType
                        override fun getName() = attachment.fileName
                    },
                )
                part.fileName = jakarta.mail.internet.MimeUtility.encodeText(
                    attachment.fileName, "UTF-8", null,
                )
                part.disposition = jakarta.mail.Part.ATTACHMENT
                multipart.addBodyPart(part)
            }
            mime.setContent(multipart)
        }

        val out = ByteArrayOutputStream()
        mime.writeTo(out)
        return out.toByteArray()
    }

    private fun Participant.toInternetAddress(): InternetAddress =
        InternetAddress(address, name, "UTF-8")

    private fun String.withAngles(): String =
        if (startsWith("<")) this else "<" + trim('<', '>', ' ') + ">"
}