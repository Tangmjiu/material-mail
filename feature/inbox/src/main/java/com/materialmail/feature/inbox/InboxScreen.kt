@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.materialmail.feature.inbox


import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
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
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenThread: (threadId: String) -> Unit,
    onAddAccount: () -> Unit,
    onCompose: () -> Unit,
    onEditDraft: (draftId: String) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 双栏嵌入模式：隐藏底部导航与 FAB（由 WideNavigationRail 接管）。 */
    embedded: Boolean = false,
    modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val ptrState = rememberPullToRefreshState()
    val selectedIds by viewModel.selectedThreadIds.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = selectedIds.isNotEmpty()) {
        viewModel.clearSelection()
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Android 13+：进入可用态时请求一次通知权限；拒绝后不再追问
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { }
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
                is InboxEvent.BatchArchived -> "已归档 " + event.count + " 个会话"
                is InboxEvent.BatchDeleted -> "已删除 " + event.count + " 个会话"
                is InboxEvent.BatchMarkedRead -> "已将 " + event.count + " 个会话标为已读"
            }
            val undoable = event is InboxEvent.Archived || event is InboxEvent.Deleted
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (undoable) "撤销" else null,
                withDismissAction = true)
            if (undoable && result == SnackbarResult.ActionPerformed) viewModel.undoMove()
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
                        onOpenSettings = onOpenSettings)
                }
            }
        }) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (uiState is InboxUiState.Ready && !embedded) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                            label = { Text("邮件", style = MailTypeScale.meta) })
                        NavigationBarItem(
                            selected = false,
                            onClick = onSearch,
                            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            label = { Text("搜索", style = MailTypeScale.meta) })
                    }
                }
            },
            floatingActionButton = {
                // MD3E：FAB 随滚动 morph —— 静止 28dp 圆角方块，滚动中弹成圆形（spring 物理）
                if (uiState is InboxUiState.Ready && !embedded) {
                    val fabCorner by animateDpAsState(
                        targetValue = if (listState.isScrollInProgress) 48.dp else 28.dp,
                        animationSpec = MailTheme.motionScheme.defaultSpatialSpec(),
                        label = "fabShapeMorph")
                    LargeFloatingActionButton(
                        onClick = onCompose,
                        shape = RoundedCornerShape(fabCorner)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "写邮件")
                    }
                }
            },
            topBar = {
                if (selectedIds.isNotEmpty()) {
                    // MD3E 情境顶栏：多选时替换普通顶栏（容器色变换 + 批量动作）
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                            }
                        },
                        title = {
                            Text(
                                "已选 " + selectedIds.size + " 项",
                                style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            IconButton(onClick = { viewModel.markSelectedRead() }) {
                                Icon(
                                    Icons.Outlined.MarkEmailRead,
                                    contentDescription = "标为已读")
                            }
                            IconButton(onClick = { viewModel.archiveSelected() }) {
                                Icon(Icons.Outlined.Archive, contentDescription = "批量归档")
                            }
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "批量删除")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
                } else if (uiState is InboxUiState.Ready) {
                    val readyState = uiState as InboxUiState.Ready
                    // MD3E 搜索栏头部（邮件 App 的 Expressive 首屏标志）：
                    // 菜单键内嵌搜索栏 + 占位文案带当前文件夹 + 账户徽章/同步波浪指示
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SearchBar(
                            inputField = {
                                SearchBarDefaults.InputField(
                                    state = rememberTextFieldState(),
                                    onSearch = { onSearch() },
                                    expanded = false,
                                    onExpandedChange = { if (it) onSearch() },
                                    readOnly = true,
                                    placeholder = {
                                        Text(
                                            "搜索 · " + inboxTitle(uiState),
                                            style = MailTypeScale.preview,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingIcon = {
                                        IconButton(onClick = {
                                            scope.launch { drawerState.open() }
                                        }) {
                                            Icon(
                                                Icons.Outlined.Menu,
                                                contentDescription = "打开文件夹导航")
                                        }
                                    },
                                    trailingIcon = {
                                        if (readyState.syncing) {
                                            LoadingIndicator(modifier = Modifier.size(28.dp))
                                        } else {
                                            com.materialmail.designsystem.component.MonogramAvatar(
                                                name = readyState.accountEmail,
                                                size = 32.dp,
                                                modifier = Modifier.clickable {
                                                    scope.launch { drawerState.open() }
                                                })
                                        }
                                    })
                            },
                            expanded = false,
                            onExpandedChange = { if (it) onSearch() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {}
                    }
                }
            }) { innerPadding ->
            when (val state = uiState) {
                InboxUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center) { LoadingIndicator() }

                InboxUiState.NoAccount -> NoAccountState(
                    onAddAccount = onAddAccount,
                    modifier = Modifier.fillMaxSize().padding(innerPadding))

                is InboxUiState.Ready -> PullToRefreshBox(
                    isRefreshing = state.syncing,
                    onRefresh = viewModel::refresh,
                    state = ptrState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = ptrState,
                            isRefreshing = state.syncing,
                            modifier = Modifier.align(Alignment.TopCenter))
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (state.destination) {
                        InboxDestination.Drafts -> DraftList(
                            drafts = state.drafts,
                            onEditDraft = onEditDraft,
                            onDeleteDraft = viewModel::deleteDraft)

                        is InboxDestination.FolderDest ->
                            if (state.threads.isEmpty()) {
                                EmptyInboxState(
                                    folderName = state.destination.displayName,
                                    modifier = Modifier.fillMaxSize())
                            } else {
                                ThreadList(
                                    threads = state.threads,
                                    listState = listState,
                                    selectedIds = selectedIds,
                                    viewModel = viewModel,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpenThread = onOpenThread)
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
    onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
        Text(
            state.accountEmail,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Material Mail",
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            vertical = MailTheme.spacing.sm))
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
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                selected = selected,
                onClick = { onSelectFolder(folder) })
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                icon = { Icon(Icons.Outlined.Drafts, contentDescription = null) },
                selected = state.destination == InboxDestination.Drafts,
                onClick = onSelectDrafts)
        }
        item(key = "settings") {
            NavigationDrawerItem(
                label = { Text("设置") },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings)
        }
        item(key = "add-account") {
            TextButton(onClick = onAddAccount) {
                Text("添加账户", style = MailTypeScale.meta)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ThreadList(
    threads: List<InboxThreadUi>,
    listState: LazyListState,
    selectedIds: Set<String>,
    viewModel: InboxViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onOpenThread: (threadId: String) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = threads, key = { _, it -> it.threadId }) { index, thread ->
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
                })
            val rowSelected = thread.threadId in selectedIds
            val selecting = selectedIds.isNotEmpty()
            val rowBg by animateColorAsState(
                targetValue = if (rowSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                animationSpec = MailTheme.motionScheme.defaultEffectsSpec(),
                label = "rowSelect",
            )
            SwipeToDismissBox(
                // 行删除/归档后，邻近行按 spring 物理补位（Expressive 默认 spec）
                modifier = Modifier
                    .animateItem()
                    .staggeredEntrance(index),
                state = dismissState,
                // 多选模式下禁用滑动操作，避免手势冲突
                enableDismissFromStartToEnd = !selecting,
                enableDismissFromEndToStart = !selecting,
                backgroundContent = {
                    val deleting =
                        dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (deleting) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = MailTheme.spacing.xl),
                        contentAlignment =
                            if (deleting) Alignment.CenterStart else Alignment.CenterEnd) {
                        Icon(
                            if (deleting) Icons.Outlined.Delete else Icons.Outlined.Archive,
                            contentDescription = if (deleting) "删除" else "归档",
                            tint = if (deleting) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            })
                    }
                }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(
                                            key = "thread-container-" + thread.threadId),
                                        animatedVisibilityScope = animatedVisibilityScope)
                                }
                            } else {
                                Modifier
                            })
                        .combinedClickable(
                            onClick = {
                                if (selecting) {
                                    viewModel.toggleSelection(thread.threadId)
                                } else {
                                    onOpenThread(thread.threadId)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(thread.threadId) },
                        )) {
                    MailListItem(
                        selection = if (selecting) rowSelected else null,
                        sender = thread.senderLine,
                        subject = thread.subject,
                        preview = thread.snippet,
                        time = thread.timeText,
                        unread = thread.unread)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            }
        }
    }
}

@Composable
private fun DraftList(
    drafts: List<DraftUi>,
    onEditDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit) {
    if (drafts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "没有草稿。写信时退出会自动保存。",
                style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = drafts, key = { it.draftId }) { draft ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditDraft(draft.draftId) }) {
                MailListItem(
                    sender = draft.toLine,
                    subject = draft.subject,
                    preview = "",
                    time = draft.timeText,
                    unread = false)
                Row(modifier = Modifier.padding(start = MailTheme.spacing.lg)) {
                    TextButton(onClick = { onDeleteDraft(draft.draftId) }) {
                        Text(
                            "删除草稿",
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
    }
}

@Composable
private fun NoAccountState(onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    // 首启欢迎页：品牌 hero + 价值观 + 唯一 CTA（MD3E 大圆角 + spring 入场）
    val heroScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "heroPop")
    Column(
        modifier = modifier.padding(MailTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.MailOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Text(
            "Material Mail",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            "Local-first 邮箱：邮件存在你的设备上，不上传、不分析。\n支持 Gmail / QQ / 163 / Outlook 与任意 IMAP 服务器。",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(MailTheme.spacing.xl))
        androidx.compose.material3.Button(
            onClick = onAddAccount,
            modifier = Modifier.height(52.dp),
            shape = MaterialTheme.shapes.extraLarge) {
            Text("添加账户", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyInboxState(folderName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(MailTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Text(
            "$folderName 是空的",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            "下拉即可刷新。新邮件也会通过周期同步到达（受系统省电策略影响可能有延迟）。",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

/**
 * 列表交错入场（MD3E motion）：首屏/滚动进入视口的行按 25ms 阶梯
 * 延迟做 fade+上移，spring 收尾不生硬。上限 250ms 防止长列表末尾过久。
 */
private fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 25L).coerceAtMost(250L))
        progress.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        )
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36f
    }
}