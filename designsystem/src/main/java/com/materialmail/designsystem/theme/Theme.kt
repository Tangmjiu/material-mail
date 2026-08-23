package com.materialmail.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

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
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
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
