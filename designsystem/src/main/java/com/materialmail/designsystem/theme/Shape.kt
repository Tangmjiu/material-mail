package com.materialmail.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 Expressive 圆角预算：大圆角是 Expressive 的性格来源。
 *  - extraSmall 4dp  → Unread Spine
 *  - small 8dp       → Chip / 小标签
 *  - medium 16dp     → 输入框 / 小弹层
 *  - large 28dp      → BottomSheet / Dialog / FAB 静止态
 *  - extraLarge 32dp → 大号容器 / FAB 展开态
 * 列表项零圆角：列表靠留白和 1px 色阶分隔，不靠卡片堆叠。
 * 按钮/开关等控件在 Expressive 主题下默认走 stadium（全圆）形状，不在此处定义。
 */
internal val MailShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
