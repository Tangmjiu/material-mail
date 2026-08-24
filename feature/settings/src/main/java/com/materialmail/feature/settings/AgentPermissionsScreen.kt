package com.materialmail.feature.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.agent.permissions.AgentPermissionStore
import com.materialmail.core.capability.AgentCapability
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CapabilityUi(
    val capability: AgentCapability,
    val label: String,
    val description: String,
    val highRisk: Boolean,
    val granted: Boolean,
)

class AgentPermissionsViewModel(
    private val store: AgentPermissionStore,
) : ViewModel() {

    private val _capabilities = MutableStateFlow<List<CapabilityUi>>(emptyList())
    val capabilities: StateFlow<List<CapabilityUi>> = _capabilities.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _capabilities.value = AgentCapability.entries.map { cap ->
            val meta = META.getValue(cap)
            CapabilityUi(
                capability = cap,
                label = meta.label,
                description = meta.description,
                highRisk = meta.highRisk,
                granted = store.isGranted(cap),
            )
        }
    }

    fun setGranted(capability: AgentCapability, granted: Boolean) {
        viewModelScope.launch {
            store.setGranted(capability, granted)
            refresh()
        }
    }

    private data class Meta(val label: String, val description: String, val highRisk: Boolean)

    companion object {
        private val META = mapOf(
            AgentCapability.READ to Meta("读取邮件", "读取指定邮件的内容", false),
            AgentCapability.SEARCH to Meta("搜索邮件", "在本地索引中搜索", false),
            AgentCapability.SUMMARIZE to Meta("总结邮件", "生成邮件摘要（本地规则，不上云）", false),
            AgentCapability.LABEL to Meta("修改标签", "为邮件添加/移除标签", false),
            AgentCapability.ARCHIVE to Meta("归档邮件", "将邮件移出收件箱", false),
            AgentCapability.DRAFT to Meta("创建草稿", "生成不会自动发送的草稿", false),
            AgentCapability.SEND to Meta("发送邮件", "代表你发送邮件（永远需要确认）", true),
            AgentCapability.DELETE to Meta("删除邮件", "将邮件移入垃圾箱；永久删除永远需要确认", true),
            AgentCapability.CONNECTOR_USE to Meta("使用连接器", "调用飞书/钉钉/企业微信等 Connector", true),
            AgentCapability.AUTOMATION to Meta("执行自动化", "运行 Trigger-Condition-Action 工作流", true),
        )

        fun factory(store: AgentPermissionStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { AgentPermissionsViewModel(store) }
        }
    }
}

/** Agent 能力授权页（需求 §12/§44：逐项授权，高风险明确标注）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPermissionsScreen(
    viewModel: AgentPermissionsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("Agent 权限", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Text(
                    "Agent 只能使用你在这里授权的能力。高风险操作（发送/删除/连接器）" +
                        "即使授权，执行时仍会弹出确认卡片。",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MailTheme.spacing.lg),
                )
            }
            items(items = capabilities, key = { it.capability.name }) { cap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MailTheme.spacing.lg, vertical = MailTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                cap.label,
                                style = MailTypeScale.subject,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (cap.highRisk) {
                                Spacer(Modifier.padding(start = MailTheme.spacing.sm))
                                Text(
                                    "高风险",
                                    style = MailTypeScale.meta,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            cap.description,
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = cap.granted,
                        onCheckedChange = { viewModel.setGranted(cap.capability, it) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
    }
}