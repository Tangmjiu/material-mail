package com.materialmail.core.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialmail.core.sync.work.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncSettingsStore by preferencesDataStore(name = "sync_settings")

/** 同步设置。间隔变更立即重排 WorkManager 周期任务。 */
class SyncSettingsStore(private val context: Context) {

    private val intervalKey = longPreferencesKey("interval_minutes")

    /** null = 仅手动同步。 */
    val intervalMinutes: Flow<Long?> =
        context.syncSettingsStore.data.map { it[intervalKey] ?: SyncScheduler.MIN_INTERVAL_MINUTES }

    suspend fun setIntervalMinutes(minutes: Long?) {
        context.syncSettingsStore.edit {
            if (minutes == null) it.remove(intervalKey) else it[intervalKey] = minutes
        }
        if (minutes == null) {
            SyncScheduler.cancelPeriodic(context)
        } else {
            SyncScheduler.reschedule(context, minutes)
        }
    }
}