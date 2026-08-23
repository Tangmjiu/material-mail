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

    /** 同步用：文件夹内当前最大 UID，增量拉取起点。 */
    @Query("SELECT MAX(remoteUid) FROM messages WHERE folderId = :folderId")
    suspend fun getMaxRemoteUid(folderId: String): Long?

    /** threading 重建用：账户下全部消息（跨文件夹）。 */
    @Query(
        "SELECT m.* FROM messages m INNER JOIN folders f ON m.folderId = f.id " +
            "WHERE f.accountId = :accountId",
    )
    suspend fun getByAccount(accountId: String): List<MessageEntity>

    @Query("UPDATE messages SET threadId = :threadId WHERE id = :id")
    suspend fun updateThreadId(id: String, threadId: String)

    /** UIDVALIDITY 变化时整文件夹重建。 */
    @Query("DELETE FROM messages WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: String)

    /** 正文懒加载完成后回写。 */
    @Query(
        "UPDATE messages SET snippet = :snippet, plainTextPath = :plainTextPath, " +
            "htmlPath = :htmlPath WHERE id = :id",
    )
    suspend fun updateBody(id: String, snippet: String, plainTextPath: String?, htmlPath: String?)
}