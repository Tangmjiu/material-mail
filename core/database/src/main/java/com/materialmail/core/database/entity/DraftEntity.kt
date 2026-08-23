package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drafts",
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
data class DraftEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val toJson: String,
    val ccJson: String,
    val bccJson: String,
    val subject: String,
    val body: String,
    val bodyFormat: String,
    /** 本地 Message.id，故意不做 FK：原邮件可能被删除，草稿必须存活。 */
    val inReplyToMessageId: String?,
    val updatedAtEpochMs: Long,
)