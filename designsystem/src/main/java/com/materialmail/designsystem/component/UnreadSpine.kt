package com.materialmail.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/**
 * Unread Spine —— Material Mail 的签名元素。
 *
 * 未读邮件不用圆点 badge：左侧出现一条 4dp 宽的 primary 色强调条，
 * 非对称圆角（右侧圆、左侧直，像书脊）。已读/归档时以 spring 收缩消失，
 * 与发件人字重回落同步发生。出现/消失是形变，不是淡入淡出。
 */
@Composable
fun UnreadSpine(
    unread: Boolean,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (unread) 4.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "unreadSpineWidth",
    )
    Box(modifier = modifier.height(56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(width)
                .height(40.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
