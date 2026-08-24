package com.materialmail.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.dp

/**
 * 邮箱服务商品牌图标（手绘矢量，无位图资源）。
 * 风格统一为品牌色圆角底 + 白色/品牌色图形，视觉重量一致。
 */
object ProviderIcons {

    private fun roundedSquare(color: Color): ImageVector.Builder.() -> Unit = {
        path(
            fill = SolidColor(color),
            pathFillType = PathFillType.NonZero,
        ) {
            // 24dp 画板上的 6dp 圆角方形
            moveTo(6f, 2f); lineTo(18f, 2f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 6f)
            lineTo(22f, 18f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 22f)
            lineTo(6f, 22f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 18f)
            lineTo(2f, 6f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6f, 2f)
            close()
        }
    }

    /** Gmail：经典红 M 信封。 */
    val Gmail: ImageVector
        get() = ImageVector.Builder("Gmail", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color(0xFFEA4335))) {
                // M 屋顶
                moveTo(3f, 6.2f); lineTo(12f, 12.2f); lineTo(21f, 6.2f)
                lineTo(21f, 8.6f); lineTo(12f, 14.6f); lineTo(3f, 8.6f); close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                // 左竖条
                moveTo(3f, 6.2f); lineTo(6.2f, 8.3f); lineTo(6.2f, 19f)
                lineTo(4.4f, 19f); arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 17.6f)
                close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                // 右竖条
                moveTo(21f, 6.2f); lineTo(17.8f, 8.3f); lineTo(17.8f, 19f)
                lineTo(19.6f, 19f); arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 21f, 17.6f)
                close()
            }
        }.build()

    /** Outlook：蓝底 + 白色 O 环 + 信封折线。 */
    val Outlook: ImageVector
        get() = ImageVector.Builder("Outlook", 24.dp, 24.dp, 24f, 24f).apply {
            roundedSquare(Color(0xFF0F6CBD))()
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                // O 环（evenOdd 外圆减内圆），偏左
                moveTo(9.5f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.49f, 8f)
                close()
                moveTo(9.5f, 10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9.51f, 10f)
                close()
            }
            path(fill = SolidColor(Color(0x66FFFFFF))) {
                // 右下信封折角
                moveTo(14f, 14f); lineTo(19f, 14f); lineTo(19f, 18f); lineTo(14f, 18f); close()
                moveTo(14f, 14f)
            }
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1f) {
                moveTo(14f, 14.5f); lineTo(16.5f, 16.2f); lineTo(19f, 14.5f)
            }
        }.build()

    /** QQ 邮箱：蓝底 + 简化白企鹅。 */
    val Qq: ImageVector
        get() = ImageVector.Builder("QQ", 24.dp, 24.dp, 24f, 24f).apply {
            roundedSquare(Color(0xFF1296DB))()
            path(fill = SolidColor(Color.White)) {
                // 身体
                moveTo(12f, 4.5f)
                arcTo(3.2f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.99f, 4.5f)
                close()
                moveTo(12f, 9f)
                arcTo(4.6f, 5.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.99f, 9f)
                close()
            }
            path(fill = SolidColor(Color(0xFFFF8C00))) {
                // 喙
                moveTo(12f, 7.4f); lineTo(10.9f, 8.6f); lineTo(13.1f, 8.6f); close()
                // 脚
                moveTo(8.6f, 19.4f); lineTo(11f, 19.4f); lineTo(11f, 20.4f); lineTo(8.6f, 20.4f); close()
                moveTo(13f, 19.4f); lineTo(15.4f, 19.4f); lineTo(15.4f, 20.4f); lineTo(13f, 20.4f); close()
            }
        }.build()

    /** 163：红底 + 白色信封。 */
    val Netease: ImageVector
        get() = ImageVector.Builder("163", 24.dp, 24.dp, 24f, 24f).apply {
            roundedSquare(Color(0xFFE21A1A))()
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
                moveTo(5.5f, 8f); lineTo(18.5f, 8f); lineTo(18.5f, 16.5f); lineTo(5.5f, 16.5f); close()
            }
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
                moveTo(5.8f, 8.5f); lineTo(12f, 12.8f); lineTo(18.2f, 8.5f)
            }
        }.build()

    /** iCloud：蓝底 + 白云。 */
    val ICloud: ImageVector
        get() = ImageVector.Builder("iCloud", 24.dp, 24.dp, 24f, 24f).apply {
            roundedSquare(Color(0xFF3693F3))()
            path(fill = SolidColor(Color.White)) {
                moveTo(7.5f, 17.5f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.3f, 11.2f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.8f, 10.2f)
                arcTo(3.6f, 3.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.5f, 17.5f)
                close()
            }
        }.build()

    /** Yahoo：紫底 + 白 Y!。 */
    val Yahoo: ImageVector
        get() = ImageVector.Builder("Yahoo", 24.dp, 24.dp, 24f, 24f).apply {
            roundedSquare(Color(0xFF6001D2))()
            path(fill = SolidColor(Color.White)) {
                moveTo(6.5f, 5.5f); lineTo(11f, 10.5f); lineTo(11f, 15.5f)
                lineTo(9f, 15.5f); lineTo(9f, 10.5f); lineTo(4.5f, 5.5f); close()
                moveTo(13f, 5.5f); lineTo(15f, 5.5f); lineTo(15f, 11f); lineTo(13f, 11f); close()
                moveTo(13f, 12.5f); lineTo(15f, 12.5f); lineTo(15f, 14.5f); lineTo(13f, 14.5f); close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(17.5f, 5.5f); lineTo(19.5f, 5.5f); lineTo(13.5f, 18.5f)
                lineTo(11.5f, 18.5f); close()
            }
        }.build()

    /** 自定义：线框信封 + 加号。 */
    val Custom: ImageVector
        get() = ImageVector.Builder("Custom", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color(0xFF5F6368)), strokeLineWidth = 1.6f) {
                moveTo(3.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 14f); lineTo(3.5f, 14f); close()
            }
            path(stroke = SolidColor(Color(0xFF5F6368)), strokeLineWidth = 1.6f) {
                moveTo(3.8f, 7f); lineTo(10.5f, 11.5f); lineTo(17.2f, 7f)
            }
            path(stroke = SolidColor(Color(0xFF5F6368)), strokeLineWidth = 1.8f) {
                moveTo(17f, 16f); lineTo(21f, 16f)
                moveTo(19f, 14f); lineTo(19f, 18f)
            }
        }.build()
}

@Composable
fun ProviderIcon(presetId: String?, modifier: Modifier = Modifier) {
    val image = when (presetId) {
        "gmail" -> ProviderIcons.Gmail
        "outlook" -> ProviderIcons.Outlook
        "qq" -> ProviderIcons.Qq
        "netease" -> ProviderIcons.Netease
        "icloud" -> ProviderIcons.ICloud
        "yahoo" -> ProviderIcons.Yahoo
        else -> ProviderIcons.Custom
    }
    Image(
        imageVector = image,
        contentDescription = null,
        modifier = modifier,
    )
}
