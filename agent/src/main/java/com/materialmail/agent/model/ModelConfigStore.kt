package com.materialmail.agent.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialmail.core.crypto.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modelStore by preferencesDataStore(name = "agent_model")

/**
 * 模型配置存储：baseUrl/model/provider 走 DataStore；
 * API Key 走 [CredentialStore]（Android Keystore 加密），与邮箱密码同等级保护。
 */
class ModelConfigStore(
    private val context: Context,
    private val credentialStore: CredentialStore,
) {
    private val providerKey = stringPreferencesKey("provider")
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val modelKey = stringPreferencesKey("model")

    val config: Flow<ModelConfig> = context.modelStore.data.map { prefs ->
        val providerId = prefs[providerKey] ?: ModelProviders.DEEPSEEK.id
        val preset = ModelProviders.byId(providerId)
        ModelConfig(
            providerId = providerId,
            baseUrl = prefs[baseUrlKey] ?: preset.baseUrl,
            model = prefs[modelKey] ?: preset.defaultModel,
        )
    }

    /** 是否可用（配置 + Key 都在）。 */
    val ready: Flow<Boolean> = config.map { cfg ->
        cfg.baseUrl.isNotBlank() && cfg.model.isNotBlank() && loadApiKey() != null
    }

    suspend fun save(config: ModelConfig) {
        context.modelStore.edit {
            it[providerKey] = config.providerId
            it[baseUrlKey] = config.baseUrl
            it[modelKey] = config.model
        }
    }

    suspend fun saveApiKey(apiKey: String) =
        credentialStore.savePassword(KEY_ACCOUNT, apiKey)

    suspend fun loadApiKey(): String? =
        credentialStore.load(KEY_ACCOUNT)?.secret

    suspend fun currentConfig(): ModelConfig = config.first()

    companion object {
        private const val KEY_ACCOUNT = "agent_model_api_key"
    }
}
