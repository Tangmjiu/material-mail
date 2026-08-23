package com.materialmail.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.crypto.CredentialStore
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toEntity
import com.materialmail.core.model.Account
import com.materialmail.core.model.AccountId
import com.materialmail.core.model.Encryption
import com.materialmail.core.model.Protocol
import com.materialmail.core.model.ServerEndpoint
import com.materialmail.core.model.SyncState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddAccountUiState(
    val email: String = "",
    val password: String = "",
    val host: String = "",
    val port: String = "",
    val encryption: Encryption = Encryption.SSL_TLS,
    /** 用户是否手动改过服务器设置（改过则不再被预设覆盖）。 */
    val serverTouched: Boolean = false,
    val presetHint: String? = null,
    /** 明文连接的显式确认（安全模型 §11：必须用户主动勾选）。 */
    val cleartextConfirmed: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddAccountViewModel(
    private val database: MaterialMailDatabase,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { state ->
            val preset = ProviderPresets.findByEmail(value)
            if (preset != null && !state.serverTouched) {
                state.copy(
                    email = value,
                    host = preset.imap.host,
                    port = preset.imap.port.toString(),
                    encryption = preset.imap.encryption,
                    presetHint = preset.hint,
                )
            } else {
                state.copy(email = value, presetHint = preset?.hint)
            }
        }
    }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value) }

    fun onHostChanged(value: String) =
        _uiState.update { it.copy(host = value, serverTouched = true) }

    fun onPortChanged(value: String) =
        _uiState.update { it.copy(port = value.filter(Char::isDigit), serverTouched = true) }

    fun onEncryptionChanged(value: Encryption) =
        _uiState.update {
            it.copy(encryption = value, serverTouched = true, cleartextConfirmed = false)
        }

    fun onCleartextConfirmedChanged(value: Boolean) =
        _uiState.update { it.copy(cleartextConfirmed = value) }

    fun save() {
        val state = _uiState.value
        val error = validate(state)
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val email = state.email.trim()
                val accountId = AccountId("acc_" + email.lowercase())
                val smtpPreset = ProviderPresets.findByEmail(email)?.smtp
                val account = Account(
                    id = accountId,
                    email = email,
                    displayName = null,
                    protocol = Protocol.IMAP,
                    imap = ServerEndpoint(
                        host = state.host.trim(),
                        port = state.port.toInt(),
                        encryption = state.encryption,
                    ),
                    smtp = smtpPreset ?: ServerEndpoint(
                        host = "smtp." + email.substringAfter('@').lowercase(),
                        port = if (state.encryption == Encryption.STARTTLS) 587 else 465,
                        encryption = state.encryption,
                    ),
                    syncState = SyncState.NOT_SYNCED,
                    createdAt = Instant.now(),
                )
                database.accountDao().upsert(account.toEntity())
                credentialStore.savePassword(accountId.value, state.password)
            }.onSuccess {
                _uiState.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(saving = false, error = "保存失败：" + (e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun validate(state: AddAccountUiState): String? {
        val email = state.email.trim()
        if (!EMAIL_REGEX.matches(email)) return "邮箱地址格式不正确"
        if (state.password.isBlank()) return "请输入密码或授权码"
        if (state.host.isBlank()) return "请填写 IMAP 服务器地址"
        val port = state.port.toIntOrNull()
        if (port == null || port !in 1..65535) return "端口号不正确"
        if (state.encryption == Encryption.NONE && !state.cleartextConfirmed) {
            return "明文连接需要先确认你已了解风险"
        }
        return null
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun factory(
            database: MaterialMailDatabase,
            credentialStore: CredentialStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddAccountViewModel(database, credentialStore) }
        }
    }
}