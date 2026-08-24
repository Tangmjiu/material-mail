package com.materialmail.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialmail.core.model.ThreadId
import com.materialmail.designsystem.theme.MailTypeScale
import com.materialmail.feature.inbox.InboxScreen
import com.materialmail.feature.inbox.InboxViewModel
import com.materialmail.feature.inbox.ThreadDetailScreen
import com.materialmail.feature.inbox.ThreadDetailViewModel

/**
 * 平板/折叠屏双栏主页（设计 §5.7：列表-详情双栏，同一套代码自适应，不是放大）。
 * 双栏模式不做容器变换（两个面板常驻），宽度 400dp 列表 + 弹性详情。
 */
@Composable
fun TwoPaneHome(
    container: AppContainer,
    onManualRefresh: () -> Unit,
    onAddAccount: () -> Unit,
    onCompose: () -> Unit,
    onEditDraft: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onReply: (String) -> Unit,
    onReplyAll: (String) -> Unit,
    onForward: (String) -> Unit,
) {
    var selectedThread by rememberSaveable { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(400.dp).fillMaxHeight()) {
            val inboxViewModel: InboxViewModel = viewModel(
                factory = InboxViewModel.factory(
                    database = container.database,
                    actionPerformer = container.actionPerformer,
                    onManualRefresh = onManualRefresh,
                ),
            )
            InboxScreen(
                viewModel = inboxViewModel,
                sharedTransitionScope = null, // 双栏无容器变换
                animatedVisibilityScope = null,
                onOpenThread = { selectedThread = it },
                onAddAccount = onAddAccount,
                onCompose = onCompose,
                onEditDraft = onEditDraft,
                onSearch = onSearch,
                onOpenSettings = onOpenSettings,
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val threadId = selectedThread
            if (threadId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "选择一封邮件开始阅读",
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val detailViewModel: ThreadDetailViewModel = viewModel(
                    key = "pane-" + threadId,
                    factory = ThreadDetailViewModel.factory(
                        threadId = ThreadId(threadId),
                        database = container.database,
                        bodyLoader = container.bodyLoader,
                        actionPerformer = container.actionPerformer,
                        attachmentDownloader = container.attachmentDownloader,
                    ),
                )
                ThreadDetailScreen(
                    viewModel = detailViewModel,
                    threadId = threadId,
                    sharedTransitionScope = null,
                    animatedVisibilityScope = null,
                    onBack = { selectedThread = null },
                    onReply = onReply,
                    onReplyAll = onReplyAll,
                    onForward = onForward,
                )
            }
        }
    }
}