package com.materialmail.agent.yolo

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
 * YOLO 常驻状态提示（需求 §50：明显但不扰人，不用红色警报 UI）
 * + 紧急停止入口（需求 §49：STOP AGENT 必须在通知栏可达）。
 */
class YoloStatusNotifier(private val context: Context) {

    fun show() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID, "YOLO 模式状态", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "YOLO Mode 开启期间的常驻状态提示" }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)

        val stopIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_BASE + ACTION_STOP_YOLO).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ YOLO Mode Active")
            .setContentText("Agent 正在按你授权的权限集自主执行")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "STOP AGENT", stopIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun hide() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "yolo_status"
        const val NOTIFICATION_ID = 1002
        const val ACTION_BASE = "com.materialmail.action."
        const val ACTION_STOP_YOLO = "STOP_YOLO"
    }
}