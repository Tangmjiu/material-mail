package com.materialmail.feature.account

import com.materialmail.core.model.Encryption
import com.materialmail.core.model.ServerEndpoint

/**
 * 常见邮箱服务商预设。选择服务商后只需填账号 + 密码/授权码，
 * 服务器参数全部自动带出（高级设置里可改）。
 * OAuth 引导是后续阶段任务（需要各平台注册 client_id）。
 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    /** 选择页副标题：支持的域名。 */
    val domainsHint: String,
    val imap: ServerEndpoint,
    val smtp: ServerEndpoint,
    /** 凭证步展示的服务商提示（授权码说明 / OAuth 说明）。 */
    val hint: String,
)

object ProviderPresets {

    val GMAIL = ProviderPreset(
        id = "gmail",
        displayName = "Gmail",
        domainsHint = "gmail.com · googlemail.com",
        imap = ServerEndpoint("imap.gmail.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.gmail.com", 465, Encryption.SSL_TLS),
        hint = "Gmail 已收紧密码登录：需要开启两步验证后使用应用专用密码；" +
            "官方 OAuth 登录将在后续版本提供。",
    )
    val OUTLOOK = ProviderPreset(
        id = "outlook",
        displayName = "Outlook",
        domainsHint = "outlook.com · hotmail.com · live.com",
        imap = ServerEndpoint("outlook.office365.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.office365.com", 587, Encryption.STARTTLS),
        hint = "Outlook 推荐官方 OAuth 登录（后续版本提供）。密码方式可能需要应用专用密码。",
    )
    val QQ = ProviderPreset(
        id = "qq",
        displayName = "QQ 邮箱",
        domainsHint = "qq.com · foxmail.com",
        imap = ServerEndpoint("imap.qq.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.qq.com", 465, Encryption.SSL_TLS),
        hint = "QQ 邮箱不使用登录密码：请先在网页版 设置 → 账户 中开启 IMAP 并获取授权码，" +
            "然后在下方填写授权码。",
    )
    val NETEASE = ProviderPreset(
        id = "netease",
        displayName = "163 邮箱",
        domainsHint = "163.com · 126.com · yeah.net",
        imap = ServerEndpoint("imap.163.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.163.com", 465, Encryption.SSL_TLS),
        hint = "163 邮箱不使用登录密码：请先在网页版开启 IMAP/SMTP 并获取授权码，" +
            "然后在下方填写授权码。",
    )
    val ICLOUD = ProviderPreset(
        id = "icloud",
        displayName = "iCloud",
        domainsHint = "icloud.com · me.com · mac.com",
        imap = ServerEndpoint("imap.mail.me.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.mail.me.com", 587, Encryption.STARTTLS),
        hint = "iCloud 需要应用专用密码：appleid.apple.com → 登录与安全 → 应用专用密码。",
    )
    val YAHOO = ProviderPreset(
        id = "yahoo",
        displayName = "Yahoo",
        domainsHint = "yahoo.com · ymail.com",
        imap = ServerEndpoint("imap.mail.yahoo.com", 993, Encryption.SSL_TLS),
        smtp = ServerEndpoint("smtp.mail.yahoo.com", 465, Encryption.SSL_TLS),
        hint = "Yahoo 需要在账户安全设置中生成应用密码。",
    )

    /** 选择页展示顺序（中国用户优先）。 */
    val ALL = listOf(QQ, NETEASE, GMAIL, OUTLOOK, ICLOUD, YAHOO)

    private val DOMAIN_INDEX: Map<String, ProviderPreset> = mapOf(
        "gmail.com" to GMAIL, "googlemail.com" to GMAIL,
        "outlook.com" to OUTLOOK, "hotmail.com" to OUTLOOK, "live.com" to OUTLOOK,
        "qq.com" to QQ, "foxmail.com" to QQ,
        "163.com" to NETEASE, "126.com" to NETEASE, "yeah.net" to NETEASE,
        "icloud.com" to ICLOUD, "me.com" to ICLOUD, "mac.com" to ICLOUD,
        "yahoo.com" to YAHOO, "ymail.com" to YAHOO,
    )

    fun findByEmail(email: String): ProviderPreset? {
        val domain = email.substringAfter('@', "").lowercase()
        return DOMAIN_INDEX[domain]
    }

    fun byId(id: String): ProviderPreset? = ALL.firstOrNull { it.id == id }
}
