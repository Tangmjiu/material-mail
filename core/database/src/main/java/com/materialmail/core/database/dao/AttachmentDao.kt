package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.AttachmentEntity

@Dao
interface AttachmentDao {
    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getByMessage(messageId: String): List<AttachmentEntity>

    @Query("UPDATE attachments SET localUri = :localUri WHERE id = :id")
    suspend fun updateLocalUri(id: String, localUri: String)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteByMessage(messageId: String)
}