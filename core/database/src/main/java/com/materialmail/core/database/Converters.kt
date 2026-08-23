package com.materialmail.core.database

import androidx.room.TypeConverter
import com.materialmail.core.model.MessageFlag
import com.materialmail.core.model.Participant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room 类型转换。复杂字段统一走 JSON / CSV，避免嵌套表膨胀。同步层也复用此编码。 */
object Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val participantListSerializer = ListSerializer(Participant.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun participantsToJson(value: List<Participant>): String =
        json.encodeToString(participantListSerializer, value)

    @TypeConverter
    fun participantsFromJson(value: String): List<Participant> =
        if (value.isEmpty()) emptyList() else json.decodeFromString(participantListSerializer, value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun stringListFromJson(value: String): List<String> =
        if (value.isEmpty()) emptyList() else json.decodeFromString(stringListSerializer, value)

    @TypeConverter
    fun flagsToCsv(value: Set<MessageFlag>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun flagsFromCsv(value: String): Set<MessageFlag> =
        if (value.isBlank()) {
            emptySet()
        } else {
            value.split(',').mapNotNull { runCatching { MessageFlag.valueOf(it) }.getOrNull() }.toSet()
        }

    @TypeConverter
    fun labelIdsToCsv(value: Set<String>): String = value.joinToString(",")

    @TypeConverter
    fun labelIdsFromCsv(value: String): Set<String> =
        if (value.isBlank()) emptySet() else value.split(',').toSet()
}