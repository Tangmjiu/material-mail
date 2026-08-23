package com.materialmail.core.sync

import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.entity.MessageEntity
import com.materialmail.core.database.toEntity
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.ImapClient
import com.materialmail.core.mail.imap.RemoteFolder
import com.materialmail.core.model.Account
import com.materialmail.core.model.Folder
import com.materialmail.core.model.FolderId
import java.time.Instant

/**
 * 文件夹级同步：
 * 1. 文件夹列表对账（新增 upsert、消失的删除）；
 * 2. UIDVALIDITY 校验 —— 变化则整文件夹本地重建（IMAP 铁律，不可跳过）；
 * 3. 增量信封拉取（本地最大 UID 之后）；
 * 4. 删除对账（本地有、远端无 → 删除）。
 *
 * 正文本阶段不拉取：详情页打开时懒加载（见 BodyLoader），
 * 列表页的 snippet 在正文落地后回写。
 */
class FolderSyncer(private val database: MaterialMailDatabase) {

    suspend fun syncFolderList(client: ImapClient, account: Account): List<Folder> {
        val remote = client.listFolders()
        val folders = remote.map { it.toModel(account) }
        database.folderDao().upsertAll(folders.map { it.toEntity() })
        database.folderDao().deleteNotIn(
            accountId = account.id.value,
            keepIds = folders.map { it.id.value }.ifEmpty { listOf("__none__") },
        )
        return folders
    }

    /** 同步单个文件夹的消息元数据，返回新增邮件数。 */
    suspend fun syncMessages(client: ImapClient, account: Account, folder: Folder): Int {
        val messageDao = database.messageDao()
        val folderId = folder.id.value

        val stored = database.folderDao().getById(folderId)
        val uidValidityChanged =
            stored != null && folder.uidValidity >= 0 && stored.uidValidity != folder.uidValidity
        if (uidValidityChanged) {
            messageDao.deleteByFolder(folderId)
        }

        // 删除对账（先于插入，避免误删刚写入的新邮件）
        val remoteUids = client.fetchAllUids(folder.remoteName).toSet()
        val staleUids = messageDao.getRemoteUidsInFolder(folderId).filter { it !in remoteUids }
        if (staleUids.isNotEmpty()) {
            messageDao.deleteByRemoteUids(folderId, staleUids)
        }

        val afterUid =
            if (uidValidityChanged) 0L else messageDao.getMaxRemoteUid(folderId) ?: 0L
        val newEnvelopes = client.fetchNewEnvelopes(folder.remoteName, afterUid)
        if (newEnvelopes.isNotEmpty()) {
            messageDao.upsertAll(newEnvelopes.map { it.toEntity(account, folder) })
        }

        database.folderDao().updateUnreadCount(folderId, folder.unreadCount.coerceAtLeast(0))
        return newEnvelopes.size
    }

    private fun RemoteFolder.toModel(account: Account): Folder = Folder(
        // 本地 ID 稳定：账户 + 远端名，重同步不产生新行
        id = FolderId("${account.id.value}:$remoteName"),
        accountId = account.id,
        remoteName = remoteName,
        displayName = displayName,
        role = role,
        unreadCount = unreadCount.coerceAtLeast(0),
        uidValidity = uidValidity,
    )

    private fun com.materialmail.core.mail.imap.RemoteEnvelope.toEntity(
        account: Account,
        folder: Folder,
    ): MessageEntity {
        val localId = "${folder.id.value}#$uid"
        return MessageEntity(
            id = localId,
            // 临时值，ThreadBuilder 在同事务内重建为真实 threadId
            threadId = "tmp:$localId",
            folderId = folder.id.value,
            remoteUid = uid,
            messageIdHeader = messageIdHeader ?: "unknown-$uid@materialmail.local",
            inReplyTo = inReplyTo,
            referencesJson = com.materialmail.core.database.Converters.stringListToJson(references),
            fromJson = com.materialmail.core.database.Converters.participantsToJson(
                from.ifEmpty { listOf(com.materialmail.core.model.Participant("unknown", null)) },
            ),
            toJson = com.materialmail.core.database.Converters.participantsToJson(to),
            ccJson = com.materialmail.core.database.Converters.participantsToJson(cc),
            bccJson = "[]",
            subject = subject,
            sentAtEpochMs = (sentAt ?: Instant.now()).toEpochMilli(),
            snippet = "",
            plainTextPath = null,
            htmlPath = null,
            hasAttachments = false, // 信封阶段未知，正文解析后修正
            flagsCsv = com.materialmail.core.database.Converters.flagsToCsv(flags),
        )
    }
}