package com.materialmail.appshell

/**
 * mailto: / 分享 / Shortcut 的预填写信请求。
 * 由 [MaterialMailNavHost] 消费，跳转到写信页并预填字段。
 */
data class ComposeRequest(
    val to: String? = null,
    val subject: String? = null,
    val body: String? = null,
)