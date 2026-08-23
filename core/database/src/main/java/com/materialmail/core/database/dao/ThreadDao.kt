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

    @Query("SELECT * FROM threads WHERE id = :id")
    fun observeById(id: String): Flow<ThreadEntity?>

    @Query("UPDATE threads SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun deleteById(id: String)

    /** threading 重建后清理已消失的线程。 */
    @Query("DELETE FROM threads WHERE accountId = :accountId AND id NOT IN (:keepIds)")
    suspend fun deleteNotIn(accountId: String, keepIds: List<String>)
}