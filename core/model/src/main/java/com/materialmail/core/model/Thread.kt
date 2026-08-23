package com.materialmail.core.model

import java.time.Instant

/**
 * 邮件会话（Threading 结果，见 core:mail 的 JWZ Threader）。
 *
 * 列表页只读这个模型，不触碰正文。
 */
data class Thread(
    val id: ThreadId,
    val accountId: AccountId,
    val subject: String,
    val participants: List<Participant>,
    val messageCount: Int,
    val lastMessageAt: Instant,
    val isRead: Boolean,
    val labels: Set<LabelId>,
)