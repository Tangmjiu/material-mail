package com.materialmail.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

/**
 * Inbox 列表项的排版标准答案：零圆角、无卡片、无阴影，
 * 层级 = spine（状态）+ 字重（读/未读）+ 色阶（主/次信息）。
 */
@Composable
fun MailListItem(
    /** null = 非选择模式；true/false = 多选模式下的选中态（spine 位换勾选圈）。 */
    selection: Boolean? = null,
    sender: String,
    subject: String,
    preview: String,
    time: String,
    unread: Boolean,
    modifier: Modifier = Modifier,
) {
    // 无障碍（设计 §5.8 检查单）：读屏用户看不到 Unread Spine 和字重，
    // 用 stateDescription 显式播报读/未读；合并子节点保证朗读顺序 = 视觉顺序
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = if (unread) "未读" else "已读"
            },
    ) {
        if (selection == null) {
            UnreadSpine(unread = unread, modifier = Modifier.align(Alignment.CenterVertically))
        } else {
            // 多选模式：勾选圈按 Expressive spring 缩放进出
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 16.dp)
                    .width(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selection) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.Circle
                    },
                    contentDescription = if (selection) "已选中" else "未选中",
                    tint = if (selection) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = MailTheme.spacing.lg,
                    end = MailTheme.spacing.lg,
                    top = MailTheme.spacing.listItemVertical,
                    bottom = MailTheme.spacing.listItemVertical,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sender,
                    style = if (unread) MailTypeScale.senderUnread else MailTypeScale.senderRead,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(MailTheme.spacing.sm))
                Text(
                    text = time,
                    style = MailTypeScale.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(MailTheme.spacing.xs))
            Text(
                text = subject,
                style = MailTypeScale.subject,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview,
                style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
