package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY remoteName ASC")
    fun observeByAccount(accountId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND role = :role LIMIT 1")
    suspend fun findByRole(accountId: String, role: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: String): List<FolderEntity>

    @Query("UPDATE folders SET unreadCount = :unreadCount WHERE id = :id")
    suspend fun updateUnreadCount(id: String, unreadCount: Int)

    @Query("DELETE FROM folders WHERE accountId = :accountId AND id NOT IN (:keepIds)")
    suspend fun deleteNotIn(accountId: String, keepIds: List<String>)
}