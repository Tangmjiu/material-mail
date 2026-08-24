package com.materialmail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.materialmail.designsystem.theme.MaterialMailTheme
import com.materialmail.feature.inbox.InboxRoutes
import com.materialmail.feature.inbox.InboxScreen
import com.materialmail.feature.inbox.InboxViewModel
import com.materialmail.feature.inbox.ThreadDetailScreen
import com.materialmail.feature.inbox.ThreadDetailViewModel
import com.materialmail.feature.inbox.SearchScreen
import com.materialmail.feature.inbox.SearchViewModel
import com.materialmail.core.model.DraftId
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.work.SyncScheduler
import com.materialmail.feature.account.AccountRoutes
import com.materialmail.feature.account.AddAccountScreen
import com.materialmail.feature.account.AddAccountViewModel
import com.materialmail.feature.composer.ComposeMode
import com.materialmail.feature.composer.ComposerRoutes
import com.materialmail.feature.composer.ComposerScreen
import com.materialmail.feature.composer.ComposerViewModel
import com.materialmail.feature.settings.ActionLogScreen
import com.materialmail.feature.settings.ActionLogViewModel
import com.materialmail.feature.settings.AgentPermissionsScreen
import com.materialmail.feature.settings.AgentPermissionsViewModel
import com.materialmail.feature.settings.SettingsRoutes
import com.materialmail.feature.settings.SettingsScreen
import com.materialmail.feature.settings.SettingsViewModel
import com.materialmail.region.ui.RegionSettingsScreen
import com.materialmail.region.ui.RegionSettingsViewModel
import com.materialmail.feature.settings.yolo.YoloScreen
import com.materialmail.feature.settings.yolo.YoloViewModel

class MainActivity : ComponentActivity() {

    /** mailto: / 分享 / Shortcut 的待处理写信请求。 */
    private val pendingCompose =
        androidx.compose.runtime.mutableStateOf<ComposeRequest?>(null)

    data class ComposeRequest(
        val to: String? = null,
        val subject: String? = null,
        val body: String? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingCompose.value = parseIntent(intent)
        setContent {
            MaterialMailTheme {
                MaterialMailNavHost(
                    container = (application as MaterialMailApp).container,
                    pendingCompose = pendingCompose,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        parseIntent(intent)?.let { pendingCompose.value = it }
    }

    private fun parseIntent(intent: android.content.Intent?): ComposeRequest? {
        if (intent == null) return null
        return when {
            intent.action == "com.materialmail.action.COMPOSE" -> ComposeRequest()
            intent.action == android.content.Intent.ACTION_VIEW &&
                intent.data?.scheme == "mailto" -> {
                val uri = intent.data!!
                ComposeRequest(
                    to = uri.schemeSpecificPart.substringBefore('?'),
                    subject = uri.getQueryParameter("subject"),
                    body = uri.getQueryParameter("body"),
                )
            }
            intent.action == android.content.Intent.ACTION_SEND ->
                ComposeRequest(
                    subject = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT),
                    body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT),
                )
            else -> null
        }
    }
}

/**
 * 导航图（组装层）。SharedTransitionLayout 承载列表 → 详情的容器变换，
 * Predictive Back 时系统自动反向预览（manifest 已开启 onBackInvokedCallback）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialMailNavHost(
    container: AppContainer,
    pendingCompose: androidx.compose.runtime.MutableState<MainActivity.ComposeRequest?>,
    navController: NavHostController = rememberNavController(),
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // mailto:/分享/Shortcut：NavHost 就绪后跳写信页
    androidx.compose.runtime.LaunchedEffect(pendingCompose.value) {
        val request = pendingCompose.value ?: return@LaunchedEffect
        pendingCompose.value = null
        navController.navigate(
            ComposerRoutes.newPrefilled(
                to = request.to,
                subject = request.subject,
                body = request.body,
            ),
        )
    }
    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = InboxRoutes.INBOX) {
            composable(InboxRoutes.INBOX) {
                val windowWidthClass = androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
                    .windowSizeClass.windowWidthSizeClass
                if (windowWidthClass == androidx.window.core.layout.WindowWidthSizeClass.EXPANDED) {
                    TwoPaneHome(
                        container = container,
                        onManualRefresh = { SyncScheduler.syncNow(appContext) },
                        onAddAccount = { navController.navigate(AccountRoutes.ADD_ACCOUNT) },
                        onCompose = { navController.navigate(ComposerRoutes.new()) },
                        onEditDraft = { navController.navigate(ComposerRoutes.editDraft(it)) },
                        onSearch = { navController.navigate(InboxRoutes.SEARCH) },
                        onOpenSettings = { navController.navigate(SettingsRoutes.SETTINGS) },
                        onReply = { navController.navigate(ComposerRoutes.reply(it, false)) },
                        onReplyAll = { navController.navigate(ComposerRoutes.reply(it, true)) },
                        onForward = { navController.navigate(ComposerRoutes.forward(it)) },
                    )
                    return@composable
                }
                val viewModel: InboxViewModel = viewModel(
                    factory = InboxViewModel.factory(
                        database = container.database,
                        actionPerformer = container.actionPerformer,
                        onManualRefresh = { SyncScheduler.syncNow(appContext) },
                    ),
                )
                InboxScreen(
                    viewModel = viewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onOpenThread = { threadId ->
                        navController.navigate(InboxRoutes.threadDetail(threadId))
                    },
                    onAddAccount = { navController.navigate(AccountRoutes.ADD_ACCOUNT) },
                    onCompose = { navController.navigate(ComposerRoutes.new()) },
                    onSearch = { navController.navigate(InboxRoutes.SEARCH) },
                    onEditDraft = { draftId ->
                        navController.navigate(ComposerRoutes.editDraft(draftId))
                    },
                    onOpenSettings = { navController.navigate(SettingsRoutes.SETTINGS) },
                )
            }
            composable(SettingsRoutes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(
                        syncSettings = container.syncSettings,
                        database = container.database,
                        credentialStore = container.credentialStore,
                        bodyStore = container.bodyStore,
                    ),
                )
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenActionLog = { navController.navigate(SettingsRoutes.ACTION_LOG) },
                    onOpenAgentPermissions = {
                        navController.navigate(SettingsRoutes.AGENT_PERMISSIONS)
                    },
                    onOpenRegion = { navController.navigate(SettingsRoutes.REGION) },
                    onOpenYolo = { navController.navigate(SettingsRoutes.YOLO) },
                )
            }
            composable(SettingsRoutes.YOLO) {
                val viewModel: YoloViewModel = viewModel(
                    factory = YoloViewModel.factory(
                        store = container.yoloCapabilityStore,
                        sessionManager = container.yoloSessionManager,
                        onActiveChanged = { active ->
                            if (active) container.yoloStatusNotifier.show()
                            else container.yoloStatusNotifier.hide()
                        },
                    ),
                )
                YoloScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.REGION) {
                val viewModel: RegionSettingsViewModel = viewModel(
                    factory = RegionSettingsViewModel.factory(
                        detector = container.regionDetector,
                        noticeStore = container.regionNoticeStore,
                    ),
                )
                RegionSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SettingsRoutes.ACTION_LOG) {
                val viewModel: ActionLogViewModel = viewModel(
                    factory = ActionLogViewModel.factory(container.actionLogReader),
                )
                ActionLogScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.AGENT_PERMISSIONS) {
                val viewModel: AgentPermissionsViewModel = viewModel(
                    factory = AgentPermissionsViewModel.factory(container.agentPermissionStore),
                )
                AgentPermissionsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(InboxRoutes.SEARCH) {
                val viewModel: SearchViewModel = viewModel(
                    factory = SearchViewModel.factory(
                        searchProvider = container.searchProvider,
                        database = container.database,
                    ),
                )
                SearchScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenThread = { threadId ->
                        navController.navigate(InboxRoutes.threadDetail(threadId))
                    },
                )
            }
            composable(AccountRoutes.ADD_ACCOUNT) {
                val viewModel: AddAccountViewModel = viewModel(
                    factory = AddAccountViewModel.factory(
                        database = container.database,
                        credentialStore = container.credentialStore,
                    ),
                )
                AddAccountScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        // 新账户立即首次同步，然后回收件箱
                        SyncScheduler.syncNow(appContext)
                        navController.popBackStack()
                    },
                )
            }
            composable(InboxRoutes.THREAD_DETAIL) { entry ->
                val threadId = entry.arguments?.getString("threadId") ?: return@composable
                val viewModel: ThreadDetailViewModel = viewModel(
                    key = "thread-$threadId",
                    factory = ThreadDetailViewModel.factory(
                        threadId = ThreadId(threadId),
                        database = container.database,
                        bodyLoader = container.bodyLoader,
                        actionPerformer = container.actionPerformer,
                        attachmentDownloader = container.attachmentDownloader,
                    ),
                )
                ThreadDetailScreen(
                    viewModel = viewModel,
                    threadId = threadId,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onBack = { navController.popBackStack() },
                    onReply = { messageId ->
                        navController.navigate(ComposerRoutes.reply(messageId, replyAll = false))
                    },
                    onReplyAll = { messageId ->
                        navController.navigate(ComposerRoutes.reply(messageId, replyAll = true))
                    },
                    onForward = { messageId ->
                        navController.navigate(ComposerRoutes.forward(messageId))
                    },
                )
            }
            // Composer：shared-axis 上升进入，退出自然下落（设计 §5.6 动效 4 的 MVP 版）
            composable(
                route = ComposerRoutes.COMPOSER,
                arguments = listOf(
                    navArgument(ComposerRoutes.ARG_DRAFT_ID) { defaultValue = "" },
                    navArgument(ComposerRoutes.ARG_REPLY_TO) { defaultValue = "" },
                    navArgument(ComposerRoutes.ARG_MODE) { defaultValue = "NEW" },
                    navArgument(ComposerRoutes.ARG_PREFILL_TO) { defaultValue = "" },
                    navArgument(ComposerRoutes.ARG_PREFILL_SUBJECT) { defaultValue = "" },
                    navArgument(ComposerRoutes.ARG_PREFILL_BODY) { defaultValue = "" },
                ),
                enterTransition = {
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                        ),
                    )
                },
                popExitTransition = {
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
                },
            ) { entry ->
                val mode = entry.arguments?.getString(ComposerRoutes.ARG_MODE)
                    ?.let { runCatching { ComposeMode.valueOf(it) }.getOrDefault(ComposeMode.NEW) }
                    ?: ComposeMode.NEW
                val viewModel: ComposerViewModel = viewModel(
                    factory = ComposerViewModel.factory(
                        draftId = entry.arguments?.getString(ComposerRoutes.ARG_DRAFT_ID)
                            ?.takeIf { it.isNotBlank() }?.let(::DraftId),
                        replyToMessageId = entry.arguments?.getString(ComposerRoutes.ARG_REPLY_TO)
                            ?.takeIf { it.isNotBlank() }?.let(::MessageId),
                        mode = mode,
                        database = container.database,
                        messageSender = container.messageSender,
                        bodyLoader = container.bodyLoader,
                        contactSuggester = container.contactSuggester,
                        prefillTo = entry.arguments?.getString(ComposerRoutes.ARG_PREFILL_TO)
                            ?.takeIf { it.isNotBlank() },
                        prefillSubject = entry.arguments?.getString(ComposerRoutes.ARG_PREFILL_SUBJECT)
                            ?.takeIf { it.isNotBlank() },
                        prefillBody = entry.arguments?.getString(ComposerRoutes.ARG_PREFILL_BODY)
                            ?.takeIf { it.isNotBlank() },
                    ),
                )
                ComposerScreen(
                    viewModel = viewModel,
                    mode = mode,
                    onClose = { navController.popBackStack() },
                    onSent = { navController.popBackStack() },
                )
            }
        }
    }
}

