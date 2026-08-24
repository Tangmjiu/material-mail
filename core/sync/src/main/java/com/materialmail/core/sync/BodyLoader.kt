package com.materialmail.core.sync

import com.materialmail.core.database.BodyStore
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.mail.mime.MimeParser
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.MessageId

/**
 * 正文懒加载（设计文档 §10：正文分离存储，列表不加载）：
 * 详情页打开消息时才连接服务器拉取原始 MIME → 解析 → 落盘 → 回写 BodyRef。
 * 已加载过的消息直接读本地文件，零网络。
 */
class BodyLoader(
    private val database: MaterialMailDatabase,
    private val bodyStore: BodyStore,
    private val credentialProvider: AccountCredentialProvider,
    private val clientFactory: ImapClientFactory = DefaultImapClientFactory(),
) {

    data class LoadedBody(
        val plainText: String?,
        val html: String?,
        val snippet: String,
    )

    /**
     * 返回正文内容；本地已有缓存时不触网。
     * 返回 null 表示消息不存在或凭据缺失。
     */
    suspend fun loadBody(messageId: MessageId): LoadedBody? {
        val entity = database.messageDao().getById(messageId.value) ?: return null

        if (entity.plainTextPath != null || entity.htmlPath != null) {
            return LoadedBody(
                plainText = entity.plainTextPath?.let(bodyStore::load),
                html = entity.htmlPath?.let(bodyStore::load),
                snippet = entity.snippet,
            )
        }

        val folder = database.folderDao().getById(entity.folderId) ?: return null
        val account = database.accountDao().getById(folder.accountId)?.toModel() ?: return null
        val credentials = credentialProvider.credentialsFor(account) ?: return null
        val uid = entity.remoteUid ?: return null

        val client = clientFactory.create()
        return try {
            client.connect(account.imap.toServerConfig(), credentials)
            val raw = client.fetchRawMessage(folder.remoteName, uid)
            val parsed = MimeParser.parse(raw.bytes.inputStream())

            // 摘要：正文前 140 字符，HTML 邮件先粗剥标签（隔离 WebView 渲染器在打磨阶段引入）
            val snippetSource = parsed.plainTextBody ?: parsed.htmlBody?.stripHtmlTags() ?: ""
            val snippet = snippetSource.replace(Regex("\\s+"), " ").take(140)

            // 附件元数据落库（内容仍惰性：点击时才真正下载）
            database.attachmentDao().deleteByMessage(entity.id)
            database.attachmentDao().upsertAll(
                parsed.attachments.map { attachment ->
                    com.materialmail.core.database.entity.AttachmentEntity(
                        id = entity.id + "#att" + attachment.partIndex,
                        messageId = entity.id,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        sizeBytes = attachment.sizeBytes,
                        localUri = null,
                        contentId = attachment.contentId,
                    )
                },
            )
            database.messageDao().updateHasAttachments(
                entity.id,
                parsed.attachments.isNotEmpty(),
            )

            val stored = bodyStore.save(
                accountId = account.id.value,
                messageId = messageId,
                plainText = parsed.plainTextBody,
                html = parsed.htmlBody,
            )
            database.messageDao().updateBody(
                id = entity.id,
                snippet = snippet,
                plainTextPath = stored.plainTextPath,
                htmlPath = stored.htmlPath,
            )
            LoadedBody(
                plainText = parsed.plainTextBody,
                html = parsed.htmlBody,
                snippet = snippet,
            )
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun com.materialmail.core.model.ServerEndpoint.toServerConfig(): ServerConfig =
        ServerConfig(
            host = host,
            port = port,
            encryption = encryption,
            allowCleartext = encryption == Encryption.NONE,
        )

    private fun String.stripHtmlTags(): String =
        replace(Regex("(?s)<(script|style).*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
}