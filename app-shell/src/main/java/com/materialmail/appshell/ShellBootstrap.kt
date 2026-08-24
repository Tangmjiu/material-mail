package com.materialmail.appshell

import android.content.Context
import com.materialmail.core.sync.work.SyncEngineLocator
import com.materialmail.core.sync.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 壳启动序列（:app 与 :pro:app 共用）：容器 + 同步调度 + YOLO 状态恢复。 */
object ShellBootstrap {

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context): AppContainer {
        val container = AppContainer(context)
        SyncEngineLocator.instance = container.syncEngine
        SyncScheduler.schedulePeriodic(context)

        // YOLO 崩溃恢复（需求 §55）：权限配置恢复，任务不自动恢复
        bootstrapScope.launch {
            container.yoloSessionManager.restore()
            if (container.yoloSessionManager.active) {
                container.yoloStatusNotifier.show()
            }
        }
        return container
    }
}