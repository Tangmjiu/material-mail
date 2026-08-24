package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query

data class SenderCount(val fromAddress: String, val cnt: Int)
data class FolderCount(val displayName: String, val cnt: Int)

/** 聚合统计查询（通用 Core 能力；Pro 的统计页是它的第一个消费者）。 */
@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int

    @Query("SELECT COUNT(*) FROM threads")
    suspend fun countThreads(): Int

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun countAttachments(): Int

    @Query(
        "SELECT fromAddress, COUNT(*) AS cnt FROM messages " +
            "GROUP BY fromAddress ORDER BY cnt DESC LIMIT :limit",
    )
    suspend fun topSenders(limit: Int): List<SenderCount>

    @Query(
        "SELECT f.displayName, COUNT(*) AS cnt FROM messages m " +
            "JOIN folders f ON m.folderId = f.id GROUP BY m.folderId ORDER BY cnt DESC",
    )
    suspend fun countByFolder(): List<FolderCount>
}