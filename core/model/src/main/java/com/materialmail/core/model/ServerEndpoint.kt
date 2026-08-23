package com.materialmail.core.model

enum class Encryption {
    /** SSL/TLS 直连，推荐。 */
    SSL_TLS,

    /** 明文连接 + STARTTLS 升级，强制要求升级成功，否则断开。 */
    STARTTLS,

    /** 明文。连接前必须向用户显式警告（安全模型 §11）。 */
    NONE,
}

/** 邮件服务器接入点（IMAP / SMTP 各一份）。 */
data class ServerEndpoint(
    val host: String,
    val port: Int,
    val encryption: Encryption,
)