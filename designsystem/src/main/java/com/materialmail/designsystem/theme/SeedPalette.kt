package com.materialmail.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 种子色 → 完整 tonal 色板（修复旧实现只覆盖 primary/primaryContainer(alpha 0.2)
 * 导致的对比度崩坏：容器色偏灰、文字与容器不同色相，选中态几乎不可见）。
 *
 * 用 HSL 明度分层近似 Material tonal palette（0/10/30/90 四档）：
 * 色相与饱和度完全跟随种子色，只调明度，保证成对角色（X / onX）对比度达标。
 */
internal fun seedColorScheme(base: ColorScheme, seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (seed.alpha * 255).toInt(),
            (seed.red * 255).toInt(),
            (seed.green * 255).toInt(),
            (seed.blue * 255).toInt(),
        ),
        hsv,
    )
    fun tone(value: Float, saturationScale: Float = 1f): Color {
        val argb = android.graphics.Color.HSVToColor(
            floatArrayOf(hsv[0], (hsv[1] * saturationScale).coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
        )
        return Color(argb)
    }
    return if (!dark) {
        base.copy(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.92f),
            onPrimaryContainer = tone(0.18f),
            inversePrimary = tone(0.80f),
            primaryFixed = tone(0.92f),
            primaryFixedDim = tone(0.80f),
            onPrimaryFixed = tone(0.10f),
            onPrimaryFixedVariant = tone(0.30f),
            surfaceTint = tone(0.40f),
        )
    } else {
        base.copy(
            primary = tone(0.80f),
            onPrimary = tone(0.20f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.92f),
            inversePrimary = tone(0.40f),
            primaryFixed = tone(0.92f),
            primaryFixedDim = tone(0.80f),
            onPrimaryFixed = tone(0.10f),
            onPrimaryFixedVariant = tone(0.30f),
            surfaceTint = tone(0.80f),
        )
    }.let { scheme ->
        // onPrimary 兜底：种子色本身很浅时白字对比度不足，强制深色
        if (!dark && scheme.primary.luminance() > 0.6f) {
            scheme.copy(onPrimary = tone(0.10f))
        } else {
            scheme
        }
    }
}
