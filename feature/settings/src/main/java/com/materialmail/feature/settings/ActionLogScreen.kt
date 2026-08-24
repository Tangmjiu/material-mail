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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.agent.audit.ActionLogReader
import com.materialmail.core.database.entity.ActionLogEntity
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Agent 操作审计日志查看页（需求 §48：时间/能力/对象/授权方式/结果）。 */
class ActionLogViewModel(reader: ActionLogReader) : ViewModel() {
    val logs: StateFlow<List<ActionLogEntity>> = reader.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(reader: ActionLogReader): ViewModelProvider.Factory = viewModelFactory {
            initializer { ActionLogViewModel(reader) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionLogScreen(
    viewModel: ActionLogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("Agent 操作记录", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .padding(MailTheme.spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(MailTheme.spacing.xxl))
                Text(
                    "还没有 Agent 操作记录",
                    style = MailTypeScale.subject,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Agent 每次执行（包括被拒绝的）都会记录在这里。",
                    style = MailTypeScale.meta,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(items = logs, key = { it.id }) { log ->
                    LogRow(log)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: ActionLogEntity) {
    val time = Instant.ofEpochMilli(log.timestampEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    Column(
        modifier = Modifier.padding(
            horizontal = MailTheme.spacing.lg,
            vertical = MailTheme.spacing.md,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                log.agentName,
                style = MailTypeScale.senderRead,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                time,
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(MailTheme.spacing.xs))
        Text(
            log.capability + " · " + log.targetDescription + "（" + log.affectedCount + " 项）",
            style = MailTypeScale.preview,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(MailTheme.spacing.xs))
        Row {
            Text(
                authorizationLabel(log.authorization),
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                " · " + resultLabel(log.result),
                style = MailTypeScale.meta,
                color = when (log.result) {
                    "SUCCESS" -> MaterialTheme.colorScheme.primary
                    "FAILED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            log.error?.let {
                Text(
                    " · " + it,
                    style = MailTypeScale.meta,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun authorizationLabel(value: String): String = when (value) {
    "CAPABILITY_GRANT" -> "能力授权"
    "USER_CONFIRMED" -> "用户确认"
    "YOLO" -> "YOLO 模式"
    else -> "已拒绝"
}

private fun resultLabel(value: String): String = when (value) {
    "SUCCESS" -> "成功"
    "FAILED" -> "失败"
    else -> "被拒绝"
}