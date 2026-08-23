package com.materialmail.agent.execution

import com.materialmail.core.capability.AgentCapability
import com.materialmail.core.capability.RiskLevel

/**
 * YOLO 状态扩展点。YOLO Mode 完整实现是 MVP 之后的阶段，
 * 但 ConfirmationGate 的检查路径现在就走这个接口 ——
 * 未来 YOLO 实现插入时不需要改 Gate 的一行代码。
 */
interface YoloStateProvider {
    val active: Boolean

    /** 该能力 + 风险级别是否在当前 YOLO 授权集内允许自动执行。 */
    fun allowsAutonomous(capability: AgentCapability, risk: RiskLevel): Boolean
}

/** 默认：YOLO 未启用，一切自动执行请求都不允许。 */
object InactiveYoloProvider : YoloStateProvider {
    override val active: Boolean = false
    override fun allowsAutonomous(capability: AgentCapability, risk: RiskLevel): Boolean = false
}