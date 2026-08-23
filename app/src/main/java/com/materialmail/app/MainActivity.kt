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
import androidx.navigation.compose.rememberNavController
import com.materialmail.designsystem.theme.MaterialMailTheme
import com.materialmail.feature.inbox.InboxRoutes
import com.materialmail.feature.inbox.InboxScreen
import com.materialmail.feature.inbox.InboxViewModel
import com.materialmail.feature.inbox.ThreadDetailScreen
import com.materialmail.feature.inbox.ThreadDetailViewModel
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.work.SyncScheduler

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
                )
            }
        }
    }
}

