package com.materialmail.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ink & Paper 排版：同一位置，字重即状态。
 *
 * 五级层级（Inbox 场景）：
 *   senderUnread → 未读发件人（Expressive emphasized 思路：加粗 + 轻微放大）
 *   senderRead   → 已读发件人（常规字重，视觉后退）
 *   subject      → 主题
 *   preview      → 摘要（次级色，由调用方上色）
 *   meta         → 时间 / 账户标签
 *
 * 有意识的决定：不引入装饰性展示字体。每天读几百条正文的工具，
 * 个性必须来自层级对比，而不是字体猎奇。
 */
object MailTypeScale {
    val senderUnread = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )
    val senderRead = senderUnread.copy(fontWeight = FontWeight.Normal)
    val subject = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )
    val preview = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )
    val meta = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    )
    /** Composer 正文：行高 1.6，保证长文可读 */
    val composerBody = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.6.sp,
    )
}

/** 标准 M3 Typography，仅微调 display/headline 字重收敛，避免“大字标题”出现在邮箱工具里 */
internal val MailTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
)
