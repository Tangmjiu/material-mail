package com.materialmail.core.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.materialmail.core.sync.SyncEngine
import com.materialmail.core.sync.notification.NewMailNotifier
import com.materialmail.core.sync.SyncResult

/**
 * 手工 DI 的临时挂点：app 模块启动时注入单例。
 * 引入 DI 框架（后续阶段评估）后移除。
 */
object SyncEngineLocator {
    @Volatile
    var instance: SyncEngine? = null
}

/** WorkManager 同步任务（周期 + 手动刷新共用）。 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val engine = SyncEngineLocator.instance ?: return Result.failure()
        return when (val result = engine.syncAll()) {
            is SyncResult.Failure ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            is SyncResult.Success -> {
                NewMailNotifier(applicationContext).notifyNewMail(result.newMessageCount)
                Result.success()
            }
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}