package com.materialmail.appshell.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import com.materialmail.appshell.AppContainerProvider
import com.materialmail.core.database.toModel
import kotlinx.coroutines.flow.first

/**
 * 收件箱 Widget（需求 §6）：展示最近 5 个会话。
 * 数据来自本地数据库（Local-first，Widget 不触网），随同步刷新。
 */
class InboxWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as? AppContainerProvider)?.container
        val threads = runCatching {
            val account = container?.database?.accountDao()?.observeAll()?.first()
                ?.firstOrNull() ?: return@runCatching emptyList()
            container.database.threadDao().observeInbox(account.id).first()
                .take(5)
                .map { it.toModel() }
        }.getOrDefault(emptyList())

        provideContent {
            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        "收件箱",
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(bottom = 8.dp),
                    )
                    if (threads.isEmpty()) {
                        Text("没有新邮件")
                    } else {
                        LazyColumn {
                            items(threads) { thread ->
                                Column(modifier = GlanceModifier.padding(bottom = 8.dp)) {
                                    Text(
                                        thread.participants.firstOrNull()?.displayName
                                            ?: "未知发件人",
                                        style = TextStyle(
                                            fontWeight = if (thread.isRead) {
                                                FontWeight.Normal
                                            } else {
                                                FontWeight.Bold
                                            },
                                        ),
                                        maxLines = 1,
                                    )
                                    Text(thread.subject.ifBlank { "（无主题）" }, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class InboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InboxWidget()
}