package com.materialmail.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    sender: String,
    subject: String,
    preview: String,
    time: String,
    unread: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        UnreadSpine(unread = unread, modifier = Modifier.align(Alignment.CenterVertically))
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
