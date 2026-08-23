package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY sentAtEpochMs ASC")
    fun observeByThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY sentAtEpochMs DESC")
    fun observeByFolder(folderId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    /** threading 反查：按 RFC Message-ID 找本地已存在的消息。 */
    @Query("SELECT * FROM messages WHERE messageIdHeader = :messageIdHeader LIMIT 1")
    suspend fun findByMessageIdHeader(messageIdHeader: String): MessageEntity?

    @Query("SELECT remoteUid FROM messages WHERE folderId = :folderId AND remoteUid IS NOT NULL")
    suspend fun getRemoteUidsInFolder(folderId: String): List<Long>

    @Query("DELETE FROM messages WHERE folderId = :folderId AND remoteUid IN (:remoteUids)")
    suspend fun deleteByRemoteUids(folderId: String, remoteUids: List<Long>)

    @Query("UPDATE messages SET flagsCsv = :flagsCsv WHERE id = :id")
    suspend fun updateFlags(id: String, flagsCsv: String)

    @Query("UPDATE messages SET folderId = :targetFolderId WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<String>, targetFolderId: String)
}