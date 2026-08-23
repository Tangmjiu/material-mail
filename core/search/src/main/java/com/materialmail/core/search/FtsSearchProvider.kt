package com.materialmail.core.search

import com.materialmail.core.capability.SearchHit
import com.materialmail.core.capability.SearchProvider
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ThreadId

/**
 * Community 本地 FTS 搜索实现（core:capability.SearchProvider 的默认注册）。
 * 纯本地、零网络 —— Local-first。Pro 高级搜索（正则/跨字段组合）另行注册，
 * Core 合并多 provider 结果，不关心归属。
 */
class FtsSearchProvider(
    private val database: MaterialMailDatabase,
) : SearchProvider {

    override val id: String = "local-fts"

    override suspend fun search(accountId: String?, query: String): List<SearchHit> {
        val ftsQuery = buildFtsQuery(query) ?: return emptyList()
        return database.searchDao()
            .search(ftsQuery, accountId, limit = MAX_RESULTS)
            .map {
                SearchHit(
                    messageId = MessageId(it.id),
                    threadId = ThreadId(it.threadId),
                    snippet = it.snippet,
                )
            }
    }

    companion object {
        private const val MAX_RESULTS = 100

        /**
         * 用户输入 → FTS 查询串：
         * - 分词（空白/标点），逐词加 * 前缀匹配；
         * - 双引号包裹 + 内部引号转义，杜绝 FTS 语法注入；
         * - 多词 AND 组合。
         */
        internal fun buildFtsQuery(raw: String): String? {
            val tokens = raw.split(Regex("[^\\p{L}\\p{N}@._-]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" AND ") { token ->
                "\"" + token.replace("\"", "\"\"") + "\"" + "*"
            }
        }
    }
}