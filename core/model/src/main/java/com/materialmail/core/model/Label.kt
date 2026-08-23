package com.materialmail.core.model

/** ARGB 颜色值。定义在 model 层，避免对 android.graphics.Color 的依赖。 */
@JvmInline
value class ColorInt(val argb: Int)

data class Label(
    val id: LabelId,
    val accountId: AccountId,
    val name: String,
    val color: ColorInt?,
)