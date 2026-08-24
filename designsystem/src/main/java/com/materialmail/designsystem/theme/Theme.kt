package com.materialmail.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Material Mail 主题入口 —— Material 3 Expressive。
 *
 * 与 plain MD3 的差别不在皮肤，在三件事：
 *  1. [MaterialExpressiveTheme] 启用 Expressive 组件行为（按钮弹性按压、
 *     大号圆角预算、强调字阶）；
 *  2. [MotionScheme.expressive] 全局动效曲线 = spring 物理（空间动画有弹性、
 *     效果动画快进快出），全 App 组件默认继承，不逐个手写 spec；
 *  3. Dynamic Color 默认开启：Android 12+ 整套色板被用户壁纸接管，墨青只是兜底。
 *
 * @param primaryOverride Pro 个性化：覆盖种子色（仅影响 primary 系角色，spine/FAB/选中态）。
 * @param compactSpacing Pro 个性化：紧凑列表密度。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    primaryOverride: androidx.compose.ui.graphics.Color? = null,
    compactSpacing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val colorScheme = if (primaryOverride != null) {
        // 完整 tonal 派生（不再是只换两个槽位的半成品）
        seedColorScheme(baseScheme, primaryOverride, darkTheme)
    } else {
        baseScheme
    }

    CompositionLocalProvider(
        LocalSpacing provides if (compactSpacing) {
            Spacing(listItemVertical = 8.dp)
        } else {
            Spacing()
        },
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            // Expressive 的灵魂：全组件共享同一套 spring 物理，
            // 空间移动有弹性（轻微回冲），透明度/颜色等效果动画快而克制
            motionScheme = MotionScheme.expressive(),
            typography = MailTypography,
            shapes = MailShapes,
            content = content,
        )
    }
}

/** 主题便捷访问 */
object MailTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    /** 全 App 统一动效规格（Expressive spring 物理），禁止就地手写 tween。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val motionScheme: MotionScheme
        @Composable get() = MaterialTheme.motionScheme
}
