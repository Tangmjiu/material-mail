package com.materialmail.agent.yolo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.yoloStore by preferencesDataStore(name = "yolo")

/** YOLO 能力集 + 启用状态的持久化。 */
class YoloCapabilityStore(private val context: Context) {

    private val activeKey = booleanPreferencesKey("yolo_active")

    val active: Flow<Boolean> = context.yoloStore.data.map { it[activeKey] ?: false }

    val capabilities: Flow<YoloCapabilities> = context.yoloStore.data.map { prefs ->
        YoloCapabilities(
            readMail = prefs[booleanPreferencesKey("yc_readMail")] ?: true,
            searchMail = prefs[booleanPreferencesKey("yc_searchMail")] ?: true,
            modifyMail = prefs[booleanPreferencesKey("yc_modifyMail")] ?: true,
            archiveMail = prefs[booleanPreferencesKey("yc_archiveMail")] ?: true,
            createDraft = prefs[booleanPreferencesKey("yc_createDraft")] ?: true,
            deleteMail = prefs[booleanPreferencesKey("yc_deleteMail")] ?: false,
            sendMail = prefs[booleanPreferencesKey("yc_sendMail")] ?: false,
            executeAutomation = prefs[booleanPreferencesKey("yc_executeAutomation")] ?: false,
            useConnectors = prefs[booleanPreferencesKey("yc_useConnectors")] ?: false,
            sendImMessage = prefs[booleanPreferencesKey("yc_sendImMessage")] ?: false,
        )
    }

    suspend fun setActive(active: Boolean) {
        context.yoloStore.edit { it[activeKey] = active }
    }

    suspend fun setCapabilities(cap: YoloCapabilities) {
        context.yoloStore.edit { prefs ->
            prefs[booleanPreferencesKey("yc_readMail")] = cap.readMail
            prefs[booleanPreferencesKey("yc_searchMail")] = cap.searchMail
            prefs[booleanPreferencesKey("yc_modifyMail")] = cap.modifyMail
            prefs[booleanPreferencesKey("yc_archiveMail")] = cap.archiveMail
            prefs[booleanPreferencesKey("yc_createDraft")] = cap.createDraft
            prefs[booleanPreferencesKey("yc_deleteMail")] = cap.deleteMail
            prefs[booleanPreferencesKey("yc_sendMail")] = cap.sendMail
            prefs[booleanPreferencesKey("yc_executeAutomation")] = cap.executeAutomation
            prefs[booleanPreferencesKey("yc_useConnectors")] = cap.useConnectors
            prefs[booleanPreferencesKey("yc_sendImMessage")] = cap.sendImMessage
        }
    }

    suspend fun currentCapabilities(): YoloCapabilities = capabilities.first()
}