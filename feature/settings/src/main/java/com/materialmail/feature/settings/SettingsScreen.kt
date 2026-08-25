@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.materialmail.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/** Pro 功能入口描述（由 pro:app 组装层注入）。 */
data class ProEntry(
    val title: String,
    val subtitle: String,
    val locked: Boolean,
    val onClick: () -> Unit,
)

/**
 * 设置首页（MD3E 分组列表）：
 * 每个分区 = 28dp 圆角色阶容器（surfaceContainerLow），行 = 图标徽章 + 主/次标题，
 * 组内用 1px 色阶分隔。触控目标 ≥ 56dp。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenActionLog: () -> Unit,
    onOpenAgentModel: () -> Unit,
    onOpenAgentChat: () -> Unit,
    onOpenAgentPermissions: () -> Unit,
    onOpenRegion: () -> Unit,
    onOpenYolo: () -> Unit,
    /** Pro 功能入口槽（pro:app 壳注入；Community 版为空，物理上无 Pro 痕迹）。 */
    proEntries: List<ProEntry> = emptyList(),
    /** Pro 状态横幅（pro:app 注入；Community 恒 null）。 */
    proBanner: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MailTheme.spacing.lg),
        ) {
            if (proBanner != null) {
                item { proBanner() }
            }

            // ── 同步 ─────────────────────────────────────
            item { SectionLabel("同步") }
            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.Sync)
                            Spacer(Modifier.size(MailTheme.spacing.md))
                            Text(
                                "后台同步频率",
                                style = MailTypeScale.subject,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(MailTheme.spacing.md))
                        val options = listOf<Pair<Long?, String>>(
                            15L to "15 分钟",
                            30L to "30 分钟",
                            60L to "1 小时",
                            null to "仅手动",
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (minutes, label) ->
                                SegmentedButton(
                                    selected = uiState.syncIntervalMinutes == minutes,
                                    onClick = { viewModel.setSyncInterval(minutes) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index, count = options.size,
                                    ),
                                ) {
                                    Text(label, style = MailTypeScale.meta)
                                }
                            }
                        }
                        Spacer(Modifier.height(MailTheme.spacing.sm))
                        Text(
                            "Local-first：没有服务器推送，新邮件靠周期同步到达。下拉刷新永远可用。",
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 账户（此前 AccountRow 是死代码，从未渲染）──────────
            if (uiState.accounts.isNotEmpty()) {
                item { SectionLabel("账户") }
                item {
                    SettingsGroup {
                        uiState.accounts.forEachIndexed { index, account ->
                            if (index > 0) GroupDivider()
                            AccountRow(
                                account = account,
                                onDelete = { viewModel.deleteAccount(account.accountId) },
                                onUpdateSignature = {
                                    viewModel.updateSignature(account.accountId, it)
                                },
                            )
                        }
                    }
                }
            }

            // ── Agent ────────────────────────────────────
            item { SectionLabel("Agent") }
            item {
                SettingsGroup {
                    ExpressiveEntry(
                        icon = Icons.Outlined.Psychology,
                        title = "AI 模型",
                        subtitle = "OpenAI 兼容端点：DeepSeek / 通义 / Kimi / 智谱 / 自定义",
                        onClick = onOpenAgentModel,
                    )
                    GroupDivider()
                    ExpressiveEntry(
                        icon = Icons.Outlined.Forum,
                        title = "Agent 对话",
                        subtitle = "用自然语言查询邮箱（只读工具，写操作仍需确认）",
                        onClick = onOpenAgentChat,
                    )
                    GroupDivider()
                    ExpressiveEntry(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = "Agent 权限",
                        subtitle = "逐项授权 Agent 可使用的邮箱能力",
                        onClick = onOpenAgentPermissions,
                    )
                    GroupDivider()
                    ExpressiveEntry(
                        icon = Icons.Outlined.History,
                        title = "操作记录",
                        subtitle = "Agent 每次执行的完整审计日志",
                        onClick = onOpenActionLog,
                    )
                    GroupDivider()
                    ExpressiveEntry(
                        icon = Icons.Outlined.WarningAmber,
                        title = "YOLO Mode",
                        subtitle = "高风险实验功能：免逐条确认，限时自动执行",
                        onClick = onOpenYolo,
                    )
                }
            }

            // ── 隐私 ─────────────────────────────────────
            item { SectionLabel("隐私") }
            item {
                SettingsGroup {
                    ExpressiveEntry(
                        icon = Icons.Outlined.Public,
                        title = "地区与服务可用性",
                        subtitle = "地区检测状态、手动覆盖、提示管理",
                        onClick = onOpenRegion,
                    )
                }
            }

            // ── Pro ──────────────────────────────────────
            if (proEntries.isNotEmpty()) {
                item { SectionLabel("Pro") }
                item {
                    SettingsGroup {
                        proEntries.forEachIndexed { index, entry ->
                            if (index > 0) GroupDivider()
                            ExpressiveEntry(
                                icon = if (entry.locked) {
                                    Icons.Outlined.Lock
                                } else {
                                    Icons.Outlined.AutoAwesome
                                },
                                title = entry.title,
                                subtitle = entry.subtitle,
                                locked = entry.locked,
                                onClick = entry.onClick,
                            )
                        }
                    }
                }
            }

            // ── 关于 ─────────────────────────────────────
            item { SectionLabel("关于") }
            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier.padding(MailTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(Icons.Outlined.Info)
                        Spacer(Modifier.size(MailTheme.spacing.md))
                        Column {
                            Text(
                                "Material Mail",
                                style = MailTypeScale.subject,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "版本 " + appVersion() + " · MPL-2.0 开源 · Local First",
                                style = MailTypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(MailTheme.spacing.xl)) }
        }
    }
}

/** 分区标签（Expressive：primary 色、加宽字距）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MailTypeScale.meta,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = MailTheme.spacing.sm,
            top = MailTheme.spacing.xl,
            bottom = MailTheme.spacing.sm,
        ),
    )
}

/** 分区圆角容器。 */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(start = 72.dp),
    )
}

/** 图标徽章：secondaryContainer 圆底 + primary 图标。 */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** MD3E 设置行：图标徽章 + 主/次标题 + 尾部状态。 */
@Composable
private fun ExpressiveEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MailTheme.spacing.lg, vertical = MailTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.size(MailTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MailTypeScale.subject, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (locked) {
                Icons.Outlined.Lock
            } else {
                Icons.AutoMirrored.Outlined.KeyboardArrowRight
            },
            contentDescription = null,
            tint = if (locked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"
    }.getOrDefault("dev")
}

@Composable
private fun AccountRow(
    account: AccountManageUi,
    onDelete: () -> Unit,
    onUpdateSignature: (String?) -> Unit,
) {
    var showConfirm by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var showSignature by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var signatureText by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MailTheme.spacing.lg, vertical = MailTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Outlined.AccountCircle)
        Spacer(Modifier.size(MailTheme.spacing.md))
        Text(
            account.email,
            style = MailTypeScale.subject,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showSignature = true }) {
            Text("签名", style = MailTypeScale.meta)
        }
        TextButton(onClick = { showConfirm = true }) {
            Text("删除", style = MailTypeScale.meta, color = MaterialTheme.colorScheme.error)
        }
    }
    if (showSignature) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSignature = false },
            title = { Text("邮件签名") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = signatureText,
                    onValueChange = { signatureText = it },
                    placeholder = { Text("例：发自 Material Mail") },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignature = false
                    onUpdateSignature(signatureText.ifBlank { null })
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSignature = false }) { Text("取消") }
            },
        )
    }
    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除账户？") },
            text = {
                Text(
                    "将删除 " + account.email + " 及其全部本地邮件、草稿和凭据。" +
                        "服务器上的邮件不受影响。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }
}
