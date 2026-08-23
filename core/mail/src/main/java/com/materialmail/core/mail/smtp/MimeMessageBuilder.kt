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
)

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

        when (message.bodyFormat) {
            BodyFormat.PLAIN_TEXT -> mime.setText(message.body, "UTF-8")
            BodyFormat.HTML -> mime.setContent(message.body, "text/html; charset=UTF-8")
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