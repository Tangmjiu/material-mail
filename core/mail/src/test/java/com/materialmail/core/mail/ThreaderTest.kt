package com.materialmail.core.mail

import com.materialmail.core.mail.threading.Threader
import com.materialmail.core.mail.threading.ThreadingInput
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreaderTest {

    private fun msg(
        id: String,
        subject: String,
        inReplyTo: String? = null,
        references: List<String> = emptyList(),
        epochSecond: Long = 0,
    ) = ThreadingInput(
        messageIdHeader = id,
        inReplyTo = inReplyTo,
        references = references,
        subject = subject,
        fromAddress = "a@b.com",
        sentAt = Instant.ofEpochSecond(epochSecond),
    )

    @Test
    fun `空列表返回空`() {
        assertTrue(Threader.thread(emptyList()).isEmpty())
    }

    @Test
    fun `单消息自成一线程`() {
        val roots = Threader.thread(listOf(msg("1", "Hello", epochSecond = 1)))
        assertEquals(1, roots.size)
        assertEquals("1", roots[0].message?.messageIdHeader)
        assertTrue(roots[0].children.isEmpty())
    }

    @Test
    fun `References 链建树`() {
        val roots = Threader.thread(
            listOf(
                msg("1", "Project", epochSecond = 1),
                msg("2", "Re: Project", references = listOf("1"), epochSecond = 2),
                msg("3", "Re: Project", references = listOf("1", "2"), epochSecond = 3),
            ),
        )
        assertEquals(1, roots.size)
        assertEquals("1", roots[0].message?.messageIdHeader)
        assertEquals("2", roots[0].children[0].message?.messageIdHeader)
        assertEquals("3", roots[0].children[0].children[0].message?.messageIdHeader)
    }

    @Test
    fun `循环引用被打断`() {
        val roots = Threader.thread(
            listOf(
                msg("1", "A", references = listOf("2"), epochSecond = 1),
                msg("2", "Re: A", references = listOf("1"), epochSecond = 2),
            ),
        )
        // 不能死循环，且最终是一棵树
        assertEquals(1, roots.size)
    }

    @Test
    fun `引用缺失的消息归并到同主题线程`() {
        val roots = Threader.thread(
            listOf(
                msg("1", "周报", epochSecond = 1),
                msg("2", "Re: 周报", epochSecond = 2), // 没有任何引用头
            ),
        )
        assertEquals(1, roots.size)
        assertEquals(2, countMessages(roots[0]))
    }

    @Test
    fun `幽灵容器不晋升为根`() {
        val roots = Threader.thread(
            listOf(
                msg("2", "Re: X", references = listOf("missing-1"), epochSecond = 2),
            ),
        )
        assertEquals(1, roots.size)
        assertEquals("2", roots[0].message?.messageIdHeader)
    }

    @Test
    fun `中文回复前缀参与主题规范化`() {
        assertEquals("项目进展", Threader.normalizeSubject("Re: 答复: 项目进展"))
        assertEquals("项目进展", Threader.normalizeSubject("回复:转发: 项目进展"))
    }

    @Test
    fun `顶级线程按最新消息时间降序`() {
        val roots = Threader.thread(
            listOf(
                msg("a1", "Old", epochSecond = 1),
                msg("b1", "New", epochSecond = 100),
                msg("a2", "Re: Old", references = listOf("a1"), epochSecond = 50),
            ),
        )
        assertEquals("b1", roots[0].message?.messageIdHeader)
        assertEquals("a1", roots[1].message?.messageIdHeader)
    }

    private fun countMessages(node: com.materialmail.core.mail.threading.ThreadNode): Int =
        (if (node.message != null) 1 else 0) + node.children.sumOf(::countMessages)
}