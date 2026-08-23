package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Upsert
    suspend fun upsert(label: LabelEntity)

    @Delete
    suspend fun delete(label: LabelEntity)

    @Query("SELECT * FROM labels WHERE accountId = :accountId ORDER BY name ASC")
    fun observeByAccount(accountId: String): Flow<List<LabelEntity>>
}