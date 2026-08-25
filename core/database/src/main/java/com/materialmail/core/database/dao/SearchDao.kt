package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.materialmail.core.database.entity.MessageEntity

@Dao
interface SearchDao {

    /**
     * FTS 搜索。query 必须是已转义的 FTS5 语法串（由 core:search 构造），
     * 本层不接受原始用户输入。
     */
    @Query(
        "SELECT m.* FROM messages m " +
            "JOIN messages_fts ON m.rowid = messages_fts.docid " +
            "JOIN folders f ON m.folderId = f.id " +
            "WHERE messages_fts MATCH :ftsQuery " +
            "AND (:accountId IS NULL OR f.accountId = :accountId) " +
            "ORDER BY m.sentAtEpochMs DESC LIMIT :limit",
    )
    suspend fun search(ftsQuery: String, accountId: String?, limit: Int): List<MessageEntity>

    /** LIKE 兜底（FTS 失败/无结果时的退路，直接在 messages 上模糊匹配）。 */
    @Query(
        "SELECT m.* FROM messages m " +
            "JOIN folders f ON m.folderId = f.id " +
            "WHERE (m.subject LIKE :pattern OR m.snippet LIKE :pattern " +
            "OR m.fromAddress LIKE :pattern) " +
            "AND (:accountId IS NULL OR f.accountId = :accountId) " +
            "ORDER BY m.sentAtEpochMs DESC LIMIT :limit",
    )
    suspend fun searchLike(pattern: String, accountId: String?, limit: Int): List<MessageEntity>
}