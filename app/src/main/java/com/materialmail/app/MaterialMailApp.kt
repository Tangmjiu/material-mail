package com.materialmail.app

import android.app.Application
import com.materialmail.core.sync.work.SyncEngineLocator
import com.materialmail.core.sync.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaterialMailApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncEngineLocator.instance = container.syncEngine
        SyncScheduler.schedulePeriodic(this)

        // YOLO 崩溃恢复（需求 §55）：权限配置恢复，任务不自动恢复
        // （任务只活在进程内存的 scope 里，进程重启即消失）
        appScope.launch {
            container.yoloSessionManager.restore()
            if (container.yoloSessionManager.active) {
                container.yoloStatusNotifier.show()
            }
        }
    }
}