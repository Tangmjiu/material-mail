package com.materialmail.core.sync

import android.content.Context
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.mail.mime.MimeParser
import com.materialmail.core.model.AttachmentId
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.ServerEndpoint
import java.io.File

/**
 * 附件下载器（设计 §11：下载后 MIME 嗅探，预览走系统查看器）。
 *
 * 流程：重新拉取原始 MIME → 按 partIndex 定位附件 → 落盘 cacheDir →
 * 魔数嗅探修正 MIME → 回写 localUri。重拉是因为正文与附件共用一份
 * MIME 报文，IMAP BODY.PEEK[part] 细粒度拉取留给后续优化。
 */
class AttachmentDownloader(
    private val context: Context,
    private val database: MaterialMailDatabase,
    private val credentialProvider: AccountCredentialProvider,
    private val clientFactory: ImapClientFactory = DefaultImapClientFactory(),
) {

    data class DownloadedAttachment(
        val file: File,
        val mimeType: String,
        val fileName: String,
    )

    suspend fun download(attachmentId: AttachmentId): DownloadedAttachment? {
        val entity = database.attachmentDao().getById(attachmentId.value) ?: return null

        // 已下载过直接返回
        entity.localUri?.let { path ->
            val existing = File(path)
            if (existing.exists()) {
                return DownloadedAttachment(existing, entity.mimeType, entity.fileName)
            }
        }

        val message = database.messageDao().getById(entity.messageId) ?: return null
        val folder = database.folderDao().getById(message.folderId) ?: return null
        val account = database.accountDao().getById(folder.accountId)?.toModel() ?: return null
        val credentials = credentialProvider.credentialsFor(account) ?: return null
        val uid = message.remoteUid ?: return null
        val partIndex = entity.id.substringAfter("#att", "").toIntOrNull() ?: return null

        val client = clientFactory.create()
        return try {
            client.connect(account.imap.toServerConfig(), credentials)
            val raw = client.fetchRawMessage(folder.remoteName, uid)
            val parsed = MimeParser.parse(raw.bytes.inputStream())
            val part = parsed.attachments.firstOrNull { it.partIndex == partIndex }
                ?: return null

            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val safeName = part.fileName.replace(Regex("[^\\w.\\-\\u4e00-\\u9fff]"), "_")
            val file = File(dir, entity.id.hashCode().toString(16) + "_" + safeName)
            part.openStream().use { input -> file.outputStream().use { input.copyTo(it) } }

            // MIME 嗅探：魔数优先于声明类型（设计 §11）
            val sniffed = MimeSniffer.sniff(file) ?: part.mimeType
            database.attachmentDao().updateLocalUri(entity.id, file.absolutePath)
            DownloadedAttachment(file, sniffed, part.fileName)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun ServerEndpoint.toServerConfig(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        encryption = encryption,
        allowCleartext = encryption == Encryption.NONE,
    )
}

/** 常见格式的魔数嗅探。不是安全边界，只是修正错标 MIME。 */
object MimeSniffer {
    fun sniff(file: File): String? {
        val header = runCatching {
            file.inputStream().use { ByteArray(16).also { b -> it.read(b) } }
        }.getOrNull() ?: return null
        return when {
            header.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            header.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            header.startsWith(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
            header.startsWith(0x50, 0x4B, 0x03, 0x04) -> "application/zip"
            header.startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            header.startsWith(0x52, 0x49, 0x46, 0x46) -> "image/webp"
            else -> null
        }
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }
    }
}