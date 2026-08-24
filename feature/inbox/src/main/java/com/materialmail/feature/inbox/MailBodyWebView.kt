package com.materialmail.feature.inbox

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * HTML 邮件正文的隔离渲染器（安全模型 §11 明确允许：
 * 渲染邮件内容 ≠ 用 WebView 做 UI，不违反需求 §63）。
 *
 * 隔离规则：
 * - 禁用 JavaScript；
 * - 禁止文件/内容访问；
 * - 网络加载全部拦截（远程图片不加载 —— 隐私：防止发件人像素追踪；
 *   后续加"显示图片"按钮按需放行）；
 * - 链接不在内跳转（后续接管到系统浏览器）。
 *
 * cid: 内联图片本轮不解析（附件区手动下载），WebView 拦截器留扩展点。
 */
@Composable
fun MailBodyWebView(
    html: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                @Suppress("DEPRECATION")
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = true // 链接不内跳

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val scheme = request.url.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") {
                            // 远程资源一律空响应（防追踪像素）
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, wrapMailHtml(html), "text/html", "utf-8", null)
        },
    )
}

/** 邮件 HTML 的包装：排版跟随 Ink & Paper，暗色跟随系统。 */
internal fun wrapMailHtml(html: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body {
    font-family: sans-serif;
    font-size: 16px;
    line-height: 1.6;
    margin: 0;
    word-wrap: break-word;
  }
  img { max-width: 100%; height: auto; }
  a { color: inherit; text-decoration: underline; }
  blockquote { margin: 0; padding-left: 12px; border-left: 2px solid #8884; }
  @media (prefers-color-scheme: dark) {
    body { color: #E3E2DE; background: transparent; }
  }
  @media (prefers-color-scheme: light) {
    body { color: #1A1C1A; background: transparent; }
  }
</style>
</head>
<body>
""" + html + "\n</body>\n</html>"