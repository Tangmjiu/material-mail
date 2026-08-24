package com.materialmail.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.sync.SyncSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** null = 仅手动同步。 */
    val syncIntervalMinutes: Long? = 15,
)

class SettingsViewModel(
    private val syncSettings: SyncSettingsStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = syncSettings.intervalMinutes
        .map { SettingsUiState(syncIntervalMinutes = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSyncInterval(minutes: Long?) {
        viewModelScope.launch { syncSettings.setIntervalMinutes(minutes) }
    }

    companion object {
        fun factory(syncSettings: SyncSettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsViewModel(syncSettings) } }
    }
}