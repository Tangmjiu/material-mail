package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.ThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Upsert
    suspend fun upsertAll(threads: List<ThreadEntity>)

    @Upsert
    suspend fun upsert(thread: ThreadEntity)

    @Query(
        "SELECT * FROM threads WHERE accountId = :accountId " +
            "ORDER BY lastMessageAtEpochMs DESC",
    )
    fun observeByAccount(accountId: String): Flow<List<ThreadEntity>>

    /**
     * 收件箱视图：只包含有消息位于 INBOX 文件夹的线程。
     * 归档（消息移出 INBOX）后线程自然从列表消失，Undo 移回即恢复。
     */
    @Query(
        "SELECT DISTINCT t.* FROM threads t " +
            "INNER JOIN messages m ON m.threadId = t.id " +
            "INNER JOIN folders f ON m.folderId = f.id " +
            "WHERE t.accountId = :accountId AND f.role = 'INBOX' " +
            "ORDER BY t.lastMessageAtEpochMs DESC",
    )
    fun observeInbox(accountId: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE id = :id")
    fun observeById(id: String): Flow<ThreadEntity?>

    @Query("SELECT * FROM threads WHERE id = :id")
    suspend fun getById(id: String): ThreadEntity?

    @Query("UPDATE threads SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun deleteById(id: String)

    /** threading 重建后清理已消失的线程。 */
    @Query("DELETE FROM threads WHERE accountId = :accountId AND id NOT IN (:keepIds)")
    suspend fun deleteNotIn(accountId: String, keepIds: List<String>)
}