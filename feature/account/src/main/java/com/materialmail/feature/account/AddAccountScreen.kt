package com.materialmail.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.core.model.Encryption
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/** 添加账户。单手优先：主操作在底部，表单区块用色阶容器而非卡片堆叠。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("添加账户", style = MaterialTheme.typography.titleLarge) },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
                    uiState.error?.let {
                        Text(
                            it,
                            style = MailTypeScale.preview,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(MailTheme.spacing.sm))
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !uiState.saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        if (uiState.saving) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        } else {
                            Text("保存并同步", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(MailTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MailTheme.spacing.lg),
        ) {
            uiState.presetHint?.let { hint ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(MailTheme.spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            hint,
                            style = MailTypeScale.preview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text("邮箱地址") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("密码 / 授权码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "服务器设置",
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::onHostChanged,
                label = { Text("IMAP 服务器") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::onPortChanged,
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Encryption.entries.forEachIndexed { index, encryption ->
                    SegmentedButton(
                        selected = uiState.encryption == encryption,
                        onClick = { viewModel.onEncryptionChanged(encryption) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Encryption.entries.size,
                        ),
                    ) {
                        Text(
                            when (encryption) {
                                Encryption.SSL_TLS -> "SSL/TLS"
                                Encryption.STARTTLS -> "STARTTLS"
                                Encryption.NONE -> "明文"
                            },
                        )
                    }
                }
            }

            // 安全模型 §11：明文连接必须显式警告 + 用户主动确认，绝不默认放过
            if (uiState.encryption == Encryption.NONE) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(MailTheme.spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "明文连接不对传输内容加密，你的密码和邮件可能被同一网络中的其他人读取。" +
                                    "仅在你完全了解风险的内网环境中使用。",
                                style = MailTypeScale.preview,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.cleartextConfirmed,
                                onCheckedChange = viewModel::onCleartextConfirmedChanged,
                            )
                            Text(
                                "我已了解明文连接的风险",
                                style = MailTypeScale.preview,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
