package com.materialmail.core.crypto

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.credentialDataStore by preferencesDataStore(name = "credentials")

/** 解密后的凭据。类型字段为 OAuth 预留（后续阶段写入 token 而非密码）。 */
data class StoredCredential(
    val type: Type,
    val secret: String,
) {
    enum class Type { PASSWORD, OAUTH2_TOKEN }
}

/**
 * 凭据存储：Keystore 加密 → DataStore 持久化。
 * 账户删除时必须调用 [remove]；卸载 App 即全部消失（allowBackup=false）。
 */
class CredentialStore(private val context: Context) {

    private fun key(accountId: String) = stringPreferencesKey("cred_$accountId")

    suspend fun savePassword(accountId: String, password: String) {
        save(accountId, StoredCredential(StoredCredential.Type.PASSWORD, password))
    }

    suspend fun save(accountId: String, credential: StoredCredential) {
        val encoded = KeystoreCipher.encrypt(credential.type.name + "\n" + credential.secret)
        context.credentialDataStore.edit { it[key(accountId)] = encoded }
    }

    suspend fun load(accountId: String): StoredCredential? {
        val encoded = context.credentialDataStore.data.first()[key(accountId)] ?: return null
        val plain = runCatching { KeystoreCipher.decrypt(encoded) }.getOrNull() ?: return null
        val separator = plain.indexOf('\n')
        if (separator <= 0) return null
        val type = runCatching {
            StoredCredential.Type.valueOf(plain.substring(0, separator))
        }.getOrNull() ?: return null
        return StoredCredential(type, plain.substring(separator + 1))
    }

    suspend fun remove(accountId: String) {
        context.credentialDataStore.edit { it.remove(key(accountId)) }
    }
}