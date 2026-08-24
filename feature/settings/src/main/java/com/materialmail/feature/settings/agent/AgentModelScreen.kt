@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.materialmail.feature.settings.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.agent.model.ModelClient
import com.materialmail.agent.model.ModelConfig
import com.materialmail.agent.model.ModelConfigStore
import com.materialmail.agent.model.ModelProviders
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgentModelUiState(
    val config: ModelConfig = ModelConfig(),
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val savedTick: Int = 0,
)

class AgentModelViewModel(
    private val store: ModelConfigStore,
    private val client: ModelClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentModelUiState())
    val uiState: StateFlow<AgentModelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = store.config.first()
            _uiState.update {
                it.copy(config = config, hasStoredKey = store.loadApiKey() != null)
            }
        }
    }

    fun selectProvider(providerId: String) {
        val preset = ModelProviders.byId(providerId)
        _uiState.update {
            it.copy(
                config = it.config.copy(
                    providerId = providerId,
                    // 切提供商时带出默认端点与模型，用户可再改
                    baseUrl = preset.baseUrl.ifBlank { it.config.baseUrl },
                    model = preset.defaultModel.ifBlank { it.config.model },
                ),
                testResult = null,
            )
        }
    }

    fun onBaseUrlChanged(v: String) =
        _uiState.update { it.copy(config = it.config.copy(baseUrl = v), testResult = null) }

    fun onModelChanged(v: String) =
        _uiState.update { it.copy(config = it.config.copy(model = v), testResult = null) }

    fun onApiKeyChanged(v: String) = _uiState.update { it.copy(apiKey = v, testResult = null) }

    fun save() = viewModelScope.launch {
        val s = _uiState.value
        store.save(s.config)
        if (s.apiKey.isNotBlank()) store.saveApiKey(s.apiKey.trim())
        _uiState.update {
            it.copy(
                apiKey = "",
                hasStoredKey = s.apiKey.isNotBlank() || it.hasStoredKey,
                savedTick = it.savedTick + 1,
            )
        }
    }

    fun testConnection() = viewModelScope.launch {
        val s = _uiState.value
        val key = s.apiKey.ifBlank { store.loadApiKey() ?: "" }
        if (key.isBlank()) {
            _uiState.update { it.copy(testResult = "请先填写并保存 API Key") }
            return@launch
        }
        _uiState.update { it.copy(testing = true, testResult = null) }
        // 测试前持久化当前表单，保证测的就是用户看到的配置
        store.save(s.config)
        if (s.apiKey.isNotBlank()) store.saveApiKey(s.apiKey.trim())
        val result = client.testConnection(s.config, key)
        _uiState.update {
            it.copy(
                testing = false,
                hasStoredKey = true,
                apiKey = "",
                testResult = result.fold(
                    onSuccess = { reply ->
                        "连接成功（${reply.elapsedMs}ms）· 模型回显：" +
                            (reply.text ?: "（空）").take(60)
                    },
                    onFailure = { e -> "连接失败：" + (e.message ?: e.javaClass.simpleName) },
                ),
            )
        }
    }

    companion object {
        fun factory(store: ModelConfigStore, client: ModelClient): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AgentModelViewModel(store, client) }
            }
    }
}

/** AI 模型配置页：提供商 → 端点 → Key → 模型 → 测试连接。 */
@Composable
fun AgentModelScreen(
    viewModel: AgentModelViewModel,
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
                title = { Text("AI 模型", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(MailTheme.spacing.lg),
        ) {
            // 隐私先行：模型是第三方服务，内容出设备
            Text(
                "Agent 对话会把相关内容发送给你选择的模型服务商。" +
                    "邮件正文默认不出设备，只有你主动提问时才会发送必要上下文。",
                style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MailTheme.spacing.lg))

            Text("服务商", style = MailTypeScale.meta, color = MaterialTheme.colorScheme.primary)
            ModelProviders.ALL.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = uiState.config.providerId == preset.id,
                            onClick = { viewModel.selectProvider(preset.id) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = MailTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.config.providerId == preset.id,
                        onClick = null,
                    )
                    Column(modifier = Modifier.padding(start = MailTheme.spacing.sm)) {
                        Text(preset.label, style = MailTypeScale.subject)
                        Text(
                            preset.hint,
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MailTheme.spacing.lg))
            OutlinedTextField(
                value = uiState.config.baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text("Base URL") },
                supportingText = { Text("OpenAI 兼容端点，不含 /chat/completions") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MailTheme.spacing.sm))
            OutlinedTextField(
                value = uiState.config.model,
                onValueChange = viewModel::onModelChanged,
                label = { Text("模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MailTheme.spacing.sm))
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::onApiKeyChanged,
                label = {
                    Text(if (uiState.hasStoredKey) "API Key（已保存，留空则不修改）" else "API Key")
                },
                supportingText = { Text("Keystore 加密存储，与邮箱密码同等级") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MailTheme.spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
                Button(onClick = viewModel::save) { Text("保存") }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !uiState.testing,
                ) { Text("测试连接") }
            }
            if (uiState.testing) {
                Spacer(Modifier.height(MailTheme.spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        "  正在请求模型…",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uiState.testResult?.let {
                Spacer(Modifier.height(MailTheme.spacing.md))
                HorizontalDivider()
                Spacer(Modifier.height(MailTheme.spacing.md))
                Text(
                    it,
                    style = MailTypeScale.preview,
                    color = if (it.startsWith("连接成功")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
