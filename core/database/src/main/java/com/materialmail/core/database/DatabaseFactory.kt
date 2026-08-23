package com.materialmail.core.database

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportFactory

/**
 * 数据库工厂。本地加密为**用户可选**（安全模型 §11：默认关闭，
 * 开启有性能代价，由用户在设置中选择）：
 *
 * - [passphrase] = null → 普通 Room
 * - [passphrase] != null → SQLCipher。口令应来自 Keystore 包裹的密钥，
 *   本方法复制后立即清空入参数组，调用方不要再使用它。
 */
object DatabaseFactory {

    fun create(context: Context, passphrase: CharArray? = null): MaterialMailDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            MaterialMailDatabase::class.java,
            MaterialMailDatabase.NAME,
        )
        if (passphrase != null) {
            System.loadLibrary("sqlcipher")
            val passphraseBytes = Charsets.UTF_8.encode(
                java.nio.CharBuffer.wrap(passphrase),
            ).let { buffer ->
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            }
            passphrase.fill(' ')
            builder.openHelperFactory(SupportFactory(passphraseBytes))
        }
        // 预发布期策略：schema 变更直接重建（v1→v2 无线上用户）。
        // 首次公开发布前必须替换为正式 Migration 链。
        builder.fallbackToDestructiveMigration(true)
        return builder.build()
    }
}