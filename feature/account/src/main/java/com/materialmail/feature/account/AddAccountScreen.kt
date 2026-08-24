@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.materialmail.feature.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.core.model.Encryption
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * 添加账户 = 引导流程（设计 §5.1 首启引导）：
 * 选服务商（品牌图标） → 填凭证（服务器参数已自动带出，可展开高级） →
 * 真实 IMAP 握手验证 → 成功/失败。步骤间 shared-axis 横移，符合 MD3E 导航语义。
 */
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.SUCCESS) {
            kotlinx.coroutines.delay(900) // 让成功动画被看见，再进收件箱
            onSaved()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.step) {
                            OnboardingStep.CREDENTIALS -> viewModel.backToProviders()
                            OnboardingStep.FAILURE -> viewModel.retry()
                            OnboardingStep.CONNECTING -> Unit // 连接中不允许打断
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        when (uiState.step) {
                            OnboardingStep.PROVIDER -> "添加邮箱账户"
                            OnboardingStep.CREDENTIALS ->
                                uiState.selectedPreset?.displayName ?: "手动配置"
                            OnboardingStep.CONNECTING -> "正在连接"
                            OnboardingStep.SUCCESS -> "完成"
                            OnboardingStep.FAILURE -> "连接失败"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.step,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            transitionSpec = {
                // shared-axis X：前进右进左出，后退反之（MD3E 导航语义）
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "onboardingStep",
        ) { step ->
            when (step) {
                OnboardingStep.PROVIDER -> ProviderPickerStep(
                    onSelect = viewModel::selectPreset,
                )
                OnboardingStep.CREDENTIALS -> CredentialsStep(
                    uiState = uiState,
                    viewModel = viewModel,
                )
                OnboardingStep.CONNECTING -> ConnectingStep(uiState)
                OnboardingStep.SUCCESS -> SuccessStep(uiState)
                OnboardingStep.FAILURE -> FailureStep(
                    uiState = uiState,
                    onRetry = viewModel::connectAndSave,
                    onEdit = viewModel::retry,
                    onSaveAnyway = viewModel::saveAnyway,
                )
            }
        }
    }
}

/** 第一步：选服务商。 */
@Composable
private fun ProviderPickerStep(
    onSelect: (ProviderPreset?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
                Text(
                    "选择你的邮箱服务商",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "服务器参数会自动填好，通常只需要账号和授权码",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = ProviderPresets.ALL, key = { it.id }) { preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(preset) }
                    .padding(
                        horizontal = MailTheme.spacing.lg,
                        vertical = MailTheme.spacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderIcon(preset.id, modifier = Modifier.size(36.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MailTheme.spacing.lg),
                ) {
                    Text(
                        preset.displayName,
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        preset.domainsHint,
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
            HorizontalDivider(
                modifier = Modifier.padding(start = MailTheme.spacing.lg + 52.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(null) }
                    .padding(
                        horizontal = MailTheme.spacing.lg,
                        vertical = MailTheme.spacing.md,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderIcon(null, modifier = Modifier.size(36.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = MailTheme.spacing.lg),
                ) {
                    Text(
                        "其他邮箱",
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "手动配置 IMAP / SMTP",
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
    }
}

/** 第二步：填凭证（服务器参数自动带出，高级设置可改）。 */
@Composable
private fun CredentialsStep(
    uiState: AddAccountUiState,
    viewModel: AddAccountViewModel,
) {
    var advancedOpen by rememberSaveable { mutableStateOf(uiState.selectedPreset == null) }
    val preset = uiState.selectedPreset

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MailTheme.spacing.lg),
    ) {
        if (preset != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderIcon(preset.id, modifier = Modifier.size(44.dp))
                Column(modifier = Modifier.padding(start = MailTheme.spacing.md)) {
                    Text(preset.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        preset.domainsHint,
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(MailTheme.spacing.md))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(modifier = Modifier.padding(MailTheme.spacing.md)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        preset.hint,
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = MailTheme.spacing.sm),
                    )
                }
            }
            Spacer(Modifier.height(MailTheme.spacing.lg))
        }

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("邮箱地址") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MailTheme.spacing.sm))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = {
                Text(if (preset != null) "授权码 / 应用专用密码" else "密码或授权码")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MailTheme.spacing.md))
        TextButton(onClick = { advancedOpen = !advancedOpen }) {
            Text(
                if (advancedOpen) "收起服务器设置" else "高级：服务器设置（已自动填好）",
                style = MailTypeScale.meta,
            )
        }
        if (advancedOpen) {
            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::onHostChanged,
                label = { Text("IMAP 服务器") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MailTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::onPortChanged,
                    label = { Text("IMAP 端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.smtpHost,
                    onValueChange = viewModel::onSmtpHostChanged,
                    label = { Text("SMTP 服务器") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
            }
            Spacer(Modifier.height(MailTheme.spacing.sm))
            OutlinedTextField(
                value = uiState.smtpPort,
                onValueChange = viewModel::onSmtpPortChanged,
                label = { Text("SMTP 端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MailTheme.spacing.sm))
            val encOptions = listOf(
                Encryption.SSL_TLS to "SSL/TLS",
                Encryption.STARTTLS to "STARTTLS",
                Encryption.NONE to "明文",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                encOptions.forEachIndexed { index, (enc, label) ->
                    SegmentedButton(
                        selected = uiState.encryption == enc,
                        onClick = { viewModel.onEncryptionChanged(enc) },
                        shape = SegmentedButtonDefaults.itemShape(index, encOptions.size),
                    ) { Text(label, style = MailTypeScale.meta) }
                }
            }
            if (uiState.encryption == Encryption.NONE) {
                Spacer(Modifier.height(MailTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.cleartextConfirmed,
                        onCheckedChange = viewModel::onCleartextConfirmedChanged,
                    )
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        " 明文连接不加密，密码可能被窃听。我了解风险。",
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(MailTheme.spacing.xl))
        Button(
            onClick = viewModel::connectAndSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("验证并添加", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            "添加前会真实连接服务器验证账号，密码只保存在本机（Keystore 加密）",
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 第三步：连接中（波浪 LoadingIndicator + 目标服务器）。 */
@Composable
private fun ConnectingStep(uiState: AddAccountUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MailTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Text("正在验证账户", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MailTheme.spacing.xs))
        Text(
            "连接 ${uiState.host}:${uiState.port} · ${uiState.email}",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 第四步：成功（对勾 spring 弹入）。 */
@Composable
private fun SuccessStep(uiState: AddAccountUiState) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "successPop",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(MailTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(MailTheme.spacing.lg))
        Text("添加成功", style = MaterialTheme.typography.headlineMedium)
        Text(
            uiState.email,
            style = MailTypeScale.subject,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MailTheme.spacing.xs))
        Text(
            "正在进入收件箱，首次同步马上开始",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 失败步：明确的下一步（重试 / 改参数 / 确认无误直接保存）。 */
@Composable
private fun FailureStep(
    uiState: AddAccountUiState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onSaveAnyway: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MailTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(MailTheme.spacing.md))
        Text("没能连上服务器", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MailTheme.spacing.sm))
        Text(
            uiState.error ?: "未知错误",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(MailTheme.spacing.xl))
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("返回修改")
        }
        Spacer(Modifier.height(MailTheme.spacing.sm))
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("重试连接")
        }
        TextButton(onClick = onSaveAnyway, modifier = Modifier.fillMaxWidth()) {
            Text(
                "我确认参数无误，仍然保存",
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
