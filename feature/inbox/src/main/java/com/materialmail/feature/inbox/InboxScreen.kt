package com.materialmail.feature.inbox

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.core.model.FolderRole
import com.materialmail.designsystem.component.MailListItem
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import kotlinx.coroutines.launch

/**
 * 收件箱 —— 全产品最重要的页面。
 *
 * 设计执行（对照设计文档 §5.8 检查单）：
 * - 无卡片、无阴影：1px 色阶分隔 + 留白；
 * - 未读 = Unread Spine + 发件人字重；
 * - 滑动归档/删除 + Undo；容器变换进入详情；
 * - 抽屉承载文件夹导航（收件箱/已发送/垃圾箱/自定义夹 + 本地草稿）。
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
    onEditDraft: (draftId: String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Android 13+：进入可用态时请求一次通知权限；拒绝后不再追问
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(uiState is InboxUiState.Ready) {
        if (uiState is InboxUiState.Ready && Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is InboxEvent.Archived -> "已归档：" + event.threadSubject
                is InboxEvent.Deleted -> "已移入垃圾箱：" + event.threadSubject
            }
            val result = snackbarHostState.showSnackbar(
                message = message, actionLabel = "撤销", withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoMove()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                (uiState as? InboxUiState.Ready)?.let { state ->
                    DrawerContent(
                        state = state,
                        onSelectAccount = { accountId ->
                            viewModel.selectAccount(accountId)
                        },
                        onSelectFolder = { folder ->
                            viewModel.selectFolder(folder.folderId, folder.displayName)
                            scope.launch { drawerState.close() }
                        },
                        onSelectDrafts = {
                            viewModel.selectDrafts()
                            scope.launch { drawerState.close() }
                        },
                        onAddAccount = onAddAccount,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (uiState is InboxUiState.Ready) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                            label = { Text("邮件", style = MailTypeScale.meta) },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onSearch,
                            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            label = { Text("搜索", style = MailTypeScale.meta) },
                        )
                    }
                }
            },
            floatingActionButton = {
                // 设计 §5.7：右下角大号 FAB（写信），28dp Expressive 圆角
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
                    navigationIcon = {
                        if (uiState is InboxUiState.Ready) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "打开文件夹导航")
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                inboxTitle(uiState),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            (uiState as? InboxUiState.Ready)?.let {
                                Text(
                                    it.accountEmail,
                                    style = MailTypeScale.meta,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if ((uiState as? InboxUiState.Ready)?.syncing == true) {
                            LoadingIndicator(
                                modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            )
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
                ) { LoadingIndicator() }

                InboxUiState.NoAccount -> NoAccountState(
                    onAddAccount = onAddAccount,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )

                is InboxUiState.Ready -> PullToRefreshBox(
                    isRefreshing = state.syncing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    when (state.destination) {
                        InboxDestination.Drafts -> DraftList(
                            drafts = state.drafts,
                            onEditDraft = onEditDraft,
                            onDeleteDraft = viewModel::deleteDraft,
                        )

                        is InboxDestination.FolderDest ->
                            if (state.threads.isEmpty()) {
                                EmptyInboxState(
                                    folderName = state.destination.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                )
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
    }
}

private fun inboxTitle(state: InboxUiState): String = when (val s = state) {
    is InboxUiState.Ready -> when (val d = s.destination) {
        is InboxDestination.FolderDest -> d.displayName
        InboxDestination.Drafts -> "草稿"
    }
    else -> "收件箱"
}

@Composable
private fun DrawerContent(
    state: InboxUiState.Ready,
    onSelectAccount: (String) -> Unit,
    onSelectFolder: (FolderUi) -> Unit,
    onSelectDrafts: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
        Text(
            state.accountEmail,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Material Mail",
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 多账户切换（设计 MVP P1）
    if (state.accounts.size > 1) {
        state.accounts.forEach { (accountId, email) ->
            if (accountId != state.accountId) {
                Text(
                    email,
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectAccount(accountId) }
                        .padding(
                            horizontal = MailTheme.spacing.lg,
                            vertical = MailTheme.spacing.sm,
                        ),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

    LazyColumn(modifier = Modifier.padding(MailTheme.spacing.md)) {
        items(items = state.folders, key = { it.folderId }) { folder ->
            val selected = (state.destination as? InboxDestination.FolderDest)
                ?.folderId == folder.folderId
            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.displayName, modifier = Modifier.weight(1f))
                        if (folder.unreadCount > 0) {
                            Text(
                                folder.unreadCount.toString(),
                                style = MailTypeScale.meta,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                selected = selected,
                onClick = { onSelectFolder(folder) },
            )
        }
        item(key = "drafts") {
            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("草稿", modifier = Modifier.weight(1f))
                        if (state.drafts.isNotEmpty()) {
                            Text(
                                state.drafts.size.toString(),
                                style = MailTypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                icon = { Icon(Icons.Outlined.Drafts, contentDescription = null) },
                selected = state.destination == InboxDestination.Drafts,
                onClick = onSelectDrafts,
            )
        }
        item(key = "settings") {
            NavigationDrawerItem(
                label = { Text("设置") },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
            )
        }
        item(key = "add-account") {
            TextButton(onClick = onAddAccount) {
                Text("添加账户", style = MailTypeScale.meta)
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
                    // 行不直接滑走：操作完成后由数据库状态驱动消失 + 邻近行 spring 补位
                    when (value) {
                        SwipeToDismissBoxValue.EndToStart ->
                            viewModel.archiveThread(thread.threadId, thread.subject)
                        SwipeToDismissBoxValue.StartToEnd ->
                            viewModel.deleteThread(thread.threadId, thread.subject)
                        else -> Unit
                    }
                    false
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val deleting =
                        dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (deleting) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer,
                            )
                            .padding(horizontal = MailTheme.spacing.xl),
                        contentAlignment =
                            if (deleting) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Icon(
                            if (deleting) Icons.Outlined.Delete else Icons.Outlined.Archive,
                            contentDescription = if (deleting) "删除" else "归档",
                            tint = if (deleting) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
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
                                rememberSharedContentState(
                                    key = "thread-container-" + thread.threadId,
                                ),
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
private fun DraftList(
    drafts: List<DraftUi>,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
) {
    if (drafts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "没有草稿。写信时退出会自动保存。",
                style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = drafts, key = { it.draftId }) { draft ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditDraft(draft.draftId) },
            ) {
                MailListItem(
                    sender = draft.toLine,
                    subject = draft.subject,
                    preview = "",
                    time = draft.timeText,
                    unread = false,
                )
                Row(modifier = Modifier.padding(start = MailTheme.spacing.lg)) {
                    TextButton(onClick = { onDeleteDraft(draft.draftId) }) {
                        Text(
                            "删除草稿",
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
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
private fun EmptyInboxState(folderName: String, modifier: Modifier = Modifier) {
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
            "$folderName 是空的",
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