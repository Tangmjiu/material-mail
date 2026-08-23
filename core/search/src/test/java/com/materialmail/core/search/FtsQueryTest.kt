package com.materialmail.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {


    @Test
    fun `单词加前缀匹配`() {
        assertEquals("\"invoice\"*", FtsSearchProvider.buildFtsQuery("invoice"))
    }

    @Test
    fun `多词 AND 组合`() {
        assertEquals("\"project\"* AND \"2026\"*", FtsSearchProvider.buildFtsQuery("project 2026"))
    }

    @Test
    fun `FTS 语法字符被中和`() {
        // 引号/冒号/括号/星号不得泄漏进查询语法
        assertEquals("\"hello\"*", FtsSearchProvider.buildFtsQuery("\"hello:*"))
    }

    @Test
    fun `空白与标点分词`() {
        assertEquals("\"a\"* AND \"b\"*", FtsSearchProvider.buildFtsQuery("  a, b；"))
    }

    @Test
    fun `纯符号输入返回 null`() {
        assertNull(FtsSearchProvider.buildFtsQuery("***"))
        assertNull(FtsSearchProvider.buildFtsQuery("   "))
    }

    @Test
    fun `中文与邮箱地址`() {
        assertEquals("\"周报\"*", FtsSearchProvider.buildFtsQuery("周报"))
        assertEquals("\"a@b.com\"*", FtsSearchProvider.buildFtsQuery("a@b.com"))
    }
}