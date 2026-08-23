package com.materialmail.core.model

import java.time.Instant

/** 草稿。自动保存只更新 [updatedAt]，不产生历史版本。 */
data class Draft(
    val id: DraftId,
    val accountId: AccountId,
    val to: List<Participant>,
    val cc: List<Participant>,
    val bcc: List<Participant>,
    val subject: String,
    val body: String,
    val bodyFormat: BodyFormat,
    /** 回复 / 转发时指向原邮件，用于发送后建立引用链。 */
    val inReplyToMessageId: MessageId?,
    val updatedAt: Instant,
)