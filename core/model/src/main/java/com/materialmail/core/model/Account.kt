package com.materialmail.core.model

import java.time.Instant

/** 登录协议。OAuth 优先，密码仅作为通用 IMAP 兜底。 */
enum class Protocol {
    IMAP,
    GMAIL_OAUTH,
    MICROSOFT_OAUTH,
}

enum class SyncState {
    NOT_SYNCED,
    SYNCING,
    SYNCED,
    ERROR,
}

/**
 * 邮箱账户。
 *
 * 注意：本模型**不包含**密码 / OAuth Token —— 凭据走 Android Keystore
 * 加密后存 DataStore（安全模型 §11），数据库里只放元信息。
 */
data class Account(
    val id: AccountId,
    val email: String,
    val displayName: String?,
    val protocol: Protocol,
    val imap: ServerEndpoint,
    val smtp: ServerEndpoint,
    val syncState: SyncState,
    /** 签名：纯文本，写信时自动附加在正文末尾（" -- " 分隔符惯例）。 */
    val signature: String? = null,
    val createdAt: Instant,
)