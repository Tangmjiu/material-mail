package com.materialmail.agent.yolo

import com.materialmail.core.capability.AgentCapability
import com.materialmail.core.capability.RiskLevel

/**
 * YOLO 权限模型（需求 §42）：不是 yolo = true，是细粒度能力集。
 * 默认值按需求 §43：读/搜/草稿/标签/归档开，删除/发送/IM/自动化关。
 */
data class YoloCapabilities(
    val readMail: Boolean = true,
    val searchMail: Boolean = true,
    val modifyMail: Boolean = true,
    val archiveMail: Boolean = true,
    val createDraft: Boolean = true,
    val deleteMail: Boolean = false,
    val sendMail: Boolean = false,
    val executeAutomation: Boolean = false,
    val useConnectors: Boolean = false,
    val sendImMessage: Boolean = false,
) {
    /** 能力 + 风险 → 是否允许 YOLO 自动执行。永久删除不经过这里（Gate 硬编码拦截）。 */
    fun allows(capability: AgentCapability, risk: RiskLevel): Boolean {
        if (risk == RiskLevel.READ_ONLY) return true
        return when (capability) {
            AgentCapability.READ, AgentCapability.SUMMARIZE -> readMail
            AgentCapability.SEARCH -> searchMail
            AgentCapability.LABEL -> modifyMail
            AgentCapability.ARCHIVE -> archiveMail
            AgentCapability.DRAFT -> createDraft
            AgentCapability.DELETE -> deleteMail
            AgentCapability.SEND -> sendMail
            AgentCapability.AUTOMATION -> executeAutomation
            AgentCapability.CONNECTOR_USE -> useConnectors && sendImMessage
        }
    }
}