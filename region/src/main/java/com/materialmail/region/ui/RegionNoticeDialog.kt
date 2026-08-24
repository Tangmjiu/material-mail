package com.materialmail.region.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.materialmail.designsystem.theme.MailTypeScale
import com.materialmail.region.model.RegionConfidence
import com.materialmail.region.model.RegionResult

/**
 * 地区不适用提示（需求 §24/§34）：
 * 中立、克制、事实导向。不说"你的地区禁止"，只说"可能不适用"。
 * 按钮：继续尝试 / 取消 / 不再提示。
 */
@Composable
fun RegionNoticeDialog(
    serviceName: String,
    region: RegionResult,
    availabilityDetail: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onDismissForever: () -> Unit,
) {
    val regionText = when {
        region.confidence == RegionConfidence.UNKNOWN || region.label == null ->
            "无法确定所在地区"
        region.confidence == RegionConfidence.LOW || region.confidence == RegionConfidence.MEDIUM ->
            "可能位于" + region.label.displayText
        else -> region.label.displayText
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "此服务可能不适用于你所在的地区",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                "当前检测地区：$regionText\n\n$availabilityDetail",
                style = MailTypeScale.preview,
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text("继续尝试") }
        },
        dismissButton = {
            TextButton(onClick = onDismissForever) { Text("不再提示") }
        },
    )
}