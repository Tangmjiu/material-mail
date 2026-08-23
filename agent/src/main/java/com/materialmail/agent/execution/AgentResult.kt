package com.materialmail.agent.execution

/** Gate 执行结果。 */
sealed interface AgentResult<out T> {
    data class Success<T>(val value: T) : AgentResult<T>

    /** 能力未授权（AgentPermissionStore 层拦截）。 */
    data object CapabilityNotGranted : AgentResult<Nothing>

    /** 用户在确认卡片上拒绝。 */
    data object UserDenied : AgentResult<Nothing>

    /** 执行块本身抛错。 */
    data class Failed(val error: Throwable) : AgentResult<Nothing>
}