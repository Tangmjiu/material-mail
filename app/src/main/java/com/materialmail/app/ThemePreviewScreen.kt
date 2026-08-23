package com.materialmail.app

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.materialmail.designsystem.component.MailListItem
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale

@Composable
fun ThemePreviewScreen() {
    var demoUnread by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(MailTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MailTheme.spacing.lg),
    ) {
        Text("Material Mail · Ink & Paper", style = MaterialTheme.typography.headlineMedium)

        Section("Color Roles") {
            ColorRow("primary", MaterialTheme.colorScheme.primary)
            ColorRow("surfaceContainer", MaterialTheme.colorScheme.surfaceContainer)
            ColorRow("surfaceContainerHigh", MaterialTheme.colorScheme.surfaceContainerHigh)
            ColorRow("outlineVariant", MaterialTheme.colorScheme.outlineVariant)
            ColorRow("error", MaterialTheme.colorScheme.error)
        }

        Section("Type Scale（字重即状态）") {
            Text("未读发件人 · Bold 17", style = MailTypeScale.senderUnread)
            Text("已读发件人 · Regular 17", style = MailTypeScale.senderRead)
            Text("邮件主题 · 16", style = MailTypeScale.subject)
            Text("摘要预览 · 14 secondary", style = MailTypeScale.preview,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:30 · META LABEL", style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Unread Spine（点击切换状态，观察形变动画）") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { demoUnread = !demoUnread },
            ) {
                MailListItem(
                    sender = "Li Wei",
                    subject = "Re: Q3 产品路线图评审",
                    preview = "下周三下午的评审会我这边没问题，材料我提前发你……",
                    time = "12:30",
                    unread = demoUnread,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = MailTheme.spacing.lg + 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                MailListItem(
                    sender = "GitHub",
                    subject = "[material-mail] CI passed: phase-0",
                    preview = "All checks have passed — 13 modules built successfully",
                    time = "11:05",
                    unread = false,
                )
            }
            Text(
                if (demoUnread) "当前：未读（spine 可见 + 字重 Bold）" else "当前：已读（spine 收缩 + 字重回落）",
                style = MailTypeScale.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MailTheme.spacing.sm)) {
        Text(
            title.uppercase(),
            style = MailTypeScale.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ColorRow(name: String, color: Color) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(color, MaterialTheme.shapes.small))
        Spacer(Modifier.width(MailTheme.spacing.md))
        Text(name, style = MailTypeScale.preview, color = MaterialTheme.colorScheme.onSurface)
    }
}
