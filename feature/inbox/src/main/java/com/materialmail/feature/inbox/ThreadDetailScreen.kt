package com.materialmail.feature.inbox

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.ReplyAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * 会话详情。与列表项构成容器变换对（sharedElement key 相同）。
 * 正文区最大宽度 640dp 居中：平板/折叠屏不拉伸，留白即排版。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ThreadDetailScreen(
    viewModel: ThreadDetailViewModel,
    threadId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onReply: (messageId: String) -> Unit,
    onReplyAll: (messageId: String) -> Unit,
    onForward: (messageId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    with(sharedTransitionScope) {
        Scaffold(
            modifier = modifier.sharedElement(
                rememberSharedContentState(key = "thread-container-$threadId"),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    title = {
                        Text(
                            uiState.subject.ifBlank { "（无主题）" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        val latestMessageId = uiState.messages.lastOrNull()?.messageId
                        if (latestMessageId != null) {
                            IconButton(onClick = { onReply(latestMessageId) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Reply,
                                    contentDescription = "回复",
                                )
                            }
                            IconButton(onClick = { onReplyAll(latestMessageId) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ReplyAll,
                                    contentDescription = "回复全部",
                                )
                            }
                            IconButton(onClick = { onForward(latestMessageId) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Forward,
                                    contentDescription = "转发",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = MailTheme.spacing.contentMaxWidth)
                        .fillMaxWidth(),
                ) {
                    items(items = uiState.messages, key = { it.messageId }) { message ->
                        MessageBlock(message)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBlock(message: DetailMessageUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MailTheme.spacing.lg,
                vertical = MailTheme.spacing.md,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    message.fromName,
                    style = MailTypeScale.senderUnread,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (message.fromAddress.isNotBlank() && message.fromAddress != message.fromName) {
                    Text(
                        message.fromAddress,
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                message.timeText,
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(MailTheme.spacing.md))
        when {
            message.bodyText == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm),
            ) {
                LoadingIndicator(modifier = Modifier.height(20.dp).widthIn(max = 20.dp))
                Text(
                    "正文加载中…",
                    style = MailTypeScale.preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> SelectionContainer {
                // 排版规范：正文 bodyLarge、行高 1.6
                Text(
                    message.bodyText,
                    style = MailTypeScale.composerBody,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}