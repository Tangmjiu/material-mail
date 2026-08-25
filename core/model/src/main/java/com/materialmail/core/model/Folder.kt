package com.materialmail.core.model

enum class FolderRole {
    INBOX,
    SENT,
    DRAFTS,
    TRASH,
    ARCHIVE,
    JUNK,
    CUSTOM,
}

/**
 * 远端邮箱文件夹。
 *
 * [remoteName] 是服务器上的原始名称（如 "INBOX"、"[Gmail]/Sent"、
 * 国内邮箱的中文文件夹名），解码后的展示名是 [displayName]。
 *
 * [uidValidity] 是 IMAP UIDVALIDITY：服务器若变更它，本文件夹内
 * 所有本地 UID 缓存立即失效，必须整文件夹重建（同步层负责）。
 */
data class Folder(
    val id: FolderId,
    val accountId: AccountId,
    val remoteName: String,
    val displayName: String,
    val role: FolderRole,
    val unreadCount: Int,
    val uidValidity: Long,
)