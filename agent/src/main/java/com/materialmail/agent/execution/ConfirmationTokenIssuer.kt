package com.materialmail.agent.execution

import com.materialmail.core.capability.ConfirmationToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 一次性确认令牌签发器（设计 §7：Connector 实现者无法绕过确认，
 * 因为令牌由 agent/execution 签发且一次性）。
 *
 * 安全属性：
 * - 一次性：[validateAndConsume] 无论成败都销毁令牌；
 * - 绑定操作指纹：令牌只对签发时的 actionFingerprint 有效；
 * - 限时：默认 60 秒过期；
 * - 纯内存：进程死亡 = 令牌全部失效（崩溃后不残留授权，YOLO 安全模型一致）。
 */
class ConfirmationTokenIssuer(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private data class Pending(val actionFingerprint: String, val expiresAtMs: Long)

    private val pending = ConcurrentHashMap<String, Pending>()

    /** 用户确认后签发。fingerprint 由调用方对操作内容做稳定散列。 */
    fun issue(actionFingerprint: String, validityMs: Long = DEFAULT_VALIDITY_MS): ConfirmationToken {
        val value = UUID.randomUUID().toString()
        pending[value] = Pending(actionFingerprint, nowMs() + validityMs)
        return ConfirmationToken(value)
    }

    /** 校验并销毁。任何失败路径（过期/指纹不符/不存在）同样销毁，防重放探测。 */
    fun validateAndConsume(token: ConfirmationToken, actionFingerprint: String): Boolean {
        val entry = pending.remove(token.value) ?: return false
        if (nowMs() > entry.expiresAtMs) return false
        return entry.actionFingerprint == actionFingerprint
    }

    companion object {
        const val DEFAULT_VALIDITY_MS = 60_000L

        /** 操作指纹：对"这次要干什么"做稳定摘要，令牌与之绑定。 */
        fun fingerprintOf(vararg parts: String): String =
            parts.joinToString("|").hashCode().toString(16)
    }
}