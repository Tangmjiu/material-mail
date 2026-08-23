package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 全文搜索索引（FTS4）。contentEntity = MessageEntity：
 * Room 自动生成同步触发器，messages 表增删改时索引自动跟随。
 *
 * 索引字段刻意保持轻量：主题 + 摘要 + 发件人地址。
 * 全文正文搜索是 Pro 候选（正文文件在磁盘，需额外索引管道）。
 */
@Entity(tableName = "messages_fts")
@Fts4(contentEntity = MessageEntity::class)
data class MessageFtsEntity(
    val subject: String,
    val snippet: String,
    val fromAddress: String,
)