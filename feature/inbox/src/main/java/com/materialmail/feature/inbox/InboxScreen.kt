package com.materialmail.feature.inbox

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.component.MailListItem
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * 收件箱 —— 全产品最重要的页面。
 *
 * 设计执行（对照设计文档 §5.8 检查单）：
 * - 无卡片、无阴影：列表用 1px 色阶分隔 + 留白；
 * - 未读 = Unread Spine + 发件人字重（MailListItem 内）；
 * - 滑动归档 = 行滑出 + Undo Snackbar；
 * - 列表 → 详情 = 容器变换（sharedElement，见 [onOpenThread] 接线处）；
 * - 下拉刷新 = 手动同步（Local-first，刷新永远可用）。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenThread: (threadId: String) -> Unit,
    onAddAccount: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.Archived -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已归档：" + event.threadSubject,
                        actionLabel = "撤销",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoArchive()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 设计规范 §5.7：右下角大号 FAB（写信），28dp Expressive 圆角
            if (uiState is InboxUiState.Ready) {
                LargeFloatingActionButton(
                    onClick = onCompose,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "写邮件")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("收件箱", style = MaterialTheme.typography.titleLarge)
                        (uiState as? InboxUiState.Ready)?.let {
                            Text(
                                it.accountEmail,
                                style = MailTypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if ((uiState as? InboxUiState.Ready)?.syncing == true) {
                        LoadingIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            InboxUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }

            InboxUiState.NoAccount -> NoAccountState(
                onAddAccount = onAddAccount,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            is InboxUiState.Ready -> PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                if (state.threads.isEmpty()) {
                    EmptyInboxState(modifier = Modifier.fillMaxSize())
                } else {
                    ThreadList(
                        threads = state.threads,
                        viewModel = viewModel,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onOpenThread = onOpenThread,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ThreadList(
    threads: List<InboxThreadUi>,
    viewModel: InboxViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenThread: (threadId: String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = threads, key = { it.threadId }) { thread ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        // 行不直接滑走：归档完成后由数据库状态驱动消失 + 邻近行 spring 补位
                        viewModel.archiveThread(thread.threadId, thread.subject)
                        false
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(end = MailTheme.spacing.xl),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Outlined.Archive,
                            contentDescription = "归档",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
            ) {
                with(sharedTransitionScope) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .sharedElement(
                                rememberSharedContentState(key = "thread-container-" + thread.threadId),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .clickable { onOpenThread(thread.threadId) },
                    ) {
                        MailListItem(
                            sender = thread.senderLine,
                            subject = thread.subject,
                            preview = thread.snippet,
                            time = thread.timeText,
                            unread = thread.unread,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoAccountState(onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(MailTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Text(
            "还没有邮箱账户",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            "添加账户后即可离线收件。支持 Gmail / QQ / 163 / Outlook 预设与任意 IMAP 服务器。",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MailTheme.spacing.xl))
        androidx.compose.material3.Button(
            onClick = onAddAccount,
            modifier = Modifier.height(52.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text("添加账户", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyInboxState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(MailTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Text(
            "收件箱是空的",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            "下拉即可刷新。新邮件也会通过周期同步到达（受系统省电策略影响可能有延迟）。",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}