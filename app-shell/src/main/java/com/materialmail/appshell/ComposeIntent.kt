package com.materialmail.appshell

import android.content.Intent
import android.net.Uri
import android.util.Patterns

/**
 * 从外部 Intent（mailto: / 分享 / Shortcut）解析出预填写信请求。
 *
 * - mailto: URI → 提取 to/subject/body
 * - ACTION_SEND with text/plain → body = extra text, subject = extra subject
 * - 其它 → null
 */
fun parseComposeIntent(intent: Intent): ComposeRequest? {
    val action = intent.action ?: return null
    val data = intent.data

    // mailto: URI
    if (Intent.ACTION_VIEW == action && data != null && "mailto".equals(data.scheme, ignoreCase = true)) {
        val to = data.schemeSpecificPart
        val subject = data.getQueryParameter("subject")
        val body = data.getQueryParameter("body")
        return ComposeRequest(
            to = to?.takeIf { it.isNotBlank() },
            subject = subject,
            body = body,
        )
    }

    // ACTION_SEND (share)
    if (Intent.ACTION_SEND == action) {
        val type = intent.type
        if (type == null || type.startsWith("text/")) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val toUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            return ComposeRequest(
                to = null,
                subject = subject,
                body = text,
            )
        }
    }

    return null
}