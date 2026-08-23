package com.materialmail.core.model

data class Attachment(
    val id: AttachmentId,
    val messageId: MessageId,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** null = 未下载。下载完成后是 SAF / 本地文件 Uri。 */
    val localUri: String?,
    /** inline 图片（HTML 正文 cid: 引用）。 */
    val contentId: String?,
)