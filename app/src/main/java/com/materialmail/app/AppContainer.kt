package com.materialmail.app

import android.content.Context
import com.materialmail.core.crypto.CredentialStore
import com.materialmail.core.search.FtsSearchProvider
import com.materialmail.core.crypto.StoredCredential
import com.materialmail.core.database.BodyStore
import com.materialmail.core.database.DatabaseFactory
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.sync.AccountCredentialProvider
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageActionPerformer
import com.materialmail.core.sync.MessageSender
import com.materialmail.core.sync.SyncEngine

/**
 * 手工 DI 容器（组装层职责）。引入 DI 框架与否是后续阶段的独立决策，
 * 当前规模下一个容器类比框架更诚实。
 */
class AppContainer(context: Context) {

    val database: MaterialMailDatabase = DatabaseFactory.create(context)
    val bodyStore: BodyStore = BodyStore(context)
    val credentialStore: CredentialStore = CredentialStore(context)

    /** 真实凭据供给：Keystore 加密 + DataStore 持久化（core:crypto）。 */
    private val credentialProvider = AccountCredentialProvider { account ->
        credentialStore.load(account.id.value)?.let { stored ->
            when (stored.type) {
                StoredCredential.Type.PASSWORD ->
                    AuthCredentials.Password(account.email, stored.secret)
                StoredCredential.Type.OAUTH2_TOKEN ->
                    AuthCredentials.OAuth2(account.email, stored.secret)
            }
        }
    }

    val syncEngine: SyncEngine = SyncEngine(database, credentialProvider)
    val bodyLoader: BodyLoader = BodyLoader(database, bodyStore, credentialProvider)
    val actionPerformer: MessageActionPerformer =
        MessageActionPerformer(database, credentialProvider)
    val messageSender: MessageSender = MessageSender(database, credentialProvider)
    val searchProvider: FtsSearchProvider = FtsSearchProvider(database)
}