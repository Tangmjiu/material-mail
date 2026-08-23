package com.materialmail.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 间距基数 4dp。列表项垂直 12dp：信息密度与单手触控面积的平衡点。 */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** 详情页正文最大宽度：平板/折叠屏自动居中留白 */
    val contentMaxWidth: Dp = 640.dp,
    val listItemVertical: Dp = 12.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }
