package com.materialmail.agent.execution

import com.materialmail.agent.audit.ActionLogSink
import com.materialmail.agent.audit.ActionResult
import com.materialmail.agent.audit.AuthorizationType
import com.materialmail.agent.permissions.CapabilityGrantChecker
import com.materialmail.core.capability.AgentAction
import com.materialmail.core.capability.ConfirmationToken
import com.materialmail.core.capability.RiskLevel

/** 确认卡片请求者：由 UI 层实现（展示收件人/主题/摘要/影响数量）。 */
fun interface ConfirmationRequester {
    suspend fun request(action: AgentAction): Boolean
}

/**
 * 确认门（设计 §6 铁律 1 的机器化执行）。
 *
 * Agent 不直接碰数据库和邮箱引擎 —— 所有操作声明为 [AgentAction] 走这里：
 *
 * ```
 * 能力未授权          → 拒绝 + 记日志
 * READ_ONLY          → 直接执行（能力授权内）+ 记日志
 * MODIFY/SEND/DELETE → YOLO 授权集内 → 自动执行 + 记 YOLO 日志
 *                    → 否则弹确认卡片 → 确认才执行 + 记日志
 * permanent = true   → 永远弹确认（YOLO 也不可绕过，需求 §45 硬编码）
 * ```
 *
 * 不可绕过性论证（由 ConfirmationGateTest 证明）：
 * - 执行块 [block] 只在确认/授权路径全部通过后才被调用；
 * - 确认令牌一次性、绑定指纹、限时，见 [ConfirmationTokenIssuer]；
 * - 拒绝/失败同样写审计日志。
 */
class ConfirmationGate(
    private val permissionStore: CapabilityGrantChecker,
    private val tokenIssuer: ConfirmationTokenIssuer,
    private val audit: ActionLogSink,
    private val yolo: YoloStateProvider = InactiveYoloProvider,
) {

    /** 执行一个操作。UI 通过 [requestConfirmation] 提供确认卡片。 */
    suspend fun <T> execute(
        agentName: String,
        action: AgentAction,
        requestConfirmation: ConfirmationRequester,
        block: suspend () -> T,
    ): AgentResult<T> {
        // 第一层：能力授权
        if (!permissionStore.isGranted(action.capability)) {
            audit.log(agentName, action, AuthorizationType.DENIED, ActionResult.DENIED,
                error = "capability not granted")
            return AgentResult.CapabilityNotGranted
        }

        // 第二层：风险分级
        val needsConfirmation = when {
            action.permanent -> true // 永久删除：硬编码确认，YOLO 不可绕过
            action.risk == RiskLevel.READ_ONLY -> false
            yolo.active && yolo.allowsAutonomous(action.capability, action.risk) -> false
            else -> true
        }

        if (needsConfirmation && !requestConfirmation.request(action)) {
            audit.log(agentName, action, AuthorizationType.DENIED, ActionResult.DENIED,
                error = "user denied")
            return AgentResult.UserDenied
        }

        val authorization = when {
            action.risk == RiskLevel.READ_ONLY -> AuthorizationType.CAPABILITY_GRANT
            needsConfirmation -> AuthorizationType.USER_CONFIRMED
            else -> AuthorizationType.YOLO
        }

        return try {
            val value = block()
            audit.log(agentName, action, authorization, ActionResult.SUCCESS)
            AgentResult.Success(value)
        } catch (e: Exception) {
            audit.log(agentName, action, authorization, ActionResult.FAILED,
                error = e.message ?: e.javaClass.simpleName)
            AgentResult.Failed(e)
        }
    }

    /**
     * Connector 发送的两阶段协议（设计 §7）：
     * UI 确认后调用本方法签发令牌，再把令牌交给 Connector.executeSend；
     * Connector 调用方（agent/execution 的封装）在执行前用
     * [consumeToken] 校验 —— Connector 实现者拿不到签发能力，无法伪造。
     */
    suspend fun confirmAndIssueToken(
        agentName: String,
        action: AgentAction,
        fingerprint: String,
        requestConfirmation: ConfirmationRequester,
    ): ConfirmationToken? {
        if (!permissionStore.isGranted(action.capability)) {
            audit.log(agentName, action, AuthorizationType.DENIED, ActionResult.DENIED,
                error = "capability not granted")
            return null
        }
        val autonomous = !action.permanent && yolo.active &&
            yolo.allowsAutonomous(action.capability, action.risk)
        if (!autonomous && !requestConfirmation.request(action)) {
            audit.log(agentName, action, AuthorizationType.DENIED, ActionResult.DENIED,
                error = "user denied")
            return null
        }
        return tokenIssuer.issue(fingerprint)
    }

    /** 校验并消费令牌（一次性）。失败必须同时记审计。 */
    suspend fun consumeToken(
        agentName: String,
        action: AgentAction,
        token: ConfirmationToken,
        fingerprint: String,
        connectorId: String,
    ): Boolean {
        val ok = tokenIssuer.validateAndConsume(token, fingerprint)
        if (!ok) {
            audit.log(agentName, action, AuthorizationType.DENIED, ActionResult.DENIED,
                connectorUsed = connectorId, error = "invalid/expired confirmation token")
        }
        return ok
    }
}