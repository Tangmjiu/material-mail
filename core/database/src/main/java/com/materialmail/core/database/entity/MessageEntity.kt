package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息元信息表。**正文不入此表**（BodyRef 分离存储，
 * 只记录 snippet 与正文文件路径），保证列表查询轻量。
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("threadId"),
        Index("folderId"),
        Index(value = ["folderId", "remoteUid"], unique = true),
        // threading 时按 RFC Message-ID / References 反查
        Index("messageIdHeader"),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val folderId: String,
    val remoteUid: Long?,
    val messageIdHeader: String,
    val inReplyTo: String?,
    /** References 头列表的 JSON。 */
    val referencesJson: String,
    val fromJson: String,
    val toJson: String,
    val ccJson: String,
    val bccJson: String,
    val subject: String,
    val sentAtEpochMs: Long,
    val snippet: String,
    val plainTextPath: String?,
    val htmlPath: String?,
    val hasAttachments: Boolean,
    /** MessageFlag 名的 CSV。 */
    val flagsCsv: String,
)