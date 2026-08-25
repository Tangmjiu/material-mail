package com.materialmail.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** buildFtsQuery：中文前缀匹配的回归防线（FTS4 引号+星号语法错误曾致全空）。 */
class FtsQueryTest {

    @Test
    fun `cjk token gets bare prefix star`() {
        assertEquals("发票*", FtsSearchProvider.buildFtsQuery("发票"))
    }

    @Test
    fun `multi word tokens AND with prefix`() {
        assertEquals("周报* AND 项目*", FtsSearchProvider.buildFtsQuery("周报 项目"))
    }

    @Test
    fun `email token is quoted phrase without star`() {
        assertEquals("\"boss@qq.com\"", FtsSearchProvider.buildFtsQuery("boss@qq.com"))
    }

    @Test
    fun `quote injection neutralized`() {
        assertEquals("a* AND \"b.com\"", FtsSearchProvider.buildFtsQuery("a\"b.com"))
    }

    @Test
    fun `blank input yields null`() {
        assertNull(FtsSearchProvider.buildFtsQuery("   "))
    }
}
