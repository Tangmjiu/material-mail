package com.materialmail.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material Mail · Ink & Paper 色板
 *
 * 种子色：墨青（Deep Ink Teal）。开启 Dynamic Color 时整套方案被用户壁纸色接管，
 * 这里的值是无壁纸 / Android 12 以下设备的兜底，也是品牌在没有品牌色时的性格。
 *
 * 规则（写死在设计系统，不靠自觉）：
 *  - 正文阅读区域禁止出现 primary 色块；primary 只用于 Unread Spine / FAB / 选中态 / 链接
 *  - 层级优先用 surface 色阶差表达，其次 outline，不用阴影堆层级
 */

// ── Light ────────────────────────────────
private val InkTeal = Color(0xFF0B525B)
private val OnInkTeal = Color(0xFFFFFFFF)
private val InkTealContainer = Color(0xFFD2E8EA)
private val OnInkTealContainer = Color(0xFF063B42)

private val PaperWhite = Color(0xFFFAFAF8)
private val PaperContainer = Color(0xFFF1EFEA)
private val PaperContainerHigh = Color(0xFFEBE8E2)
private val InkBlack = Color(0xFF1A1C1A)
private val InkSecondary = Color(0xFF5A6B6C)
private val PaperOutline = Color(0xFFDBD8D0)

private val ErrorRed = Color(0xFFBA1A1A)

internal val LightScheme = lightColorScheme(
    primary = InkTeal,
    onPrimary = OnInkTeal,
    primaryContainer = InkTealContainer,
    onPrimaryContainer = OnInkTealContainer,
    secondary = InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6E5),
    onSecondaryContainer = Color(0xFF243334),
    tertiary = Color(0xFF4C5F7C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E2F7),
    onTertiaryContainer = Color(0xFF0F2440),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = PaperWhite,
    onBackground = InkBlack,
    surface = PaperWhite,
    onSurface = InkBlack,
    surfaceVariant = PaperContainer,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4F1),
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = Color(0xFFE5E2DA),
    outline = Color(0xFF8A9494),
    outlineVariant = PaperOutline,
)

// ── Dark ─────────────────────────────────
internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AD3D9),
    onPrimary = Color(0xFF063B42),
    primaryContainer = Color(0xFF0F4A52),
    onPrimaryContainer = Color(0xFFD2E8EA),
    secondary = Color(0xFFA8BABA),
    onSecondary = Color(0xFF1E2A2B),
    secondaryContainer = Color(0xFF334242),
    onSecondaryContainer = Color(0xFFC4D6D5),
    tertiary = Color(0xFFA9C3E4),
    onTertiary = Color(0xFF14263C),
    tertiaryContainer = Color(0xFF2A3C54),
    onTertiaryContainer = Color(0xFFD7E2F7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131515),
    onBackground = Color(0xFFE4E3DE),
    surface = Color(0xFF131515),
    onSurface = Color(0xFFE4E3DE),
    surfaceVariant = Color(0xFF232625),
    onSurfaceVariant = Color(0xFFAFBBB9),
    surfaceContainerLowest = Color(0xFF0D0F0F),
    surfaceContainerLow = Color(0xFF191B1B),
    surfaceContainer = Color(0xFF1E2120),
    surfaceContainerHigh = Color(0xFF282B2A),
    surfaceContainerHighest = Color(0xFF333635),
    outline = Color(0xFF7A8584),
    outlineVariant = Color(0xFF3A3F3E),
)
