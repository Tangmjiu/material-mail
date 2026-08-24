package com.materialmail.region.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.regionNoticeStore by preferencesDataStore(name = "region_notices")

/**
 * 地区提示的「不再提示」记忆（需求 §30）：
 * 每个 serviceId 独立记录；设置页可重置；不每次打开 App 都弹。
 */
class RegionNoticeStore(private val context: Context) {

    private fun key(serviceId: String) = booleanPreferencesKey("dismissed_" + serviceId)

    suspend fun shouldShow(serviceId: String): Boolean =
        context.regionNoticeStore.data.first()[key(serviceId)] != true

    suspend fun dismissForever(serviceId: String) {
        context.regionNoticeStore.edit { it[key(serviceId)] = true }
    }

    /** 设置页「重新开启提示」用。 */
    suspend fun resetAll() {
        context.regionNoticeStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith("dismissed_") }
                .forEach { prefs.remove(it) }
        }
    }
}