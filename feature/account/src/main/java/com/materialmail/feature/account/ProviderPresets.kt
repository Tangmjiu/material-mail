package com.materialmail.feature.account

import com.materialmail.core.model.Encryption
import com.materialmail.core.model.ServerEndpoint

/** 常见邮箱服务商预设。OAuth 引导是后续阶段任务（需要各平台注册 client_id）。 */
data class ProviderPreset(
    val displayName: String,
    val imap: ServerEndpoint,
    val smtp: ServerEndpoint,
    /** 展示在表单上方的提示（授权码说明 / OAuth 说明）。 */
    val hint: String,
)

object ProviderPresets {

    fun findByEmail(email: String): ProviderPreset? {
        val domain = email.substringAfter('@', "").lowercase()
        return PRESETS[domain]
    }

    private val PRESETS: Map<String, ProviderPreset> = listOf(
        ProviderPreset(
            displayName = "Gmail",
            imap = ServerEndpoint("imap.gmail.com", 993, Encryption.SSL_TLS),
            smtp = ServerEndpoint("smtp.gmail.com", 465, Encryption.SSL_TLS),
            hint = "Gmail 已逐步收紧密码登录。若密码方式失败，请使用应用专用密码；" +
                "官方 OAuth 登录将在后续版本提供。",
        ),
        ProviderPreset(
            displayName = "QQ 邮箱",
            imap = ServerEndpoint("imap.qq.com", 993, Encryption.SSL_TLS),
            smtp = ServerEndpoint("smtp.qq.com", 465, Encryption.SSL_TLS),
            hint = "QQ 邮箱不使用登录密码：请先在网页版 设置 → 账户 中开启 IMAP 并获取授权码，" +
                "然后在下方密码框填写授权码。",
        ),
        ProviderPreset(
            displayName = "163 邮箱",
            imap = ServerEndpoint("imap.163.com", 993, Encryption.SSL_TLS),
            smtp = ServerEndpoint("smtp.163.com", 465, Encryption.SSL_TLS),
            hint = "163 邮箱不使用登录密码：请先在网页版开启 IMAP/SMTP 并获取授权码，" +
                "然后在下方密码框填写授权码。",
        ),
        ProviderPreset(
            displayName = "Outlook",
            imap = ServerEndpoint("outlook.office365.com", 993, Encryption.SSL_TLS),
            smtp = ServerEndpoint("smtp.office365.com", 587, Encryption.STARTTLS),
            hint = "Outlook 推荐官方 OAuth 登录（后续版本提供）。密码方式可能需要应用专用密码。",
        ),
    ).flatMap { preset ->
        when (preset.displayName) {
            "Gmail" -> listOf("gmail.com", "googlemail.com")
            "QQ 邮箱" -> listOf("qq.com", "foxmail.com")
            "163 邮箱" -> listOf("163.com", "126.com", "yeah.net")
            "Outlook" -> listOf("outlook.com", "hotmail.com", "live.com")
            else -> emptyList()
        }.map { it to preset }
    }.toMap()
}