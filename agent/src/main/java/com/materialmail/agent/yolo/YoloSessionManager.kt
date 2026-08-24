package com.materialmail.agent.yolo

import com.materialmail.agent.execution.YoloStateProvider
import com.materialmail.core.capability.AgentCapability
import com.materialmail.core.capability.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * YOLO 会话管理（设计 §9）：
 *
 * - enable 只由四步确认流程调用（UI 层保证，本类不自证）；
 * - disable 立即生效：取消整个任务 scope（结构化并发，停得掉），
 *   正在执行的任务不等 Workflow 跑完（需求 §54）；
 * - 崩溃恢复：active 配置持久化保留，但任务全部只活在内存 scope 里，
 *   进程死亡即任务消失，不会自动恢复（需求 §55）；高风险动作
 *   由 Gate 的令牌限时 + 重新确认天然覆盖。
 */
class YoloSessionManager(
    private val store: YoloCapabilityStore,
) : YoloStateProvider {

    /** Agent 任务的父 scope：disable 时整体取消。 */
    var taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private set

    override val active: Boolean
        get() = activeSnapshot

    @Volatile
    private var activeSnapshot: Boolean = false

    /** 进程启动时恢复状态快照（权限配置保留；任务不恢复，scope 全新）。 */
    suspend fun restore() {
        activeSnapshot = store.active.first()
    }

    override fun allowsAutonomous(capability: AgentCapability, risk: RiskLevel): Boolean {
        if (!activeSnapshot) return false
        return capabilitiesSnapshot.allows(capability, risk)
    }

    @Volatile
    private var capabilitiesSnapshot: YoloCapabilities = YoloCapabilities()

    suspend fun refreshCapabilities() {
        capabilitiesSnapshot = store.currentCapabilities()
    }

    suspend fun enable() {
        refreshCapabilities()
        store.setActive(true)
        activeSnapshot = true
    }

    /** 紧急停止：先落内存标记（新 Action 立即被 Gate 拒绝），再取消任务。 */
    suspend fun disable() {
        activeSnapshot = false
        store.setActive(false)
        taskScope.cancel()
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}