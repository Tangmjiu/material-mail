package com.materialmail.core.sync.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.materialmail.core.sync.NewMailInfo

/**
 * 新邮件通知（设计 MVP P1：通知 + 归档/删除 Action）。
 *
 * - 恰好 1 封新邮件：展示发件人/主题 + 「归档」「删除」操作；
 * - 多封：聚合摘要，点击打开 App；
 * - Android 13+ 未授权通知权限时静默跳过。
 */
class NewMailNotifier(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "新邮件",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "新邮件到达时提醒（同步为周期轮询，通知可能有延迟）"
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun notifyNewMail(newMails: List<NewMailInfo>) {
        if (newMails.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_email)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        if (newMails.size == 1) {
            val mail = newMails.first()
            builder
                .setContentTitle(mail.senderName)
                .setContentText(mail.subject)
                .addAction(
                    0, "归档",
                    actionPendingIntent(ACTION_ARCHIVE, mail.threadId, REQUEST_ARCHIVE),
                )
                .addAction(
                    0, "删除",
                    actionPendingIntent(ACTION_DELETE, mail.threadId, REQUEST_DELETE),
                )
        } else {
            builder
                .setContentTitle("你有 " + newMails.size + " 封新邮件")
                .setContentText(
                    newMails.take(3).joinToString("、") { it.senderName } +
                        if (newMails.size > 3) " 等" else "",
                )
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun actionPendingIntent(action: String, threadId: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(ACTION_BASE + action)
                .setPackage(context.packageName)
                .putExtra(EXTRA_THREAD_ID, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "new_mail"
        const val NOTIFICATION_ID = 1001

        /** Receiver 在 app 模块（能拿到容器），action 字符串契约在这里定义。 */
        const val ACTION_BASE = "com.materialmail.action."
        const val ACTION_ARCHIVE = "ARCHIVE_THREAD"
        const val ACTION_DELETE = "DELETE_THREAD"
        const val EXTRA_THREAD_ID = "threadId"
        private const val REQUEST_ARCHIVE = 1
        private const val REQUEST_DELETE = 2
    }
}