package com.materialmail.core.model

/**
 * 本地 ID 与远端标识分离：本地 ID 由数据库签发（String 主键），
 * IMAP UID / RFC Message-ID 等远端标识存放在各模型各自的 remote 字段中。
 */
@JvmInline
value class AccountId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class FolderId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ThreadId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class MessageId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class AttachmentId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class DraftId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class LabelId(val value: String) {
    override fun toString(): String = value
}