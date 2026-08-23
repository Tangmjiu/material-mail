package com.materialmail.core.mail.smtp

import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.mail.imap.ServerConfig

/** SMTP 发送结果。 */
sealed interface SendOutcome {
    data object Sent : SendOutcome
    data class Failure(val reason: String) : SendOutcome
}

/**
 * SMTP 客户端契约。与 [com.materialmail.core.mail.imap.ImapClient] 平级，
 * 实现细节（Jakarta Mail）收敛在模块内部。
 */
interface SmtpClient {
    /**
     * 发送已构造好的原始 MIME 报文。
     * 收件人列表从报文头解析（含 Bcc —— Bcc 不出现在线上报文中，
     * 由 [MimeMessageBuilder] 生成报文时保留在头内、发送前剥离的工作
     * 由实现负责）。
     */
    suspend fun send(
        config: ServerConfig,
        credentials: AuthCredentials,
        rawMessage: ByteArray,
        recipients: List<String>,
    ): SendOutcome
}