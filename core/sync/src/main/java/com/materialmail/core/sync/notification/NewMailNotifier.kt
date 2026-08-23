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

/**
 * 新邮件通知。MVP 形态：聚合通知（"N 封新邮件"），点击打开 App。
 * 逐条通知 + 归档/删除 Action 需要 SyncResult 携带新邮件上下文，
 * 是下一轮的独立任务（设计文档 MVP P1：通知 + Action）。
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

    fun notifyNewMail(count: Int) {
        if (count <= 0) return
        // Android 13+ 运行时权限：未授权则静默跳过（用户在设置里自行开启）
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val contentIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_email)
            .setContentTitle("你有 $count 封新邮件")
            .setContentText("点击查看收件箱")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "new_mail"
        const val NOTIFICATION_ID = 1001
    }
}