package com.materialmail.core.sync

import com.materialmail.core.database.Converters
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.MessageFlag
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ServerEndpoint
import com.materialmail.core.model.ThreadId

/**
 * 用户邮件操作（归档 / 已读）的执行器。
 *
 * 策略：**本地乐观更新 + 远端尽力而为**。Local-first 原则下 UI 不等待网络；
 * 远端失败不阻断用户 —— 下次全量同步时删除对账/信封拉取会自然纠正一致性问题，
 * 因此这里不需要复杂的离线操作队列（MVP 之后的打磨阶段再评估）。
 */
class MessageActionPerformer(
    private val database: MaterialMailDatabase,
    private val credentialProvider: AccountCredentialProvider,
    private val clientFactory: ImapClientFactory = DefaultImapClientFactory(),
) {

    /** 归档前的位置快照，Undo 用。 */
    data class ThreadMoveSnapshot(
        val threadId: ThreadId,
        val originalFolders: List<Pair<MessageId, String>>,
    )

    /** 归档线程：本地立即移动到 Archive，远端尽力同步。返回 Undo 快照。 */
    suspend fun archiveThread(threadId: ThreadId): ThreadMoveSnapshot? =
        moveThreadToRole(threadId, FolderRole.ARCHIVE)

    /** 删除线程：移入 Trash（不是永久删除；永久删除永远需要显式确认）。 */
    suspend fun deleteThread(threadId: ThreadId): ThreadMoveSnapshot? =
        moveThreadToRole(threadId, FolderRole.TRASH)

    private suspend fun moveThreadToRole(
        threadId: ThreadId,
        targetRole: FolderRole,
    ): ThreadMoveSnapshot? {
        val thread = database.threadDao().getById(threadId.value) ?: return null
        val messages = database.messageDao().getByThread(threadId.value)
        if (messages.isEmpty()) return null

        val archiveFolder = database.folderDao()
            .findByRole(thread.accountId, targetRole.name)
            ?: return null // 没有目标文件夹的账户（少见）：不执行，由 UI 提示

        val snapshot = ThreadMoveSnapshot(
            threadId = threadId,
            originalFolders = messages.map { MessageId(it.id) to it.folderId },
        )

        // 本地乐观更新
        database.messageDao().moveToFolder(
            ids = messages.map { it.id },
            targetFolderId = archiveFolder.id,
        )

        // 远端尽力而为
        runCatching {
            val account = database.accountDao().getById(thread.accountId)?.toModel() ?: return@runCatching
            val credentials = credentialProvider.credentialsFor(account) ?: return@runCatching
            val client = clientFactory.create()
            try {
                client.connect(account.imap.toServerConfig(), credentials)
                messages.groupBy { it.folderId }.forEach { (sourceFolderId, group) ->
                    val sourceFolder = database.folderDao().getById(sourceFolderId) ?: return@forEach
                    val uids = group.mapNotNull { it.remoteUid }
                    if (uids.isNotEmpty()) {
                        client.moveMessages(sourceFolder.remoteName, uids, archiveFolder.remoteName)
                    }
                }
            } finally {
                runCatching { client.disconnect() }
            }
        }
        return snapshot
    }

    /** Undo 归档：按快照恢复原始位置。 */
    suspend fun restore(snapshot: ThreadMoveSnapshot) {
        val messages = database.messageDao().getByThread(snapshot.threadId.value)
        val byMessageId = messages.associateBy { it.id }
        snapshot.originalFolders.forEach { (messageId, originalFolderId) ->
            if (byMessageId.containsKey(messageId.value)) {
                database.messageDao().moveToFolder(listOf(messageId.value), originalFolderId)
            }
        }
        // 远端恢复依赖下次同步对账（归档的远端移动可能已成功也可能没有，
        // 做对就是幂等：本地以远端为准重建）。不在这里反向远程移动，
        // 避免对"可能未成功的远端操作"执行错误的逆操作。
    }

    /** 线程标记已读：本地立即生效（Unread Spine 收缩动画由此驱动），远端尽力同步。 */
    suspend fun markThreadRead(threadId: ThreadId) {
        val thread = database.threadDao().getById(threadId.value) ?: return
        val messages = database.messageDao().getByThread(threadId.value)
        val unread = messages.filter {
            MessageFlag.SEEN !in Converters.flagsFromCsv(it.flagsCsv)
        }
        if (unread.isEmpty()) return

        database.threadDao().setRead(threadId.value, true)
        for (message in unread) {
            val flags = Converters.flagsFromCsv(message.flagsCsv) + MessageFlag.SEEN
            database.messageDao().updateFlags(message.id, Converters.flagsToCsv(flags))
        }

        runCatching {
            val account = database.accountDao().getById(thread.accountId)?.toModel() ?: return@runCatching
            val credentials = credentialProvider.credentialsFor(account) ?: return@runCatching
            val client = clientFactory.create()
            try {
                client.connect(account.imap.toServerConfig(), credentials)
                unread.groupBy { it.folderId }.forEach { (folderId, group) ->
                    val folder = database.folderDao().getById(folderId) ?: return@forEach
                    val uids = group.mapNotNull { it.remoteUid }
                    if (uids.isNotEmpty()) {
                        client.setFlags(folder.remoteName, uids, setOf(MessageFlag.SEEN), true)
                    }
                }
            } finally {
                runCatching { client.disconnect() }
            }
        }
    }

    private fun ServerEndpoint.toServerConfig(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        encryption = encryption,
        allowCleartext = encryption == Encryption.NONE,
    )
}