package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "threads",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index(value = ["accountId", "lastMessageAtEpochMs"])],
)
data class ThreadEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val subject: String,
    /** Participant 列表的 JSON。 */
    val participantsJson: String,
    val messageCount: Int,
    val lastMessageAtEpochMs: Long,
    val isRead: Boolean,
    /** LabelId 的 CSV。 */
    val labelIdsCsv: String,
)