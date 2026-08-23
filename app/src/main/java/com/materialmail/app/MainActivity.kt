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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialMailTheme {
                MaterialMailNavHost(container = (application as MaterialMailApp).container)
            }
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
    navController: NavHostController = rememberNavController(),
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = InboxRoutes.INBOX) {
            composable(InboxRoutes.INBOX) {
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

