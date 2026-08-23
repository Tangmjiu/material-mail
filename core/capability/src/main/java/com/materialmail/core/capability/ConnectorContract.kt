package com.materialmail.core.capability

import java.time.Instant

enum class ConnectorPlatform {
    FEISHU,
    DINGTALK,
    WECOM,
    SLACK,
    DISCORD,
    MATRIX,
    OTHER,
}

/** 跨平台统一消息抽象，Core 只看抽象不看平台 SDK。 */
data class UnifiedMessage(
    val platform: ConnectorPlatform,
    val remoteId: String,
    val senderName: String,
    val senderId: String?,
    val conversationName: String?,
    val textPreview: String,
    val sentAt: Instant,
)

/** 待发送内容（prepareSend 的输入）。 */
data class OutgoingMessage(
    val platform: ConnectorPlatform,
    val targetConversationId: String,
    val targetDisplayName: String,
    val text: String,
)

/**
 * prepareSend 的产出：待确认内容。UI 展示给用户，
 * 用户确认后由 agent/execution 签发一次性 [ConfirmationToken]。
 */
data class PendingAction(
    val draft: OutgoingMessage,
    val description: String,
)

/**
 * 一次性确认令牌。由 agent/execution 签发并校验，
 * Connector 实现者无法伪造或复用 —— 确认协议不可编程绕过。
 */
@JvmInline
value class ConfirmationToken(val value: String)

sealed interface SendResult {
    data object Success : SendResult
    data class Failure(val reason: String) : SendResult
}

/**
 * IM Connector 契约（Pro 功能，pro/connectors/* 实现）。
 * 发送是两阶段协议：prepareSend → 用户确认 → executeSend(token)。
 */
interface MessageConnector {
    val platform: ConnectorPlatform

    suspend fun search(query: String): List<UnifiedMessage>

    /** 只准备，不发送。 */
    suspend fun prepareSend(draft: OutgoingMessage): PendingAction

    /** 必须携带有效确认令牌才会真正调用平台 API。 */
    suspend fun executeSend(token: ConfirmationToken, action: PendingAction): SendResult
}