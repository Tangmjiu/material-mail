package com.materialmail.feature.settings.yolo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.agent.yolo.YoloCapabilities
import com.materialmail.agent.yolo.YoloCapabilityStore
import com.materialmail.agent.yolo.YoloSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 四步启用流程的步骤（需求 §37-§41：独立页面，不是 Dialog 连击）。 */
enum class YoloEnableStep { WARNING, RISK_CHECKBOX, TYPE_YOLO, FINAL }

data class YoloUiState(
    val active: Boolean = false,
    val capabilities: YoloCapabilities = YoloCapabilities(),
    val step: YoloEnableStep? = null,
    val riskChecked: Boolean = false,
    val typedInput: String = "",
    val responsibilityChecked: Boolean = false,
)

class YoloViewModel(
    private val store: YoloCapabilityStore,
    private val sessionManager: YoloSessionManager,
    /** 状态变化联动（app 注入常驻通知的显示/隐藏）。 */
    private val onActiveChanged: (Boolean) -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YoloUiState())
    val uiState: StateFlow<YoloUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.active.collect { active ->
                _uiState.update { it.copy(active = active) }
            }
        }
        viewModelScope.launch {
            store.capabilities.collect { caps ->
                _uiState.update { it.copy(capabilities = caps) }
            }
        }
    }

    fun startEnableFlow() = _uiState.update {
        it.copy(
            step = YoloEnableStep.WARNING,
            riskChecked = false,
            typedInput = "",
            responsibilityChecked = false,
        )
    }

    fun cancelEnableFlow() = _uiState.update { it.copy(step = null) }

    fun nextStep() = _uiState.update {
        when (it.step) {
            YoloEnableStep.WARNING -> it.copy(step = YoloEnableStep.RISK_CHECKBOX)
            YoloEnableStep.RISK_CHECKBOX ->
                if (it.riskChecked) it.copy(step = YoloEnableStep.TYPE_YOLO) else it
            YoloEnableStep.TYPE_YOLO ->
                if (isValidInput(it.typedInput)) it.copy(step = YoloEnableStep.FINAL) else it
            else -> it
        }
    }

    fun onRiskChecked(checked: Boolean) = _uiState.update { it.copy(riskChecked = checked) }
    fun onTypedInput(value: String) = _uiState.update { it.copy(typedInput = value) }
    fun onResponsibilityChecked(checked: Boolean) =
        _uiState.update { it.copy(responsibilityChecked = checked) }

    fun confirmEnable() {
        if (!_uiState.value.responsibilityChecked) return
        viewModelScope.launch {
            sessionManager.enable()
            onActiveChanged(true)
            _uiState.update { it.copy(step = null) }
        }
    }

    fun disable() {
        viewModelScope.launch {
            sessionManager.disable()
            onActiveChanged(false)
        }
    }

    fun updateCapabilities(transform: (YoloCapabilities) -> YoloCapabilities) {
        viewModelScope.launch {
            val updated = transform(store.capabilities.first())
            store.setCapabilities(updated)
            sessionManager.refreshCapabilities()
        }
    }

    private fun isValidInput(value: String): Boolean =
        value.trim().uppercase() == "YOLO" || value.trim().uppercase() == "ENABLE YOLO"

    companion object {
        fun factory(
            store: YoloCapabilityStore,
            sessionManager: YoloSessionManager,
            onActiveChanged: (Boolean) -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { YoloViewModel(store, sessionManager, onActiveChanged) }
        }
    }
}