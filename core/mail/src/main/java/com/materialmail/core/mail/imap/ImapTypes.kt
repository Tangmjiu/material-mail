package com.materialmail.core.mail.imap

import com.materialmail.core.model.FolderRole
import com.materialmail.core.model.MessageFlag
import com.materialmail.core.model.Participant
import java.time.Instant

enum class Encryption {
    /** 993：SSL/TLS 直连，推荐。 */
    SSL_TLS,

    /** 143 + STARTTLS 升级，强制要求升级成功，否则断开。 */
    STARTTLS,

    /**
     * 明文。安全模型 §11：连接前必须向用户显式警告，
     * 因此构造时强制要求 [ServerConfig.allowCleartext] 确认。
     */
    NONE,
}

data class ServerConfig(
    val host: String,
    val port: Int,
    val encryption: Encryption,
    /** encryption = NONE 时必须显式为 true（用户已确认明文风险）。 */
    val allowCleartext: Boolean = false,
) {
    init {
        require(encryption != Encryption.NONE || allowCleartext) {
            "明文 IMAP 连接需要显式确认（allowCleartext = true）"
        }
    }
}

/** 认证凭据。OAuth 优先，密码仅通用 IMAP 兜底。 */
sealed interface AuthCredentials {
    val username: String

    data class Password(
        override val username: String,
        val password: String,
    ) : AuthCredentials

    /** XOAUTH2 / OAUTHBEARER，token 由 OAuth 模块（后续阶段）产出。 */
    data class OAuth2(
        override val username: String,
        val accessToken: String,
    ) : AuthCredentials
}

/** 远端文件夹快照。 */
data class RemoteFolder(
    /** 服务器原始名（modified UTF-7 传输层已解码为可读字符串）。 */
    val remoteName: String,
    val displayName: String,
    val role: FolderRole,
    val messageCount: Int,
    val unreadCount: Int,
    /** UIDVALIDITY：变化意味着本地 UID 缓存全部失效，必须重新同步。 */
    val uidValidity: Long,
)

/** FETCH ENVELOPE 级别的轻量元信息（列表同步用，不拉正文）。 */
data class RemoteEnvelope(
    val uid: Long,
    val messageIdHeader: String?,
    val inReplyTo: String?,
    val references: List<String>,
    val from: List<Participant>,
    val to: List<Participant>,
    val cc: List<Participant>,
    val subject: String,
    val sentAt: Instant?,
    val flags: Set<MessageFlag>,
    val sizeBytes: Long,
)

/** 整封原始 MIME 报文。 */
data class RawMessage(
    val uid: Long,
    val flags: Set<MessageFlag>,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is RawMessage && other.uid == uid && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = 31 * uid.hashCode() + bytes.contentHashCode()
}

/** IDLE 推送的事件。 */
sealed interface FolderEvent {
    /** 新邮件到达（EXISTS 增加）。 */
    data object MessageArrived : FolderEvent

    /** 有邮件被删除（EXPUNGE）。 */
    data object MessageExpunged : FolderEvent

    /** 标记变化（FETCH FLAGS  unsolicited）。 */
    data object FlagsChanged : FolderEvent
}