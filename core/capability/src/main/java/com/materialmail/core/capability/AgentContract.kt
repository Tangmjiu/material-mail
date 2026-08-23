package com.materialmail.core.capability

/**
 * Agent 能力枚举。Community 提供基础子集（READ/SEARCH/SUMMARIZE/LABEL/
 * ARCHIVE/DRAFT），Pro 注册更大的集合。Core 只查询"注册了哪些能力"，
 * 永远不知道"用户是不是 Pro"。
 */
enum class AgentCapability {
    READ,
    SEARCH,
    SUMMARIZE,
    LABEL,
    ARCHIVE,
    DRAFT,
    SEND,
    DELETE,
    CONNECTOR_USE,
    AUTOMATION,
}

/** 风险分级，决定 ConfirmationGate 的确认策略。 */
enum class RiskLevel {
    /** 读取类：授权 capability 内默认放行。 */
    READ_ONLY,

    /** 修改类：标签 / 归档等，需确认。 */
    MODIFY,

    /** 对外通信类：发送邮件 / IM，需确认且可配置为 Strict。 */
    SEND,

    /** 删除类：需确认；永久删除永不自动执行。 */
    DELETE,
}

/** 一次待执行的 Agent 操作声明。Agent 不直接碰数据库与邮箱引擎，只提交它。 */
data class AgentAction(
    val capability: AgentCapability,
    val risk: RiskLevel,
    /** 面向用户的可读描述，确认卡片上展示。 */
    val description: String,
    /** 受影响的邮件 / 对象数量，确认卡片展示。 */
    val affectedCount: Int,
    /**
     * 是否永久删除。true 时 ConfirmationGate 强制确认，
     * YOLO 也不可自动执行（需求 §45，硬编码非配置项）。
     */
    val permanent: Boolean = false,
)

/** 提供方注册自己拥有的能力集合，由 DI 容器聚合。 */
interface AgentCapabilityProvider {
    val capabilities: Set<AgentCapability>
}