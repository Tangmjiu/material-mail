package com.materialmail.core.mail.imap

import com.materialmail.core.mail.utf7.ModifiedUtf7
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.MessageFlag
import com.materialmail.core.model.Participant
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.UIDFolder
import jakarta.mail.event.MessageChangedListener
import jakarta.mail.event.MessageChangedEvent
import jakarta.mail.event.MessageCountAdapter
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayOutputStream
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * 基于 Jakarta Mail（Angus）的 [ImapClient] 实现。
 *
 * 线程模型：jakarta.mail 全部 API 均为阻塞式，本类统一收敛到
 * [Dispatchers.IO]；[idle] 使用独立守护线程驱动 IDLE/NOOP 循环。
 *
 * 已知边界（记录在案，不静默掩盖）：
 * - IDLE 断线重连由 sync 层（后续阶段）负责，本类断线即关闭事件流；
 * - CONDSTORE/QRESYNC 本阶段只探测不启用，增量同步留给 sync 层协商。
 */
class JakartaImapClient : ImapClient {

    private var store: IMAPStore? = null
    private var cachedCapabilities: ImapCapabilities? = null

    override suspend fun connect(config: ServerConfig, credentials: AuthCredentials) =
        withContext(Dispatchers.IO) {
            disconnectInternal()
            val useSsl = config.encryption == Encryption.SSL_TLS
            val protocol = if (useSsl) "imaps" else "imap"
            val props = Properties().apply {
                put("mail.$protocol.connectiontimeout", "15000")
                put("mail.$protocol.timeout", "30000")
                put("mail.$protocol.writetimeout", "15000")
                when (config.encryption) {
                    Encryption.SSL_TLS -> Unit
                    Encryption.STARTTLS -> {
                        put("mail.$protocol.starttls.enable", "true")
                        put("mail.$protocol.starttls.required", "true")
                    }
                    Encryption.NONE -> Unit // ServerConfig.init 已强制用户确认
                }
                if (credentials is AuthCredentials.OAuth2) {
                    put("mail.$protocol.sasl.enable", "true")
                    put("mail.$protocol.sasl.mechanisms", "XOAUTH2")
                    put("mail.$protocol.auth.login.disable", "true")
                    put("mail.$protocol.auth.plain.disable", "true")
                }
            }
            val session = Session.getInstance(props)
            val newStore = session.getStore(protocol) as IMAPStore
            val secret = when (credentials) {
                is AuthCredentials.Password -> credentials.password
                is AuthCredentials.OAuth2 -> credentials.accessToken
            }
            newStore.connect(config.host, config.port, credentials.username, secret)
            store = newStore
            cachedCapabilities = null
            Unit
        }

    override suspend fun capabilities(): ImapCapabilities = withContext(Dispatchers.IO) {
        cachedCapabilities ?: ImapCapabilities(
            hasIdle = requireStore().hasCapability("IDLE"),
            hasCondstore = requireStore().hasCapability("CONDSTORE"),
            hasQresync = requireStore().hasCapability("QRESYNC"),
            hasUidPlus = requireStore().hasCapability("UIDPLUS"),
            hasMove = requireStore().hasCapability("MOVE"),
        ).also { cachedCapabilities = it }
    }

    override suspend fun listFolders(): List<RemoteFolder> = withContext(Dispatchers.IO) {
        requireStore().defaultFolder.list("*").map { folder ->
            val decoded = ModifiedUtf7.decode(folder.fullName)
            var messageCount = -1
            var unreadCount = -1
            var uidValidity = -1L
            runCatching {
                folder.open(Folder.READ_ONLY)
                messageCount = folder.messageCount
                unreadCount = folder.unreadMessageCount
                uidValidity = (folder as IMAPFolder).uidValidity
                folder.close(false)
            }
            RemoteFolder(
                remoteName = folder.fullName,
                displayName = decoded,
                role = inferRole(folder as IMAPFolder, decoded),
                messageCount = messageCount,
                unreadCount = unreadCount,
                uidValidity = uidValidity,
            )
        }
    }

    override suspend fun fetchEnvelopes(folderName: String, uids: List<Long>): List<RemoteEnvelope> =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext emptyList()
            val folder = openFolder(folderName, Folder.READ_ONLY)
            try {
                val messages = folder.getMessagesByUID(uids.toLongArray())
                    .filterNotNull()
                if (messages.isEmpty()) return@withContext emptyList()
                fetchEnvelopeBatch(folder, messages)
            } finally {
                folder.close(false)
            }
        }

    override suspend fun fetchNewEnvelopes(folderName: String, afterUid: Long): List<RemoteEnvelope> =
        withContext(Dispatchers.IO) {
            val folder = openFolder(folderName, Folder.READ_ONLY)
            try {
                val messages = folder.getMessagesByUID(afterUid + 1, UIDFolder.LASTUID)
                    ?.filterNotNull() ?: return@withContext emptyList()
                if (messages.isEmpty()) return@withContext emptyList()
                fetchEnvelopeBatch(folder, messages)
            } finally {
                folder.close(false)
            }
        }

    override suspend fun fetchAllUids(folderName: String): List<Long> = withContext(Dispatchers.IO) {
        val folder = openFolder(folderName, Folder.READ_ONLY)
        try {
            folder.getMessagesByUID(1, UIDFolder.LASTUID)
                ?.filterNotNull()
                ?.map { folder.getUID(it) }
                ?: emptyList()
        } finally {
            folder.close(false)
        }
    }

    override suspend fun fetchRawMessage(folderName: String, uid: Long): RawMessage =
        withContext(Dispatchers.IO) {
            val folder = openFolder(folderName, Folder.READ_ONLY)
            try {
                val message = (folder as IMAPFolder).getMessageByUID(uid)
                    ?: error("UID $uid 在 $folderName 中不存在（可能已被删除）")
                val out = ByteArrayOutputStream()
                message.writeTo(out)
                RawMessage(
                    uid = uid,
                    flags = message.flags.toModelFlags(),
                    bytes = out.toByteArray(),
                )
            } finally {
                folder.close(false)
            }
        }

    override suspend fun appendMessage(
        folderName: String,
        raw: ByteArray,
        flags: Set<MessageFlag>,
    ): Long? = withContext(Dispatchers.IO) {
        val folder = requireStore().getFolder(folderName) as IMAPFolder
        if (!folder.exists()) folder.create(Folder.HOLDS_MESSAGES)
        val message = MimeMessage(
            jakarta.mail.Session.getInstance(Properties()),
            raw.inputStream(),
        )
        message.setFlags(flags.toJakartaFlags(), true)
        val appendUids = folder.appendUIDMessages(arrayOf(message))
        appendUids?.firstOrNull()?.uid?.takeIf { it > 0 }
    }

    override suspend fun setFlags(
        folderName: String,
        uids: List<Long>,
        flags: Set<MessageFlag>,
        value: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (uids.isEmpty()) return@withContext
        val folder = openFolder(folderName, Folder.READ_WRITE)
        try {
            val messages = folder.getMessagesByUID(uids.toLongArray()).filterNotNull()
            folder.setFlags(messages.toTypedArray(), flags.toJakartaFlags(), value)
        } finally {
            folder.close(false)
        }
        Unit
    }

    override suspend fun moveMessages(folderName: String, uids: List<Long>, targetFolderName: String) =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext
            val folder = openFolder(folderName, Folder.READ_WRITE)
            try {
                val messages = folder.getMessagesByUID(uids.toLongArray()).filterNotNull()
                if (messages.isEmpty()) return@withContext
                val target = requireStore().getFolder(targetFolderName)
                if (capabilities().hasMove) {
                    (folder as IMAPFolder).moveMessages(messages.toTypedArray(), target)
                } else {
                    // UIDPLUS 兜底：COPY + \Deleted + EXPUNGE
                    folder.copyMessages(messages.toTypedArray(), target)
                    folder.setFlags(
                        messages.toTypedArray(),
                        Flags(Flags.Flag.DELETED),
                        true,
                    )
                    folder.expunge()
                }
            } finally {
                folder.close(false)
            }
            Unit
        }

    override suspend fun expungeMessages(folderName: String, uids: List<Long>) =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext
            val folder = openFolder(folderName, Folder.READ_WRITE)
            try {
                val messages = folder.getMessagesByUID(uids.toLongArray()).filterNotNull()
                folder.setFlags(messages.toTypedArray(), Flags(Flags.Flag.DELETED), true)
                folder.expunge()
            } finally {
                folder.close(false)
            }
            Unit
        }

    override fun idle(folderName: String): Flow<FolderEvent> = callbackFlow {
        val idleThread = Thread({
            try {
                val folder = openFolder(folderName, Folder.READ_WRITE) as IMAPFolder
                folder.addMessageCountListener(object : MessageCountAdapter() {
                    override fun messagesAdded(e: MessageCountEvent) {
                        trySend(FolderEvent.MessageArrived)
                    }

                    override fun messagesRemoved(e: MessageCountEvent) {
                        trySend(FolderEvent.MessageExpunged)
                    }
                })
                folder.addMessageChangedListener(object : MessageChangedListener {
                    override fun messageChanged(e: MessageChangedEvent) {
                        trySend(FolderEvent.FlagsChanged)
                    }
                })
                val supportsIdle = requireStore().hasCapability("IDLE")
                while (!Thread.currentThread().isInterrupted) {
                    if (supportsIdle) {
                        folder.idle() // 阻塞直至服务器推送或超时
                    } else {
                        // 不支持 IDLE 的服务器：NOOP 保活 + 周期检查
                        Thread.sleep(NOOP_POLL_INTERVAL_MS)
                        runCatching { folder.messageCount }
                    }
                }
                folder.close(false)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // 断线：关闭事件流，由 sync 层重建（含兜底轮询）
                close()
            }
        }, "imap-idle-$folderName").apply {
            isDaemon = true
            start()
        }
        awaitClose { idleThread.interrupt() }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        Unit
    }

    // ── internal ─────────────────────────────────────────────

    /** 批量拉取信封。调用方负责文件夹的开关与关闭。 */
    private fun fetchEnvelopeBatch(
        folder: IMAPFolder,
        messages: List<jakarta.mail.Message>,
    ): List<RemoteEnvelope> {
        val profile = jakarta.mail.FetchProfile().apply {
            add(jakarta.mail.FetchProfile.Item.ENVELOPE)
            add(jakarta.mail.FetchProfile.Item.FLAGS)
            add(IMAPFolder.FetchProfileItem.INTERNALDATE)
            add(jakarta.mail.FetchProfile.Item.SIZE)
            add(UIDFolder.FetchProfileItem.UID)
            add("Message-ID")
            add("In-Reply-To")
            add("References")
            add("List-Unsubscribe")
        }
        folder.fetch(messages.toTypedArray(), profile)
        return messages.map { message ->
            RemoteEnvelope(
                uid = folder.getUID(message),
                messageIdHeader = message.getHeader("Message-ID")?.firstOrNull()
                    ?.trim()?.removePrefix("<")?.removeSuffix(">"),
                inReplyTo = message.getHeader("In-Reply-To")?.firstOrNull()
                    ?.trim()?.removePrefix("<")?.removeSuffix(">"),
                references = message.getHeader("References")
                    ?.flatMap { REFERENCE_REGEX.findAll(it).map { m -> m.value } }
                    ?.map { it.removePrefix("<").removeSuffix(">") }
                    ?: emptyList(),
                from = message.from.toParticipants(),
                to = message.getRecipients(jakarta.mail.Message.RecipientType.TO).toParticipants(),
                cc = message.getRecipients(jakarta.mail.Message.RecipientType.CC).toParticipants(),
                subject = message.subject ?: "",
                sentAt = message.sentDate?.toInstant(),
                flags = message.flags.toModelFlags(),
                sizeBytes = message.size.takeIf { it >= 0 }?.toLong() ?: 0L,
                listUnsubscribe = message.getHeader("List-Unsubscribe")?.firstOrNull()?.trim(),
            )
        }
    }

    private fun disconnectInternal() {
        runCatching { store?.close() }
        store = null
        cachedCapabilities = null
    }

    private fun requireStore(): IMAPStore =
        store ?: error("未连接：请先调用 connect()")

    private fun openFolder(name: String, mode: Int): IMAPFolder {
        val folder = requireStore().getFolder(name) as IMAPFolder
        if (!folder.isOpen) folder.open(mode)
        return folder
    }

    /**
     * 文件夹角色推断：优先服务器特殊用途属性（RFC 6154），
     * 其次常见英文名，最后国内邮箱中文名。
     */
    private fun inferRole(folder: IMAPFolder, decodedName: String): FolderRole {
        val attrs = runCatching { folder.attributes.map { it.lowercase() } }.getOrDefault(emptyList())
        return when {
            attrs.any { it == "\\inbox" } || decodedName.equals("INBOX", true) -> FolderRole.INBOX
            attrs.any { it == "\\sent" } || decodedName in SENT_NAMES -> FolderRole.SENT
            attrs.any { it == "\\drafts" } || decodedName in DRAFT_NAMES -> FolderRole.DRAFTS
            attrs.any { it == "\\trash" } || decodedName in TRASH_NAMES -> FolderRole.TRASH
            attrs.any { it == "\\archive" || it == "\\all" } || decodedName in ARCHIVE_NAMES ->
                FolderRole.ARCHIVE
            else -> FolderRole.CUSTOM
        }
    }

    private fun Array<jakarta.mail.Address>?.toParticipants(): List<Participant> =
        this?.filterIsInstance<InternetAddress>()
            ?.map { Participant(address = it.address, name = it.personal) }
            ?: emptyList()

    private fun Flags.toModelFlags(): Set<MessageFlag> = buildSet {
        if (this@toModelFlags.contains(Flags.Flag.SEEN)) add(MessageFlag.SEEN)
        if (this@toModelFlags.contains(Flags.Flag.ANSWERED)) add(MessageFlag.ANSWERED)
        if (this@toModelFlags.contains(Flags.Flag.FLAGGED)) add(MessageFlag.FLAGGED)
        if (this@toModelFlags.contains(Flags.Flag.DRAFT)) add(MessageFlag.DRAFT)
        if (this@toModelFlags.contains(Flags.Flag.DELETED)) add(MessageFlag.DELETED)
        if (this@toModelFlags.contains(Flags.Flag.RECENT)) add(MessageFlag.RECENT)
    }

    private fun Set<MessageFlag>.toJakartaFlags(): Flags {
        val flags = Flags()
        for (flag in this) {
            when (flag) {
                MessageFlag.SEEN -> flags.add(Flags.Flag.SEEN)
                MessageFlag.ANSWERED -> flags.add(Flags.Flag.ANSWERED)
                MessageFlag.FLAGGED -> flags.add(Flags.Flag.FLAGGED)
                MessageFlag.DRAFT -> flags.add(Flags.Flag.DRAFT)
                MessageFlag.DELETED -> flags.add(Flags.Flag.DELETED)
                MessageFlag.RECENT -> flags.add(Flags.Flag.RECENT)
            }
        }
        return flags
    }

    private companion object {
        const val NOOP_POLL_INTERVAL_MS = 30_000L

        val REFERENCE_REGEX = Regex("<[^>]+>")

        val SENT_NAMES = setOf("Sent", "Sent Items", "Sent Mail", "已发送", "已发送邮件")
        val DRAFT_NAMES = setOf("Drafts", "草稿", "草稿箱")
        val TRASH_NAMES = setOf("Trash", "Deleted", "Deleted Items", "已删除", "回收站")
        val ARCHIVE_NAMES = setOf("Archive", "All Mail", "归档", "全部邮件")
    }
}