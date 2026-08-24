package com.materialmail.core.mail.mime

import com.materialmail.core.model.Participant
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.InputStream
import java.time.Instant
import java.util.Properties

/** MIME 解析结果：core:mail 对外只暴露这个模型，不泄漏 jakarta.mail 类型。 */
data class ParsedMimeMessage(
    val messageIdHeader: String,
    val inReplyTo: String?,
    val references: List<String>,
    val from: Participant?,
    val to: List<Participant>,
    val cc: List<Participant>,
    val bcc: List<Participant>,
    val subject: String,
    val sentAt: Instant,
    /** 首选纯文本正文（multipart 中选取，HTML 会被转文本兜底）。 */
    val plainTextBody: String?,
    /** HTML 正文原文。 */
    val htmlBody: String?,
    val attachments: List<ParsedAttachment>,
)

data class ParsedAttachment(
    /** 在 MIME 树中的出现顺序：与 BodyLoader 落库的附件行下标一致。 */
    val partIndex: Int,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentId: String?,
    /** 内容惰性读取：调用方决定何时真正下载/落盘。 */
    val openStream: () -> InputStream,
)

/**
 * MIME 解析 facade。内部使用 Jakarta Mail（Angus），
 * 调用方不感知具体库 —— 这是可替换性的边界。
 */
object MimeParser {

    private val session: Session = Session.getInstance(Properties())

    fun parse(input: InputStream): ParsedMimeMessage {
        val message = MimeMessage(session, input)

        val bodies = BodyParts()
        val attachments = mutableListOf<ParsedAttachment>()
        walk(message, bodies, attachments)

        return ParsedMimeMessage(
            messageIdHeader = message.messageID?.stripAngles()
                ?: error("MIME 消息缺少 Message-ID 头"),
            inReplyTo = message.getHeader("In-Reply-To")?.firstOrNull()?.stripAngles(),
            references = message.getHeader("References")
                ?.flatMap { MESSAGE_ID_REGEX.findAll(it).map { m -> m.value.stripAngles() } }
                ?: emptyList(),
            from = message.from?.firstOrNull()?.toParticipant(),
            to = message.getRecipients(Message.RecipientType.TO).toParticipants(),
            cc = message.getRecipients(Message.RecipientType.CC).toParticipants(),
            bcc = message.getRecipients(Message.RecipientType.BCC).toParticipants(),
            subject = message.subject ?: "",
            sentAt = message.sentDate?.toInstant() ?: Instant.EPOCH,
            plainTextBody = bodies.plainText,
            htmlBody = bodies.html,
            attachments = attachments,
        )
    }

    private class BodyParts {
        var plainText: String? = null
        var html: String? = null
        var attachmentCounter = 0
    }

    /** 递归遍历 multipart，提取正文与附件。 */
    private fun walk(
        part: Part,
        bodies: BodyParts,
        attachments: MutableList<ParsedAttachment>,
    ) {
        when {
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as Multipart
                for (i in 0 until multipart.count) {
                    walk(multipart.getBodyPart(i), bodies, attachments)
                }
            }

            part.isMimeType("text/html") -> {
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) {
                    attachments += part.toAttachment(bodies.attachmentCounter++)
                } else if (bodies.html == null) {
                    bodies.html = part.contentAsString()
                }
            }

            part.isMimeType("text/plain") -> {
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) {
                    attachments += part.toAttachment(bodies.attachmentCounter++)
                } else if (bodies.plainText == null) {
                    bodies.plainText = part.contentAsString()
                }
            }

            part.disposition != null || part.fileName != null -> {
                attachments += part.toAttachment(bodies.attachmentCounter++)
            }
        }
    }

    private fun Part.contentAsString(): String = content.toString()

    private fun Part.toAttachment(index: Int): ParsedAttachment = ParsedAttachment(
        partIndex = index,
        fileName = fileName ?: "attachment",
        mimeType = contentType.substringBefore(';').trim().lowercase(),
        sizeBytes = size.takeIf { it >= 0 }?.toLong() ?: 0L,
        contentId = getHeader("Content-ID")?.firstOrNull()?.stripAngles(),
        openStream = { inputStream },
    )

    private fun jakarta.mail.Address.toParticipant(): Participant? =
        when (this) {
            is InternetAddress -> Participant(address = address, name = personal)
            else -> null
        }

    private fun Array<jakarta.mail.Address>?.toParticipants(): List<Participant> =
        this?.mapNotNull { it.toParticipant() } ?: emptyList()

    private fun String.stripAngles(): String = trim().removePrefix("<").removeSuffix(">")

    private val MESSAGE_ID_REGEX = Regex("<[^>]+>")
}