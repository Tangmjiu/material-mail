package com.materialmail.agent.permissions

import com.materialmail.core.capability.AgentCapability

/** 能力授权查询抽象（ConfirmationGate 只依赖它，Android 存储是实现细节）。 */
fun interface CapabilityGrantChecker {
    suspend fun isGranted(capability: AgentCapability): Boolean
}