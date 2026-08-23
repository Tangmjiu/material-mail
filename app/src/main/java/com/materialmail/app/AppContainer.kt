package com.materialmail.app

import android.content.Context
import com.materialmail.core.database.BodyStore
import com.materialmail.core.database.DatabaseFactory
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.sync.AccountCredentialProvider
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageActionPerformer
import com.materialmail.core.sync.SyncEngine

/**
 * 手工 DI 容器（组装层职责）。引入 DI 框架与否是后续阶段的独立决策，
 * 当前规模下一个容器类比框架更诚实。
 */
class AppContainer(context: Context) {

    val database: MaterialMailDatabase = DatabaseFactory.create(context)
    val bodyStore: BodyStore = BodyStore(context)

    /**
     * 凭据供给桩：账户/认证层（Keystore + OAuth 引导）在后续阶段实现。
     * 当前返回 null → 同步结果为 NoCredentials，跳过而非报错。
     */
    private val credentialProvider = AccountCredentialProvider { null }

    val syncEngine: SyncEngine = SyncEngine(database, credentialProvider)
    val bodyLoader: BodyLoader = BodyLoader(database, bodyStore, credentialProvider)
    val actionPerformer: MessageActionPerformer =
        MessageActionPerformer(database, credentialProvider)
}