package com.materialmail.core.capability

import com.materialmail.core.model.MessageId
import com.materialmail.core.model.ThreadId

data class SearchHit(
    val messageId: MessageId,
    val threadId: ThreadId,
    val snippet: String,
)

/**
 * 搜索能力契约。Community 注册本地 FTS 实现，Pro 可追加高级搜索实现。
 * Core 查询当前注册的 provider 列表并合并结果，不关心实现归属。
 */
interface SearchProvider {
    val id: String

    /**
     * @param accountId null 表示跨账户搜索。
     */
    suspend fun search(accountId: String?, query: String): List<SearchHit>
}