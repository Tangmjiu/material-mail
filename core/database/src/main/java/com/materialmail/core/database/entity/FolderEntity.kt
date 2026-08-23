package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val remoteName: String,
    val displayName: String,
    val role: String,
    val unreadCount: Int,
    val uidValidity: Long,
)