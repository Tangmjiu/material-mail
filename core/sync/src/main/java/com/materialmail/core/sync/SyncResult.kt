package com.materialmail.core.sync

/** 新到达邮件的摘要（通知用）。 */
data class NewMailInfo(
    val threadId: String,
    val senderName: String,
    val subject: String,
)

/** 单次账户同步的结果。 */
sealed interface SyncResult {
    data class Success(
        val newMessageCount: Int,
        /** 本次同步新到达的 INBOX 邮件（通知展示用，仅元数据）。 */
        val newInboxMails: List<NewMailInfo> = emptyList(),
    ) : SyncResult

    /** 凭据缺失 / 失效：跳过，不算错误（用户重新登录前同步无意义）。 */
    data object NoCredentials : SyncResult

    data class Failure(val reason: String) : SyncResult
}