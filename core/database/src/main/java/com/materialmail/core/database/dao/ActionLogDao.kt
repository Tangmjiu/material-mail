package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.materialmail.core.database.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(log: ActionLogEntity): Long

    @Query("SELECT * FROM action_logs ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ActionLogEntity>>

    @Query("SELECT COUNT(*) FROM action_logs")
    suspend fun count(): Int
}