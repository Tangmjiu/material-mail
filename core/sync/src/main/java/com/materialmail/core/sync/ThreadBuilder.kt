package com.materialmail.core.sync

import com.materialmail.core.database.Converters
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.entity.MessageEntity
import com.materialmail.core.database.entity.ThreadEntity
import com.materialmail.core.mail.threading.ThreadNode
import com.materialmail.core.mail.threading.Threader
import com.materialmail.core.mail.threading.ThreadingInput
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.FolderRole

/**
 * 同步后重建账户级会话线程：
 *
 * - 跨文件夹去重：同一 Message-ID 出现在多个文件夹（Gmail All Mail）
 *   只保留一份，优先 INBOX；
 * - 调用 [Threader]（JWZ）建树，每棵树 = 一个 Thread；
 * - threadId 稳定：`t_<accountId>_<根消息 Message-ID>`，
 *   新回复到来不改变 threadId，UI key 稳定；
 * - 必须在事务内调用（SyncEngine 保证）：线程行 upsert → 消息回写
 *   threadId → 清理消失线程，提交时 deferred FK 统一校验。
 */
class ThreadBuilder(private val database: MaterialMailDatabase) {

    suspend fun rebuildForAccount(accountId: AccountId) {
        val threadDao = database.threadDao()
        val messageDao = database.messageDao()

        val messages = messageDao.getByAccount(accountId.value)
        if (messages.isEmpty()) {
            threadDao.deleteNotIn(accountId.value, listOf("__none__"))
            return
        }

        val inboxFolderIds = database.folderDao().getByAccount(accountId.value)
            .filter { it.role == FolderRole.INBOX.name }
            .mapTo(mutableSetOf()) { it.id }

        // Message-ID 去重，INBOX 优先，其次取较新的一份
        val deduped = messages.groupBy { it.messageIdHeader }.map { (_, group) ->
            group.sortedWith(
                compareByDescending<MessageEntity> { it.folderId in inboxFolderIds }
                    .thenByDescending { it.sentAtEpochMs },
            ).first()
        }

        val inputs = deduped.map { entity ->
            ThreadingInput(
                messageIdHeader = entity.messageIdHeader,
                inReplyTo = entity.inReplyTo,
                references = Converters.stringListFromJson(entity.referencesJson),
                subject = entity.subject,
                fromAddress = Converters.participantsFromJson(entity.fromJson)
                    .firstOrNull()?.address ?: "unknown",
                fromName = Converters.participantsFromJson(entity.fromJson)
                    .firstOrNull()?.name,
                isRead = Converters.flagsFromCsv(entity.flagsCsv)
                    .contains(com.materialmail.core.model.MessageFlag.SEEN),
                sentAt = java.time.Instant.ofEpochMilli(entity.sentAtEpochMs),
            )
        }

        val currentMessagesByHeader = deduped.associateBy { it.messageIdHeader }
        val roots = Threader.thread(inputs)

        val threadEntities = mutableListOf<ThreadEntity>()
        val headerToThread = mutableMapOf<String, String>() // messageIdHeader -> threadId
        for (root in roots) {
            val members = flatten(root)
            if (members.isEmpty()) continue
            val rootHeader = root.message?.messageIdHeader ?: members.first().messageIdHeader
            val threadId = "t_${accountId.value}_$rootHeader"
            val latest = members.maxBy { it.sentAt }
            threadEntities += ThreadEntity(
                id = threadId,
                accountId = accountId.value,
                subject = Threader.normalizeSubject(
                    root.message?.subject ?: members.first().subject,
                ),
                // 预览行取最新消息的正文摘要（正文未加载为空串，详情页打开后随下次重建刷新）
                snippet = entitySnippet(currentMessagesByHeader, latest.messageIdHeader),
                participantsJson = Converters.participantsToJson(
                    members.distinctBy { it.fromAddress }
                        .map { com.materialmail.core.model.Participant(it.fromAddress, it.fromName) },
                ),
                messageCount = members.size,
                lastMessageAtEpochMs = latest.sentAt.toEpochMilli(),
                isRead = members.all { it.isRead },
                labelIdsCsv = "", // 标签功能后续阶段接入，重建时保留为空
            )
            for (member in members) {
                headerToThread[member.messageIdHeader] = threadId
            }
        }

        threadDao.upsertAll(threadEntities)
        // 全量回写（含被去重的副本行）：同一 Message-ID 的所有本地行归到同一线程，
        // 否则副本会带着临时 threadId 触发 deferred FK 校验失败
        for (entity in messages) {
            headerToThread[entity.messageIdHeader]?.let { messageDao.updateThreadId(entity.id, it) }
        }
        threadDao.deleteNotIn(
            accountId.value,
            threadEntities.map { it.id }.ifEmpty { listOf("__none__") },
        )
    }

    /** 最新消息的 snippet 需要回查实体（ThreadingInput 不带摘要，保持 threading 输入纯粹）。 */
    private fun entitySnippet(
        messagesByHeader: Map<String, MessageEntity>,
        messageIdHeader: String,
    ): String = messagesByHeader[messageIdHeader]?.snippet ?: ""

    private fun flatten(node: ThreadNode): List<ThreadingInput> =
        listOfNotNull(node.message) + node.children.flatMap(::flatten)
}