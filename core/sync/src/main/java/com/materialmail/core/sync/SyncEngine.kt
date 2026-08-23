package com.materialmail.core.sync

import androidx.room.withTransaction
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toModel
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.ServerEndpoint
import com.materialmail.core.model.SyncState
import kotlinx.coroutines.flow.first

/**
 * 同步引擎入口。一次账户同步 = 一个独立 IMAP 连接 + 一个数据库事务：
 *
 * 1. 连接认证（凭据由注入的 [AccountCredentialProvider] 供给）；
 * 2. 文件夹列表对账；
 * 3. 系统文件夹（INBOX/SENT/DRAFTS/TRASH/ARCHIVE）元数据同步，
 *    自定义文件夹本阶段不动（后续在设置中开启）；
 * 4. ThreadBuilder 同事务重建会话（deferred FK 提交时校验）；
 * 5. 更新账户 SyncState，断开连接。
 *
 * 失败不静默：任何异常 → SyncState.ERROR + [SyncResult.Failure]。
 */
class SyncEngine(
    private val database: MaterialMailDatabase,
    private val credentialProvider: AccountCredentialProvider,
    private val clientFactory: ImapClientFactory = DefaultImapClientFactory(),
) {
    private val folderSyncer = FolderSyncer(database)
    private val threadBuilder = ThreadBuilder(database)

    suspend fun syncAccount(accountId: AccountId): SyncResult {
        val accountDao = database.accountDao()
        val account = accountDao.getById(accountId.value)?.toModel()
            ?: return SyncResult.Failure("账户不存在：${accountId.value}")
        val credentials = credentialProvider.credentialsFor(account)
            ?: return SyncResult.NoCredentials

        accountDao.updateSyncState(accountId.value, SyncState.SYNCING.name)
        val client = clientFactory.create()
        return try {
            client.connect(account.imap.toServerConfig(), credentials)
            var newCount = 0
            database.withTransaction {
                val folders = folderSyncer.syncFolderList(client, account)
                for (folder in folders.filter { it.role != FolderRole.CUSTOM }) {
                    newCount += folderSyncer.syncMessages(client, account, folder)
                }
                threadBuilder.rebuildForAccount(accountId)
            }
            accountDao.updateSyncState(accountId.value, SyncState.SYNCED.name)
            SyncResult.Success(newCount)
        } catch (e: Exception) {
            accountDao.updateSyncState(accountId.value, SyncState.ERROR.name)
            SyncResult.Failure(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    /** 全账户顺序同步（单连接池之前不并行，IMAP 服务器对同账户并发连接有限制）。 */
    suspend fun syncAll(): SyncResult {
        val accounts = database.accountDao().observeAll().first()
        if (accounts.isEmpty()) return SyncResult.Success(0)
        var totalNew = 0
        var firstFailure: SyncResult.Failure? = null
        for (entity in accounts) {
            when (val result = syncAccount(AccountId(entity.id))) {
                is SyncResult.Success -> totalNew += result.newMessageCount
                is SyncResult.Failure -> if (firstFailure == null) firstFailure = result
                SyncResult.NoCredentials -> Unit
            }
        }
        return firstFailure ?: SyncResult.Success(totalNew)
    }

    private fun ServerEndpoint.toServerConfig(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        encryption = encryption,
        // encryption = NONE 的账户在创建时已显式确认明文风险（账户引导负责），
        // 同步层不再重复询问
        allowCleartext = encryption == Encryption.NONE,
    )
}