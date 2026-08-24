@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.materialmail.feature.settings.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.agent.model.ChatMessage
import com.materialmail.agent.model.EMPTY_PARAMS
import com.materialmail.agent.model.ModelClient
import com.materialmail.agent.model.ModelConfigStore
import com.materialmail.agent.model.ToolDef
import com.materialmail.agent.model.stringParamSchema
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 对话气泡。TOOL 行是 Agent 调用本地只读工具的可解释痕迹。 */
data class ChatItem(
    val role: Role,
    val text: String,
) { enum class Role { USER, ASSISTANT, TOOL, ERROR } }

data class AgentChatUiState(
    val ready: Boolean = false,
    val checked: Boolean = false,
    val items: List<ChatItem> = emptyList(),
    val sending: Boolean = false,
)

/**
 * Agent 对话（只读工具 MVP）。
 *
 * 工具白名单刻意只读（统计 / FTS 搜索）：写操作必须走 ConfirmationGate，
 * 那是另一条链路（需求 §23 的不可绕过约束），不在聊天里开后门。
 */
class AgentChatViewModel(
    private val store: ModelConfigStore,
    private val client: ModelClient,
    private val database: MaterialMailDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private val history = mutableListOf<ChatMessage>()

    private val tools = listOf(
        ToolDef(
            name = "get_mailbox_stats",
            description = "获取邮箱本地统计：邮件总数、会话数、附件数",
            parameters = EMPTY_PARAMS,
        ),
        ToolDef(
            name = "search_mail",
            description = "全文搜索本地邮件（主题/发件人/正文），返回最相关的若干封",
            parameters = stringParamSchema("搜索关键词，中英文均可"),
        ),
    )

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(ready = store.ready.first(), checked = true) }
        }
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(items = it.items + ChatItem(ChatItem.Role.USER, input), sending = true) }
        viewModelScope.launch {
            val config = store.currentConfig()
            val apiKey = store.loadApiKey()
            if (apiKey == null) {
                append(ChatItem.Role.ERROR, "未配置 API Key，请先在 设置 → Agent → AI 模型 中配置")
                return@launch
            }
            history += ChatMessage(role = "user", content = input)
            try {
                runAgentLoop(config, apiKey)
            } catch (e: Exception) {
                append(ChatItem.Role.ERROR, "请求失败：" + (e.message ?: e.javaClass.simpleName))
            }
            _uiState.update { it.copy(sending = false) }
        }
    }

    private suspend fun runAgentLoop(config: com.materialmail.agent.model.ModelConfig, apiKey: String) {
        repeat(MAX_TOOL_ROUNDS) { round ->
            val reply = client.chat(
                config = config,
                apiKey = apiKey,
                messages = listOf(ChatMessage(role = "system", content = SYSTEM_PROMPT)) + history,
                tools = tools,
            )
            if (reply.toolCalls.isEmpty()) {
                val text = reply.text ?: "（模型没有返回内容）"
                history += ChatMessage(role = "assistant", content = text)
                append(ChatItem.Role.ASSISTANT, text)
                return
            }
            // 有工具调用：记录 assistant 回合，本地执行，回填结果
            history += ChatMessage(role = "assistant", content = reply.text, toolCalls = reply.toolCalls)
            for (call in reply.toolCalls) {
                append(ChatItem.Role.TOOL, toolTrace(call.name, call.argumentsJson))
                val result = executeTool(call.name, call.argumentsJson)
                history += ChatMessage(
                    role = "tool",
                    content = result,
                    toolCallId = call.id,
                    name = call.name,
                )
            }
            if (round == MAX_TOOL_ROUNDS - 1) {
                append(ChatItem.Role.ERROR, "已达到最大工具调用轮数，请换个问法")
            }
        }
    }

    private fun toolTrace(name: String, argsJson: String): String = when (name) {
        "search_mail" -> {
            val q = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(argsJson)
                    .jsonObject["query"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            "🔍 搜索邮件：" + (q ?: "")
        }
        "get_mailbox_stats" -> "📊 读取邮箱统计"
        else -> "调用工具：$name"
    }

    private suspend fun executeTool(name: String, argsJson: String): String = when (name) {
        "get_mailbox_stats" -> runCatching {
            val stats = database.statsDao()
            "邮件总数 ${stats.countMessages()}，会话 ${stats.countThreads()}，" +
                "附件 ${stats.countAttachments()}（本地数据）"
        }.getOrElse { "统计读取失败：" + it.message }

        "search_mail" -> runCatching {
            val query = kotlinx.serialization.json.Json.parseToJsonElement(argsJson)
                .jsonObject["query"]?.jsonPrimitive?.contentOrNull ?: return@runCatching "缺少 query 参数"
            val results = database.searchDao().search(query, accountId = null, limit = 8)
            if (results.isEmpty()) return@runCatching "没有找到相关邮件"
            results.joinToString("\n") { m ->
                val time = Instant.ofEpochMilli(m.sentAtEpochMs).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "[$time] ${m.fromAddress}｜${m.subject}｜${m.snippet.take(80)}"
            }
        }.getOrElse { "搜索失败：" + it.message }

        else -> "未知工具：$name"
    }

    private fun append(role: ChatItem.Role, text: String) {
        _uiState.update { it.copy(items = it.items + ChatItem(role, text)) }
    }

    companion object {
        private const val MAX_TOOL_ROUNDS = 4

        private const val SYSTEM_PROMPT =
            "你是 Material Mail 的邮箱助理。用简洁的中文回答。" +
                "需要查邮箱数据时调用提供的只读工具；不要编造邮件内容；" +
                "你不能执行任何写操作（删除/移动/发送），用户要求写操作时说明需要手动完成。"

        fun factory(
            store: ModelConfigStore,
            client: ModelClient,
            database: MaterialMailDatabase,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentChatViewModel(store, client, database) }
        }
    }
}

@Composable
fun AgentChatScreen(
    viewModel: AgentChatViewModel,
    onBack: () -> Unit,
    onConfigureModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.items.size) {
        if (uiState.items.isNotEmpty()) listState.animateScrollToItem(uiState.items.size - 1)
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
                title = { Text("Agent 对话", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            if (uiState.checked && !uiState.ready) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(MailTheme.spacing.lg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("还没有配置 AI 模型", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "配置一个 OpenAI 兼容端点即可开始（DeepSeek / 通义 / Kimi / 智谱 均可）",
                        style = MailTypeScale.preview,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(MailTheme.spacing.lg))
                    Button(onClick = onConfigureModel) { Text("去配置") }
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = MailTheme.spacing.lg,
                    vertical = MailTheme.spacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm),
            ) {
                items(items = uiState.items) { item -> ChatBubble(item) }
                if (uiState.sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                "  思考中…",
                                style = MailTypeScale.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MailTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("问点什么，例如：我这周收到多少邮件？") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                FilledIconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !uiState.sending,
                    modifier = Modifier.padding(start = MailTheme.spacing.sm),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem) {
    when (item.role) {
        ChatItem.Role.USER -> Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = 300.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    item.text,
                    style = MailTypeScale.subject,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(MailTheme.spacing.md),
                )
            }
        }
        ChatItem.Role.ASSISTANT -> Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).widthIn(max = 300.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    item.text,
                    style = MailTypeScale.subject,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(MailTheme.spacing.md),
                )
            }
        }
        ChatItem.Role.TOOL -> Text(
            item.text,
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = MailTheme.spacing.sm),
        )
        ChatItem.Role.ERROR -> Text(
            item.text,
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
