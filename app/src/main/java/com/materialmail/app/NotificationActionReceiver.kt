package com.materialmail.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.materialmail.core.model.ThreadId
import com.materialmail.core.sync.notification.NewMailNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知 Action 接收器：归档/删除新邮件。
 * 复用 MessageActionPerformer（本地乐观 + 远端尽力），与列表手势同一条路径。
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra(NewMailNotifier.EXTRA_THREAD_ID) ?: return
        val container = (context.applicationContext as? MaterialMailApp)?.container ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    NewMailNotifier.ACTION_BASE + NewMailNotifier.ACTION_ARCHIVE ->
                        container.actionPerformer.archiveThread(ThreadId(threadId))
                    NewMailNotifier.ACTION_BASE + NewMailNotifier.ACTION_DELETE ->
                        container.actionPerformer.deleteThread(ThreadId(threadId))
                }
            } finally {
                // 处理后收起通知
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.cancel(NewMailNotifier.NOTIFICATION_ID)
                pending.finish()
            }
        }
    }
}