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
}