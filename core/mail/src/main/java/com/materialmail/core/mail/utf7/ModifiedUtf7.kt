package com.materialmail.core.mail.utf7

/**
 * IMAP modified UTF-7（RFC 3501 §5.1.3）编解码。
 *
 * 国内邮箱（QQ / 163 / 阿里）普遍存在中文文件夹名，IMAP 协议层
 * 以 modified UTF-7 传输（如 "已发送" → "&Uh,1,2,3-" 风格），
 * 不处理会显示为乱码。与标准 UTF-7 的差异：
 * - 可打印 ASCII（0x20-0x7E）原样输出，但 '&' 编码为 "&-"
 * - 非 ASCII 段用 UTF-16BE → Base64（'/' 替换为 ','，去掉 '=' 填充），
 *   包在 '&' 与 '-' 之间
 */
object ModifiedUtf7 {

    fun encode(value: String): String {
        val out = StringBuilder(value.length)
        val nonAscii = StringBuilder()

        fun flush() {
            if (nonAscii.isEmpty()) return
            val bytes = nonAscii.toString().toByteArray(Charsets.UTF_16BE)
            out.append('&')
                .append(java.util.Base64.getEncoder().encodeToString(bytes)
                    .replace('/', ',').trimEnd('='))
                .append('-')
            nonAscii.setLength(0)
        }

        for (c in value) {
            if (c.code in 0x20..0x7E) {
                flush()
                if (c == '&') out.append("&-") else out.append(c)
            } else {
                nonAscii.append(c)
            }
        }
        flush()
        return out.toString()
    }

    fun decode(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val end = value.indexOf('-', i + 1)
            if (end == i + 1) { // "&-" 转义
                out.append('&')
                i = end + 1
                continue
            }
            if (end == -1) { // 容错：无终止符，原样输出
                out.append(c)
                i++
                continue
            }
            val b64 = value.substring(i + 1, end).replace(',', '/')
            val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
            runCatching {
                String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_16BE)
            }.onSuccess { decoded ->
                out.append(decoded)
                i = end + 1
            }.onFailure { // 容错：解码失败原样输出
                out.append(value, i, end + 1)
                i = end + 1
            }
        }
        return out.toString()
    }
}