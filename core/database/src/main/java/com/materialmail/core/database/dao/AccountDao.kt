package com.materialmail.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.materialmail.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("UPDATE accounts SET syncState = :syncState WHERE id = :id")
    suspend fun updateSyncState(id: String, syncState: String)

    @Query("UPDATE accounts SET signature = :signature WHERE id = :id")
    suspend fun updateSignature(id: String, signature: String?)
}