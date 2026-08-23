package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String?,
    val protocol: String,
    val syncState: String,
    val createdAtEpochMs: Long,
)