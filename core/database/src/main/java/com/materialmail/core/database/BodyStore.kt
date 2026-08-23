package com.materialmail.core.database

import android.content.Context
import com.materialmail.core.model.MessageId
import java.io.File

/**
 * 正文文件存储。正文与列表分离（BodyRef 只记路径）：
 * - 列表查询只读 messages 表，零正文 IO；
 * - 详情页打开时才读文件；
 * - 账户删除时按目录整体清理。
 *
 * 文件布局：filesDir/bodies/<accountId>/<messageId>.txt | .html
 */
class BodyStore(context: Context) {

    private val rootDir = File(context.filesDir, "bodies")

    data class StoredBody(
        val plainTextPath: String?,
        val htmlPath: String?,
    )

    @Synchronized
    fun save(
        accountId: String,
        messageId: MessageId,
        plainText: String?,
        html: String?,
    ): StoredBody {
        val dir = File(rootDir, accountId).apply { mkdirs() }
        fun write(suffix: String, content: String?): String? {
            val file = File(dir, "${messageId.value}$suffix")
            return if (content != null) {
                file.writeText(content, Charsets.UTF_8)
                file.absolutePath
            } else {
                file.delete()
                null
            }
        }
        return StoredBody(
            plainTextPath = write(".txt", plainText),
            htmlPath = write(".html", html),
        )
    }

    fun load(path: String): String? =
        File(path).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun delete(accountId: String, messageId: MessageId) {
        File(rootDir, "$accountId/${messageId.value}.txt").delete()
        File(rootDir, "$accountId/${messageId.value}.html").delete()
    }

    fun deleteAccount(accountId: String) {
        File(rootDir, accountId).deleteRecursively()
    }
}