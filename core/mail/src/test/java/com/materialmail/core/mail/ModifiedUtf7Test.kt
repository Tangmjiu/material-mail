package com.materialmail.core.mail

import com.materialmail.core.mail.utf7.ModifiedUtf7
import org.junit.Assert.assertEquals
import org.junit.Test

class ModifiedUtf7Test {

    @Test
    fun `纯 ASCII 原样保留`() {
        assertEquals("INBOX", ModifiedUtf7.encode("INBOX"))
        assertEquals("[Gmail]/Sent Mail", ModifiedUtf7.encode("[Gmail]/Sent Mail"))
    }

    @Test
    fun `& 转义为 &杠`() {
        assertEquals("A&-B", ModifiedUtf7.encode("A&B"))
        assertEquals("A&B", ModifiedUtf7.decode("A&-B"))
    }

    @Test
    fun `中文文件夹名往返一致`() {
        val names = listOf("已发送", "草稿箱", "垃圾邮件", "QQ邮箱收件夹", "已删除")
        for (name in names) {
            assertEquals(name, ModifiedUtf7.decode(ModifiedUtf7.encode(name)))
        }
    }

    @Test
    fun `中英文混合往返一致`() {
        val name = "工作-2026年项目"
        assertEquals(name, ModifiedUtf7.decode(ModifiedUtf7.encode(name)))
    }

    @Test
    fun `解码容错 - 无终止符原样输出`() {
        assertEquals("abc&def", ModifiedUtf7.decode("abc&def"))
    }

    @Test
    fun `与 RFC3501 示例一致`() {
        // RFC 3501: "~peter/mail/台北/日本語" 的 "台" U+53F0
        val encoded = ModifiedUtf7.encode("台北")
        assertEquals("台北", ModifiedUtf7.decode(encoded))
        // RFC 3501 已知向量："&ZeVnLIqe-" 解码为 "日本語"
        assertEquals("日本語", ModifiedUtf7.decode("&ZeVnLIqe-"))
    }
}