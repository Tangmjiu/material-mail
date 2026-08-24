package com.materialmail.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.core.crypto.CredentialStore
import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.toEntity
import com.materialmail.core.mail.imap.AuthCredentials
import com.materialmail.core.mail.imap.JakartaImapClient
import com.materialmail.core.mail.imap.ServerConfig
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

/** 引导流程步骤。 */
enum class OnboardingStep { PROVIDER, CREDENTIALS, CONNECTING, SUCCESS, FAILURE }

data class AddAccountUiState(
    val step: OnboardingStep = OnboardingStep.PROVIDER,
    /** null = 手动配置（自定义 IMAP/SMTP）。 */
    val selectedPreset: ProviderPreset? = null,
    val email: String = "",
    val password: String = "",
    val host: String = "",
    val port: String = "",
    val smtpHost: String = "",
    val smtpPort: String = "",
    val encryption: Encryption = Encryption.SSL_TLS,
    /** 用户是否手动改过服务器设置（改过则不再被预设覆盖）。 */
    val serverTouched: Boolean = false,
    /** 明文连接的显式确认（安全模型 §11：必须用户主动勾选）。 */
    val cleartextConfirmed: Boolean = false,
    /** 连接/保存失败的错误信息（FAILURE 步展示）。 */
    val error: String? = null,
)

class AddAccountViewModel(
    private val database: MaterialMailDatabase,
    private val credentialStore: CredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    /** 选择服务商（null = 手动配置）：自动带出服务器参数，进入填写凭证步。 */
    fun selectPreset(preset: ProviderPreset?) {
        _uiState.update {
            if (preset == null) {
                it.copy(
                    step = OnboardingStep.CREDENTIALS,
                    selectedPreset = null,
                    serverTouched = false,
                    error = null,
                )
            } else {
                it.copy(
                    step = OnboardingStep.CREDENTIALS,
                    selectedPreset = preset,
                    host = preset.imap.host,
                    port = preset.imap.port.toString(),
                    smtpHost = preset.smtp.host,
                    smtpPort = preset.smtp.port.toString(),
                    encryption = preset.imap.encryption,
                    serverTouched = false,
                    error = null,
                )
            }
        }
    }

    fun backToProviders() =
        _uiState.update { it.copy(step = OnboardingStep.PROVIDER, error = null) }

    fun onEmailChanged(value: String) {
        _uiState.update { state ->
            // 手动配置模式下，按域名再猜一次预设（减轻手填负担）
            val preset = state.selectedPreset ?: ProviderPresets.findByEmail(value)
            if (preset != null && !state.serverTouched && state.selectedPreset == null) {
                state.copy(
                    email = value,
                    host = preset.imap.host,
                    port = preset.imap.port.toString(),
                    smtpHost = preset.smtp.host,
                    smtpPort = preset.smtp.port.toString(),
                    encryption = preset.imap.encryption,
                )
            } else {
                state.copy(email = value)
            }
        }
    }

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value) }

    fun onHostChanged(value: String) =
        _uiState.update { it.copy(host = value, serverTouched = true) }

    fun onPortChanged(value: String) =
        _uiState.update { it.copy(port = value.filter(Char::isDigit), serverTouched = true) }

    fun onSmtpHostChanged(value: String) =
        _uiState.update { it.copy(smtpHost = value, serverTouched = true) }

    fun onSmtpPortChanged(value: String) =
        _uiState.update { it.copy(smtpPort = value.filter(Char::isDigit), serverTouched = true) }

    fun onEncryptionChanged(value: Encryption) =
        _uiState.update {
            it.copy(encryption = value, serverTouched = true, cleartextConfirmed = false)
        }

    fun onCleartextConfirmedChanged(value: Boolean) =
        _uiState.update { it.copy(cleartextConfirmed = value) }

    /** 验证并添加：先真实 IMAP 登录握手，通过才落库。 */
    fun connectAndSave() {
        val state = _uiState.value
        val error = validate(state)
        if (error != null) {
            _uiState.update { it.copy(step = OnboardingStep.FAILURE, error = error) }
            return
        }
        _uiState.update { it.copy(step = OnboardingStep.CONNECTING, error = null) }
        viewModelScope.launch {
            val email = state.email.trim()
            val client = JakartaImapClient()
            val connectError = runCatching {
                client.connect(
                    ServerConfig(
                        host = state.host.trim(),
                        port = state.port.toInt(),
                        encryption = state.encryption,
                        allowCleartext = state.encryption == Encryption.NONE,
                    ),
                    AuthCredentials.Password(email, state.password),
                )
            }.exceptionOrNull()
            runCatching { client.disconnect() }

            if (connectError != null) {
                _uiState.update {
                    it.copy(
                        step = OnboardingStep.FAILURE,
                        error = friendlyError(connectError),
                    )
                }
                return@launch
            }
            persist(state, email)
        }
    }

    /** 验证失败后的逃生门：用户确认参数无误时允许直接保存（设计上的显式选择，不是 bug）。 */
    fun saveAnyway() {
        val state = _uiState.value
        val error = validate(state)
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(step = OnboardingStep.CONNECTING, error = null) }
        viewModelScope.launch { persist(state, state.email.trim()) }
    }

    fun retry() = _uiState.update { it.copy(step = OnboardingStep.CREDENTIALS, error = null) }

    private suspend fun persist(state: AddAccountUiState, email: String) {
        runCatching {
            val accountId = AccountId("acc_" + email.lowercase())
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
                smtp = ServerEndpoint(
                    host = state.smtpHost.trim().ifBlank {
                        "smtp." + email.substringAfter('@').lowercase()
                    },
                    port = state.smtpPort.toIntOrNull()
                        ?: if (state.encryption == Encryption.STARTTLS) 587 else 465,
                    encryption = state.encryption,
                ),
                syncState = SyncState.NOT_SYNCED,
                createdAt = Instant.now(),
            )
            database.accountDao().upsert(account.toEntity())
            credentialStore.savePassword(accountId.value, state.password)
        }.onSuccess {
            _uiState.update { it.copy(step = OnboardingStep.SUCCESS, error = null) }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    step = OnboardingStep.FAILURE,
                    error = "保存失败：" + (e.message ?: e.javaClass.simpleName),
                )
            }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("AUTHENTICATIONFAILED", true) ||
                msg.contains("Invalid credentials", true) ||
                msg.contains("authentication failed", true) ||
                msg.contains("Login fail", true) ->
                "登录被拒绝：账号或密码/授权码不正确（部分邮箱需要用授权码而不是登录密码）"
            msg.contains("UnknownHost", true) ->
                "找不到服务器：请检查 IMAP 地址拼写"
            msg.contains("ConnectException", true) || msg.contains("timed out", true) ->
                "连接超时：请检查网络，或确认该服务商在当前网络可用"
            msg.contains("SSLHandshake", true) || msg.contains("certificate", true) ->
                "TLS 握手失败：请检查加密方式与端口是否匹配（SSL 993 / STARTTLS 143）"
            else -> "连接失败：$msg"
        }
    }

    private fun validate(state: AddAccountUiState): String? {
        val email = state.email.trim()
        if (!EMAIL_REGEX.matches(email)) return "邮箱地址格式不正确"
        if (state.password.isBlank()) return "请输入密码或授权码"
        if (state.host.isBlank()) return "请填写 IMAP 服务器地址"
        val port = state.port.toIntOrNull()
        if (port == null || port !in 1..65535) return "IMAP 端口号不正确"
        if (state.smtpHost.isNotBlank()) {
            val sp = state.smtpPort.toIntOrNull()
            if (sp == null || sp !in 1..65535) return "SMTP 端口号不正确"
        }
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
