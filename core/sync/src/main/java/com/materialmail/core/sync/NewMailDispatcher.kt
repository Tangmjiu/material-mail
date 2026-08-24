package com.materialmail.core.sync

import com.materialmail.core.capability.NewMailListener
import com.materialmail.core.capability.NewMailSummary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 新邮件事件分发点。Community 无人注册 = 零开销空转；
 * Pro 壳启动时注册 automation 引擎。手工 DI 的挂点（与 SyncEngineLocator 同策略）。
 */
object NewMailDispatcher {
    private val listeners = CopyOnWriteArrayList<NewMailListener>()

    fun register(listener: NewMailListener) {
        listeners.addIfAbsent(listener)
    }

    fun unregister(listener: NewMailListener) {
        listeners.remove(listener)
    }

    internal suspend fun dispatch(mails: List<NewMailSummary>) {
        if (mails.isEmpty()) return
        for (listener in listeners) {
            runCatching { listener.onNewMail(mails) }
            // 监听器异常不影响同步主流程，也不传染其他监听器
        }
    }
}