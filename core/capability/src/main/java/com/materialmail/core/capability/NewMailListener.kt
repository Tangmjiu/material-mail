package com.materialmail.core.capability

/** 新邮件摘要（同步层产出，监听器消费）。 */
data class NewMailSummary(
    val messageId: String,
    val threadId: String,
    val accountId: String,
    val fromAddress: String,
    val fromName: String?,
    val subject: String,
    val snippet: String,
    val hasAttachments: Boolean,
    val folderRole: String,
)

/**
 * 新邮件事件监听器（Capability Registry 模式的又一个实例）：
 * Core 的同步引擎把事件发给注册的监听器列表；Community 默认空列表，
 * Pro 的 automation 模块注册自己。Core 永远不知道监听器来自哪里。
 */
fun interface NewMailListener {
    suspend fun onNewMail(mails: List<NewMailSummary>)
}