package com.materialmail.core.mail.smtp

import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.mail.imap.ServerConfig
import com.materialmail.core.model.Encryption
import com.sun.mail.smtp.SMTPTransport
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayInputStream
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 Jakarta Mail 的 [SmtpClient] 实现。
 *
 * Bcc 处理：[MimeMessageBuilder] 会把 Bcc 写进报文头（用于 IMAP 存档），
 * 发送时这里重建一份**剥离 Bcc 头**的副本投递，存档用原始报文 ——
 * 既不让收件人看到 Bcc，又让 Sent 文件夹里的记录完整。
 */
class JakartaSmtpClient : SmtpClient {

    override suspend fun send(
        config: ServerConfig,
        credentials: AuthCredentials,
        rawMessage: ByteArray,
        recipients: List<String>,
    ): SendOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val useSsl = config.encryption == Encryption.SSL_TLS
            val protocol = if (useSsl) "smtps" else "smtp"
            val props = Properties().apply {
                put("mail.$protocol.connectiontimeout", "15000")
                put("mail.$protocol.timeout", "30000")
                put("mail.$protocol.writetimeout", "15000")
                put("mail.$protocol.auth", "true")
                if (config.encryption == Encryption.STARTTLS) {
                    put("mail.$protocol.starttls.enable", "true")
                    put("mail.$protocol.starttls.required", "true")
                }
                if (credentials is AuthCredentials.OAuth2) {
                    put("mail.$protocol.sasl.enable", "true")
                    put("mail.$protocol.sasl.mechanisms", "XOAUTH2")
                    put("mail.$protocol.auth.login.disable", "true")
                    put("mail.$protocol.auth.plain.disable", "true")
                }
            }
            val session = Session.getInstance(props)

            // 发送副本：剥离 Bcc 头后直接走 sendMessage（不经 Transport.send，
            // 不会触发 saveChanges 重写 Message-ID），报文字节与存档版保持一致
            val wireMessage = MimeMessage(session, ByteArrayInputStream(rawMessage))
            wireMessage.removeHeader("Bcc")

            val secret = when (credentials) {
                is AuthCredentials.Password -> credentials.password
                is AuthCredentials.OAuth2 -> credentials.accessToken
            }
            val transport = session.getTransport(protocol) as SMTPTransport
            try {
                transport.connect(
                    config.host,
                    config.port,
                    credentials.username,
                    secret,
                )
                transport.sendMessage(
                    wireMessage,
                    recipients.map { InternetAddress(it) }.toTypedArray(),
                )
            } finally {
                transport.close()
            }
            SendOutcome.Sent
        }.getOrElse { e ->
            SendOutcome.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}