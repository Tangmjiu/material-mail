package com.materialmail.core.sync

/** 单次账户同步的结果。 */
sealed interface SyncResult {
    data class Success(val newMessageCount: Int) : SyncResult

    /** 凭据缺失 / 失效：跳过，不算错误（用户重新登录前同步无意义）。 */
    data object NoCredentials : SyncResult

    data class Failure(val reason: String) : SyncResult
}