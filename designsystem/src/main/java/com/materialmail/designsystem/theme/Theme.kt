package com.materialmail.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Material Mail 主题入口。
 *
 * @param dynamicColor Dynamic Color 默认开启：Android 12+ 下整套色板被用户壁纸色接管，
 *                     墨青只是兜底。这不是可选项是默认行为。
 */
@Composable
fun MaterialMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** Pro 个性化：覆盖种子色（仅影响 primary 系角色，spine/FAB/选中态）。 */
    primaryOverride: androidx.compose.ui.graphics.Color? = null,
    /** Pro 个性化：紧凑列表密度。 */
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
        baseScheme.copy(
            primary = primaryOverride,
            primaryContainer = primaryOverride.copy(alpha = 0.2f),
        )
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
        MaterialTheme(
            colorScheme = colorScheme,
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
}
