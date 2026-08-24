package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String?,
    val protocol: String,
    val imapHost: String,
    val imapPort: Int,
    val imapEncryption: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpEncryption: String,
    val syncState: String,
    /** 账户签名（纯文本，Composer 自动附加）。 */
    val signature: String? = null,
    val createdAtEpochMs: Long,
)