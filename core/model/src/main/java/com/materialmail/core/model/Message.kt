package com.materialmail.core.model

import java.time.Instant

/** IMAP 系统标记。 */
enum class MessageFlag {
    SEEN,
    ANSWERED,
    FLAGGED,
    DRAFT,
    DELETED,
    RECENT,
}

enum class BodyFormat {
    PLAIN_TEXT,
    HTML,
}

/**
 * 正文分离存储的引用：列表查询绝不加载正文，
 * 正文内容以文件形式存放，这里只记录位置与摘要。
 */
data class BodyRef(
    val snippet: String,
    val plainTextPath: String?,
    val htmlPath: String?,
) {
    val preferredFormat: BodyFormat
        get() = if (htmlPath != null) BodyFormat.HTML else BodyFormat.PLAIN_TEXT
}

data class Message(
    val id: MessageId,
    val threadId: ThreadId,
    val folderId: FolderId,
    /** 远端 IMAP UID（文件夹内唯一），与本地 [id] 分离。 */
    val remoteUid: Long?,
    /** RFC Message-ID 头，threading 依据。 */
    val messageIdHeader: String,
    val inReplyTo: String?,
    val references: List<String>,
    val from: Participant,
    val to: List<Participant>,
    val cc: List<Participant>,
    val bcc: List<Participant>,
    val subject: String,
    val sentAt: Instant,
    val bodyRef: BodyRef,
    val hasAttachments: Boolean,
    val flags: Set<MessageFlag>,
) {
    val isRead: Boolean
        get() = MessageFlag.SEEN in flags
}