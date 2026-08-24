package com.materialmail.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/** 设置首页。分区用色阶与留白，不用卡片堆。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenActionLog: () -> Unit,
    onOpenAgentPermissions: () -> Unit,
    onOpenRegion: () -> Unit,
    onOpenYolo: () -> Unit,
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item { SectionLabel("同步") }
            item {
                Column(modifier = Modifier.padding(horizontal = MailTheme.spacing.lg)) {
                    Text(
                        "后台同步频率",
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(MailTheme.spacing.sm))
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
                    Spacer(Modifier.height(MailTheme.spacing.xs))
                    Text(
                        "Local-first：没有服务器推送，新邮件靠周期同步到达。下拉刷新永远可用。",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { DividerItem() }

            item { SectionLabel("Agent") }
            item {
                SettingsEntry(
                    title = "Agent 权限",
                    subtitle = "逐项授权 Agent 可使用的邮箱能力",
                    onClick = onOpenAgentPermissions,
                )
            }
            item {
                SettingsEntry(
                    title = "操作记录",
                    subtitle = "Agent 每次执行的完整审计日志",
                    onClick = onOpenActionLog,
                )
            }
            item {
                SettingsEntry(
                    title = "YOLO Mode",
                    subtitle = "Experimental high-risk feature",
                    onClick = onOpenYolo,
                )
            }
            item { DividerItem() }

            item { SectionLabel("隐私") }
            item {
                SettingsEntry(
                    title = "地区与服务可用性",
                    subtitle = "地区检测状态、手动覆盖、提示管理",
                    onClick = onOpenRegion,
                )
            }
            item { DividerItem() }

            item { SectionLabel("关于") }
            item {
                Column(modifier = Modifier.padding(horizontal = MailTheme.spacing.lg)) {
                    Text(
                        "Material Mail（Community Edition）",
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "MPL-2.0 开源 · Local First · Privacy First",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MailTypeScale.meta,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = MailTheme.spacing.lg,
            top = MailTheme.spacing.xl,
            bottom = MailTheme.spacing.sm,
        ),
    )
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MailTheme.spacing.lg, vertical = MailTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MailTypeScale.subject, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DividerItem() {
    Spacer(Modifier.height(MailTheme.spacing.md))
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
}