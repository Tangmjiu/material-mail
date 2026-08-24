package com.materialmail.appshell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.materialmail.core.sync.work.SyncScheduler

/**
 * 公开 Intent API（Tasker / Termux / 自动化工具接入点，见 docs/INTENT-API.md）。
 * 无数据载荷、无副作用边界：只触发"立即同步"。
 */
class IntentApiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SYNC_NOW) {
            SyncScheduler.syncNow(context)
        }
    }

    companion object {
        const val ACTION_SYNC_NOW = "com.materialmail.action.SYNC_NOW"
    }
}