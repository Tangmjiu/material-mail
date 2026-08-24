package com.materialmail.feature.settings.yolo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.agent.yolo.YoloCapabilities
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * YOLO Mode 设置页（需求 §36-§58 的完整实现）。
 *
 * Design Review 要点（需求 §58）：
 * - 第一步默认焦点在「返回」；
 * - 无任何诱导性高亮，「继续」按钮从不默认选中；
 * - 第三步必须手动输入 YOLO，不能只点按钮；
 * - 开启中状态提示常驻但不红色警报；
 * - 警告明显但不制造恐慌。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoloScreen(
    viewModel: YoloViewModel,
    onBack: () -> Unit,
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
                title = {
                    Column {
                        Text("YOLO Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Experimental high-risk feature",
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val step = uiState.step
        when {
            step != null -> EnableFlowPage(
                step = step,
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> ManagePage(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

// ── 管理页（未开启：入口说明；已开启：状态 + 权限集 + 关闭）─────────────

@Composable
private fun ManagePage(
    uiState: YoloUiState,
    viewModel: YoloViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(MailTheme.spacing.lg),
    ) {
        if (uiState.active) {
            // 需求 §50：开启期间持续存在明显但不扰人的状态提示
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "⚡ YOLO Mode Active",
                    style = MailTypeScale.subject,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(MailTheme.spacing.md),
                )
            }
            Spacer(Modifier.height(MailTheme.spacing.lg))
        } else {
            Text(
                "YOLO Mode 允许 Agent 在你授权的 Capability 范围内更自主地执行操作。" +
                    "适合高级用户、自动化用户、Power User。普通用户不需要开启。",
                style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MailTheme.spacing.lg))
            OutlinedButton(
                onClick = viewModel::startEnableFlow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("了解并开启")
            }
            return@ManagePage  // 未开启时只显示入口说明
        }

        Text(
            "YOLO 权限集",
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(MailTheme.spacing.sm))
        YoloCapabilityToggles(uiState.capabilities) { transform ->
            viewModel.updateCapabilities(transform)
        }

        Spacer(Modifier.height(MailTheme.spacing.xl))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(Modifier.height(MailTheme.spacing.lg))
        TextButton(
            onClick = viewModel::disable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "关闭 YOLO Mode",
                color = MaterialTheme.colorScheme.error,
                style = MailTypeScale.subject,
            )
        }
    }
}

@Composable
private fun YoloCapabilityToggles(
    caps: YoloCapabilities,
    onChange: ((YoloCapabilities) -> YoloCapabilities) -> Unit,
) {
    @Composable
    fun row(
        label: String,
        highRisk: Boolean,
        value: Boolean,
        set: YoloCapabilities.(Boolean) -> YoloCapabilities,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MailTypeScale.subject,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (highRisk) {
                    Spacer(Modifier.padding(start = MailTheme.spacing.sm))
                    Text(
                        "高风险",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(checked = value, onCheckedChange = { v -> onChange { caps -> caps.set(v) } })
        }
    }

    Column {
        row("读取邮件", false, caps.readMail) { v -> copy(readMail = v) }
        row("搜索邮件", false, caps.searchMail) { v -> copy(searchMail = v) }
        row("修改标签", false, caps.modifyMail) { v -> copy(modifyMail = v) }
        row("归档邮件", false, caps.archiveMail) { v -> copy(archiveMail = v) }
        row("创建草稿", false, caps.createDraft) { v -> copy(createDraft = v) }
        row("删除邮件", true, caps.deleteMail) { v -> copy(deleteMail = v) }
        row("发送邮件", true, caps.sendMail) { v -> copy(sendMail = v) }
        row("执行自动化", true, caps.executeAutomation) { v -> copy(executeAutomation = v) }
        row("使用连接器", true, caps.useConnectors) { v -> copy(useConnectors = v) }
        row("发送 IM 消息", true, caps.sendImMessage) { v -> copy(sendImMessage = v) }
    }
}

// ── 四步启用流程 ────────────────────────────────────────────

@Composable
private fun EnableFlowPage(
    step: YoloEnableStep,
    uiState: YoloUiState,
    viewModel: YoloViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(MailTheme.spacing.lg),
    ) {
        when (step) {
            YoloEnableStep.WARNING -> {
                // 需求 §37：第一步默认焦点必须在「返回」
                val backFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { backFocus.requestFocus() }

                Text(
                    "⚠️ YOLO Mode",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(MailTheme.spacing.md))
                Text(
                    "YOLO Mode 会显著扩大 Agent 可以自主执行的操作范围。\n\n" +
                        "可能包括：自动修改邮件、批量处理邮件、创建和执行工作流、" +
                        "操作第三方服务、调用 Connector、自动执行多个连续操作。\n\n" +
                        "Agent 可能执行你没有逐步确认的操作。\n\n请确认你理解相关风险。",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(MailTheme.spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                    OutlinedButton(
                        onClick = viewModel::cancelEnableFlow,
                        modifier = Modifier.focusRequester(backFocus),
                    ) { Text("返回") }
                    TextButton(onClick = viewModel::nextStep) { Text("我了解风险，继续") }
                }
            }

            YoloEnableStep.RISK_CHECKBOX -> {
                // 需求 §38：第二次确认，必须主动勾选
                Text(
                    "你真的确定吗？",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(MailTheme.spacing.md))
                Text(
                    "YOLO Mode 不代表 Agent 永远正确。Agent 可能：\n\n" +
                        "· 理解错误\n· 判断错误\n· 修改错误内容\n· 执行错误操作\n" +
                        "· 产生意外结果\n· 因第三方 API 行为产生错误",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(MailTheme.spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.riskChecked,
                        onCheckedChange = viewModel::onRiskChecked,
                    )
                    Text(
                        "我已经阅读并理解上述风险。",
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(MailTheme.spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                    OutlinedButton(onClick = viewModel::cancelEnableFlow) { Text("返回") }
                    TextButton(
                        onClick = viewModel::nextStep,
                        enabled = uiState.riskChecked,
                    ) { Text("继续") }
                }
            }

            YoloEnableStep.TYPE_YOLO -> {
                // 需求 §39：必须输入 YOLO，不能只点按钮
                Text(
                    "输入确认",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(MailTheme.spacing.md))
                Text(
                    "在下方输入 YOLO 以继续。",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(MailTheme.spacing.lg))
                OutlinedTextField(
                    value = uiState.typedInput,
                    onValueChange = viewModel::onTypedInput,
                    label = { Text("输入 YOLO") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MailTheme.spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                    OutlinedButton(onClick = viewModel::cancelEnableFlow) { Text("返回") }
                    TextButton(
                        onClick = viewModel::nextStep,
                        enabled = uiState.typedInput.trim().uppercase()
                            .let { it == "YOLO" || it == "ENABLE YOLO" },
                    ) { Text("继续") }
                }
            }

            YoloEnableStep.FINAL -> {
                // 需求 §40 + §41：最终确认 + 用户责任确认
                Text(
                    "最后一次确认",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(MailTheme.spacing.md))
                Text(
                    "你即将开启 YOLO Mode。开启后，Material Mail Agent 将可以在你授权的 " +
                        "Capability 范围内更加自主地执行操作。\n\n" +
                        "YOLO Mode 是实验性高级功能。你主动选择扩大 Agent 的自主操作权限。" +
                        "Material Mail 会尽合理努力提供权限控制、操作记录和安全保护，" +
                        "但无法保证 Agent 永远不会产生错误结果。\n\n" +
                        "你可以随时关闭 YOLO Mode。",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(MailTheme.spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.responsibilityChecked,
                        onCheckedChange = viewModel::onResponsibilityChecked,
                    )
                    Text(
                        "我理解并接受上述风险。",
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(MailTheme.spacing.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                    OutlinedButton(onClick = viewModel::cancelEnableFlow) { Text("取消") }
                    TextButton(
                        onClick = viewModel::confirmEnable,
                        enabled = uiState.responsibilityChecked,
                    ) { Text("开启 YOLO Mode") }
                }
            }
        }
    }
}