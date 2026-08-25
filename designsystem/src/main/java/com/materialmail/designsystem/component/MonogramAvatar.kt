package com.materialmail.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 发件人单色徽章头像（M3 联系人风格）：姓名首字 + 按地址哈希取色的圆形底。
 * 不引入头像图片（隐私：不请求第三方头像服务），
 * 用色板区分发件人——与 Unread Spine 并列的第二视觉锚点。
 */
@Composable
fun MonogramAvatar(
    /** 展示名或地址（取首字；色板哈希用完整字符串保证稳定）。 */
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    val paletteIndex = abs(name.hashCode()) % AVATAR_PALETTE.size
    val (container, content) = AVATAR_PALETTE[paletteIndex]
    Box(
        modifier = modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = content,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 柔和但可辨识的容器/内容色对（浅色深色都安全的中明度色 + 白字）。 */
private val AVATAR_PALETTE = listOf(
    Color(0xFF0B525B) to Color.White, // 墨青（品牌）
    Color(0xFF6B4A2F) to Color.White, // 暖棕
    Color(0xFF3D5A96) to Color.White, // 靛蓝
    Color(0xFF2D5A3D) to Color.White, // 松绿
    Color(0xFF9B2C3C) to Color.White, // 绯红
    Color(0xFF8A5A00) to Color.White, // 琥珀
    Color(0xFF0B5E8A) to Color.White, // 海蓝
    Color(0xFF5B3E8A) to Color.White, // 堇紫
)
