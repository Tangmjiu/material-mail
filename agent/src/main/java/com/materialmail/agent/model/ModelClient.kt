package com.materialmail.agent.model

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 对话消息。tool 消息用 [toolCallId] + [name] 回填结果。 */
data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
)

data class ToolCall(val id: String, val name: String, val argumentsJson: String)

/** OpenAI 兼容 tools 定义（JSON Schema 子集，手写 JsonObject 即可）。 */
data class ToolDef(val name: String, val description: String, val parameters: JsonObject)

data class ChatReply(
    val text: String?,
    val toolCalls: List<ToolCall>,
    val elapsedMs: Long,
)

/**
 * OpenAI 兼容 Chat Completions 客户端。
 * HttpURLConnection 直连（无 OkHttp 依赖），15s 超时，支持 tools 往返。
 * 所有网络在 [Dispatchers.IO]。
 */
class ModelClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** 连接测试：发一条最小请求，返回耗时与模型回显。 */
    suspend fun testConnection(config: ModelConfig, apiKey: String): Result<ChatReply> =
        runCatching {
            chat(
                config, apiKey,
                messages = listOf(
                    ChatMessage(role = "user", content = "用一句话回答：你已连接。"),
                ),
                tools = emptyList(),
                maxTokens = 32,
            )
        }

    suspend fun chat(
        config: ModelConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        tools: List<ToolDef> = emptyList(),
        maxTokens: Int = 1024,
    ): ChatReply = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        m.content?.let { put("content", it) }
                        m.toolCallId?.let { put("tool_call_id", it) }
                        m.name?.let { put("name", it) }
                        if (m.toolCalls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                m.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", tc.argumentsJson)
                                        }
                                    })
                                }
                            }
                        }
                    })
                }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.parameters)
                            }
                        })
                    }
                }
            }
        }

        val url = URI(config.baseUrl.trimEnd('/') + "/chat/completions").toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }
        val started = System.currentTimeMillis()
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]
                        ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw ModelException(code, apiMsg ?: text.take(200))
            }
            val choice = json.parseToJsonElement(text)
                .jsonObject["choices"]!!.jsonArray.first().jsonObject
            val message = choice["message"]!!.jsonObject
            val toolCalls = (message["tool_calls"] as? JsonArray).orEmpty().map { tc ->
                val obj = tc.jsonObject
                val fn = obj["function"]!!.jsonObject
                ToolCall(
                    id = obj["id"]!!.jsonPrimitive.content,
                    name = fn["name"]!!.jsonPrimitive.content,
                    argumentsJson = fn["arguments"]!!.jsonPrimitive.content,
                )
            }
            ChatReply(
                text = message["content"]?.jsonPrimitive?.contentOrNull,
                toolCalls = toolCalls,
                elapsedMs = System.currentTimeMillis() - started,
            )
        } finally {
            conn.disconnect()
        }
    }

    class ModelException(val httpCode: Int, message: String) :
        Exception("HTTP $httpCode：$message")
}

/** 常用 JSON Schema 片段构造（工具参数用）。 */
fun stringParamSchema(description: String): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("query") {
            put("type", "string")
            put("description", description)
        }
    }
    putJsonArray("required") { add(JsonPrimitive("query")) }
}

val EMPTY_PARAMS: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}
