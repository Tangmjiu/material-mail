package com.materialmail.core.sync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 同步调度。Local-first 意味着没有服务器推送（设计文档 §13 风险 3），
 * 周期同步 + 手动刷新是 MVP 的全部手段 —— 产品文案需对用户诚实。
 */
object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "material-mail-periodic-sync"
    private const val ONE_TIME_WORK_NAME = "material-mail-manual-sync"

    /** WorkManager 周期任务下限 15 分钟，低于此值无意义。 */
    const val MIN_INTERVAL_MINUTES = 15L

    fun schedulePeriodic(context: Context, intervalMinutes: Long = MIN_INTERVAL_MINUTES) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES),
            TimeUnit.MINUTES,
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 手动刷新：立即执行一次。重复触发时复用进行中的任务。 */
    fun syncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** 变更周期（用户改设置）：取消重排。 */
    fun reschedule(context: Context, intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES),
            TimeUnit.MINUTES,
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}