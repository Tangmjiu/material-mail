package com.materialmail.feature.composer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * 写信页。设计执行：
 * - 单手优先：收件人/主题紧凑在顶部，正文占据剩余全部空间；
 * - 输入框去容器化（透明底色，Ink & Paper 的纸面感），分隔靠 1px 色阶；
 * - 正文排版 = composerBody（bodyLarge，行高 1.6）；
 * - 关闭不丢内容：草稿防抖自动保存，退出即已落库。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposerScreen(
    viewModel: ComposerViewModel,
    mode: ComposeMode,
    onClose: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // SAF 选附件（可多选；不持久化授权——草稿不保存附件，诚实告知）
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris?.forEach { uri ->
            runCatching {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "attachment"
                val mime = context.contentResolver.getType(uri)
                    ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) viewModel.addAttachment(name, mime, bytes)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ComposerEvent.Sent -> onSent()
                is ComposerEvent.Failed ->
                    snackbarHostState.showSnackbar("发送失败：" + event.reason)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭（草稿已自动保存）")
                        }
                    },
                    title = {
                        Text(
                            when (mode) {
                                ComposeMode.NEW -> "写邮件"
                                ComposeMode.REPLY, ComposeMode.REPLY_ALL -> "回复"
                                ComposeMode.FORWARD -> "转发"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                            enabled = !uiState.sending,
                        ) {
                            Icon(
                                Icons.Outlined.AttachFile,
                                contentDescription = "添加附件（草稿不保存附件）",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            uiState.accountEmail,
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(MailTheme.spacing.sm))
                        IconButton(onClick = viewModel::send, enabled = !uiState.sending) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "发送",
                                tint = if (uiState.sending) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (uiState.sending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MailTheme.spacing.lg),
        ) {
            uiState.error?.let {
                Spacer(Modifier.height(MailTheme.spacing.sm))
                Text(it, style = MailTypeScale.preview, color = MaterialTheme.colorScheme.error)
            }

            ComposerField(
                value = uiState.to,
                onValueChange = viewModel::onToChanged,
                label = "收件人",
                keyboardType = KeyboardType.Email,
            )
            // 收件人联想（本地数据，零权限零网络）
            uiState.suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.applySuggestion(suggestion) }
                        .padding(vertical = MailTheme.spacing.sm),
                ) {
                    Text(
                        suggestion.displayName,
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(MailTheme.spacing.sm))
                    Text(
                        suggestion.address,
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                if (!uiState.showCcBcc) {
                    TextButton(onClick = viewModel::onToggleCcBcc) {
                        Text("添加抄送/密送", style = MailTypeScale.meta)
                    }
                }
            }
            if (uiState.showCcBcc) {
                ComposerField(
                    value = uiState.cc,
                    onValueChange = viewModel::onCcChanged,
                    label = "抄送",
                    keyboardType = KeyboardType.Email,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                ComposerField(
                    value = uiState.bcc,
                    onValueChange = viewModel::onBccChanged,
                    label = "密送",
                    keyboardType = KeyboardType.Email,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }

            ComposerField(
                value = uiState.subject,
                onValueChange = viewModel::onSubjectChanged,
                label = "主题",
                keyboardType = KeyboardType.Text,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            if (uiState.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MailTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm),
                ) {
                    uiState.attachments.forEachIndexed { index, attachment ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeAttachment(index) },
                            label = {
                                Text(
                                    attachment.fileName,
                                    style = MailTypeScale.meta,
                                    maxLines = 1,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "移除附件",
                                    modifier = Modifier.height(16.dp),
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }

            TextField(
                value = uiState.body,
                onValueChange = viewModel::onBodyChanged,
                placeholder = {
                    Text(
                        "正文",
                        style = MailTypeScale.composerBody,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                textStyle = MailTypeScale.composerBody.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MailTypeScale.meta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        colors = transparentFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Ink & Paper：输入框去容器化，纸面即背景。 */
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)