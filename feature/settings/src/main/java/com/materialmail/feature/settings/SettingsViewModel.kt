package com.materialmail.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.crypto.CredentialStore
import com.materialmail.core.database.BodyStore
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.sync.SyncSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountManageUi(
    val accountId: String,
    val email: String,
)

data class SettingsUiState(
    /** null = 仅手动同步。 */
    val syncIntervalMinutes: Long? = 15,
    val accounts: List<AccountManageUi> = emptyList(),
)

class SettingsViewModel(
    private val syncSettings: SyncSettingsStore,
    private val database: MaterialMailDatabase,
    private val credentialStore: CredentialStore,
    private val bodyStore: BodyStore,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        syncSettings.intervalMinutes,
        database.accountDao().observeAll(),
    ) { interval, accounts ->
        SettingsUiState(
            syncIntervalMinutes = interval,
            accounts = accounts.map { AccountManageUi(it.id, it.email) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSyncInterval(minutes: Long?) {
        viewModelScope.launch { syncSettings.setIntervalMinutes(minutes) }
    }

    /**
     * 删除账户：Room 级联删 folders/messages/threads/attachments/drafts/labels，
     * 另外两处手动清理 —— Keystore 加密的凭据、磁盘上的正文文件。
     */
    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            database.accountDao().getById(accountId)?.let { database.accountDao().delete(it) }
            credentialStore.remove(accountId)
            bodyStore.deleteAccount(accountId)
        }
    }

    companion object {
        fun factory(
            syncSettings: SyncSettingsStore,
            database: MaterialMailDatabase,
            credentialStore: CredentialStore,
            bodyStore: BodyStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(syncSettings, database, credentialStore, bodyStore) }
        }
    }
}