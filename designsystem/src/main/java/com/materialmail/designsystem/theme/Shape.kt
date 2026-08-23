package com.materialmail.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角预算：全文只有三档。
 *  - small 4dp   → Unread Spine / Chip
 *  - medium 16dp → BottomSheet / Dialog
 *  - large 28dp  → FAB 及 Expressive 组件默认
 * 列表项零圆角：列表靠留白和 1px 色阶分隔，不靠卡片堆叠。
 */
internal val MailShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
