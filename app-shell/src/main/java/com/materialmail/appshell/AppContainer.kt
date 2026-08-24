package com.materialmail.appshell

import android.content.Context
import com.materialmail.core.crypto.CredentialStore
import com.materialmail.agent.audit.ActionLogReader
import com.materialmail.region.availability.RegionAvailabilityChecker
import com.materialmail.region.detection.RegionDetector
import com.materialmail.region.ui.RegionNoticeStore
import com.materialmail.agent.audit.ActionLogWriter
import com.materialmail.agent.execution.ConfirmationGate
import com.materialmail.agent.execution.ConfirmationTokenIssuer
import com.materialmail.agent.permissions.AgentPermissionStore
import com.materialmail.agent.yolo.YoloCapabilityStore
import com.materialmail.agent.yolo.YoloSessionManager
import com.materialmail.agent.yolo.YoloStatusNotifier
import com.materialmail.core.search.ContactSuggester
import com.materialmail.core.search.FtsSearchProvider
import com.materialmail.core.crypto.StoredCredential
import com.materialmail.core.database.BodyStore
import com.materialmail.core.database.DatabaseFactory
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.sync.AccountCredentialProvider
import com.materialmail.core.sync.AttachmentDownloader
import com.materialmail.core.sync.BodyLoader
import com.materialmail.core.sync.MessageActionPerformer
import com.materialmail.core.sync.MessageSender
import com.materialmail.core.sync.SyncEngine
import com.materialmail.core.sync.SyncSettingsStore

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
    val attachmentDownloader: AttachmentDownloader =
        AttachmentDownloader(context, database, credentialProvider)
    val searchProvider: FtsSearchProvider = FtsSearchProvider(database)
    val contactSuggester: ContactSuggester = ContactSuggester(database)
    val syncSettings: SyncSettingsStore = SyncSettingsStore(context)

    // ── Agent 地基（阶段 5）────────────────────────────
    // 确认卡片 UI 后续阶段接入；Gate 现在就位，任何 Agent 操作的唯一入口
    val agentPermissionStore = AgentPermissionStore(context)
    val actionLogWriter = ActionLogWriter(database)
    val actionLogReader = ActionLogReader(database)
    val yoloCapabilityStore = YoloCapabilityStore(context)
    val yoloSessionManager = YoloSessionManager(yoloCapabilityStore)
    val yoloStatusNotifier = YoloStatusNotifier(context)

    // Gate 的 YOLO 扩展点接入真实实现：session 管理器即 provider
    val confirmationGate = ConfirmationGate(
        permissionStore = agentPermissionStore,
        tokenIssuer = ConfirmationTokenIssuer(),
        audit = actionLogWriter,
        yolo = yoloSessionManager,
    )

    // ── Region（自包含模块，可整体摘除）─────────────────────
    val regionDetector = com.materialmail.region.detection.RegionDetector(context)
    val regionNoticeStore = RegionNoticeStore(context)
    /** Core 契约的真实实现：region 存在时替换 AllowAll 默认实现。 */
    val availabilityChecker: com.materialmail.core.capability.ServiceAvailabilityChecker =
        RegionAvailabilityChecker(regionDetector)
}