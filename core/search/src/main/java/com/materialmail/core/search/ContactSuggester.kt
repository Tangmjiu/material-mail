package com.materialmail.core.search

import com.materialmail.core.database.Converters
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.model.Participant

/** 收件人联想：只从本地已同步邮件提取，零网络、零权限（不读系统通讯录）。 */
class ContactSuggester(private val database: MaterialMailDatabase) {

    suspend fun suggest(prefix: String, limit: Int = 8): List<Participant> {
        val trimmed = prefix.trim()
        if (trimmed.length < 2) return emptyList()
        return database.messageDao().suggestContacts(trimmed, limit).map { row ->
            Converters.participantsFromJson(row.fromJson).firstOrNull()
                ?: Participant(address = row.fromAddress)
        }
    }
}