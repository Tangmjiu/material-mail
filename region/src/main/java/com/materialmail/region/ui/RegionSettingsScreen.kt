package com.materialmail.region.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.materialmail.designsystem.theme.MailTheme
import com.materialmail.designsystem.theme.MailTypeScale
import com.materialmail.region.detection.RegionDetector
import com.materialmail.region.model.CommonRegionLabels
import com.materialmail.region.model.RegionConfidence
import com.materialmail.region.model.RegionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RegionSettingsUiState(
    val loading: Boolean = true,
    val current: RegionResult? = null,
    val manualOverrideCountry: String? = null,
)

class RegionSettingsViewModel(
    private val detector: RegionDetector,
    private val noticeStore: RegionNoticeStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegionSettingsUiState())
    val uiState: StateFlow<RegionSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = detector.currentRegion()
            _uiState.value = RegionSettingsUiState(
                loading = false,
                current = current,
                manualOverrideCountry = detector.manualOverride.first(),
            )
        }
    }

    fun setManualOverride(country: String?) {
        viewModelScope.launch {
            detector.setManualOverride(country)
            refresh()
        }
    }

    fun resetNotices() {
        viewModelScope.launch { noticeStore.resetAll() }
    }

    companion object {
        fun factory(
            detector: RegionDetector,
            noticeStore: RegionNoticeStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RegionSettingsViewModel(detector, noticeStore) }
        }
    }
}

/**
 * 设置 → 隐私 → 地区与服务可用性（需求 §30/§31）：
 * 当前地区 + 检测状态 + 手动覆盖 + 清除/恢复 + 重置提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSettingsScreen(
    viewModel: RegionSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("地区与服务可用性", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Column(modifier = Modifier.padding(MailTheme.spacing.lg)) {
                    Text(
                        "当前地区",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(MailTheme.spacing.sm))
                    val current = uiState.current
                    Text(
                        when {
                            uiState.loading -> "检测中…"
                            current == null || current.confidence == RegionConfidence.UNKNOWN ||
                                current.label == null -> "无法确定所在地区"
                            else -> current.label.displayText
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    current?.let {
                        Text(
                            "来源：" + it.source + " · 置信度：" + confidenceLabel(it.confidence),
                            style = MailTypeScale.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(MailTheme.spacing.sm))
                    Text(
                        "地区检测只使用系统地区和时区，不请求定位权限，不访问网络，" +
                            "不与你的邮箱账户关联。地区信息仅用于服务可用性提示。",
                        style = MailTypeScale.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh) }

            item {
                Text(
                    "手动选择地区",
                    style = MailTypeScale.meta,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(MailTheme.spacing.lg),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setManualOverride(null) }
                        .padding(horizontal = MailTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.manualOverrideCountry == null,
                        onClick = { viewModel.setManualOverride(null) },
                    )
                    Text(
                        "自动检测",
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            items(
                items = CommonRegionLabels.ALL,
                key = { it.country },
            ) { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setManualOverride(label.country) }
                        .padding(horizontal = MailTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.manualOverrideCountry == label.country,
                        onClick = { viewModel.setManualOverride(label.country) },
                    )
                    Text(
                        label.displayText,
                        style = MailTypeScale.subject,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh) }
            item {
                Row(
                    modifier = Modifier.padding(MailTheme.spacing.lg),
                ) {
                    TextButton(onClick = viewModel::resetNotices) {
                        Text("重新开启服务可用性提示", style = MailTypeScale.meta)
                    }
                }
            }
        }
    }
}

private fun confidenceLabel(confidence: RegionConfidence): String = when (confidence) {
    RegionConfidence.HIGH -> "高"
    RegionConfidence.MEDIUM -> "中"
    RegionConfidence.LOW -> "低"
    RegionConfidence.UNKNOWN -> "无法确定"
}