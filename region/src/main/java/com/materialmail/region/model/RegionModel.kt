package com.materialmail.region.model

/**
 * 地区标签（需求 §26/§27 + 已确认决策）：
 * 文字标签严格执行产品四标签规范（政治表述由文字承担），
 * Emoji 按 Unicode 国际标准如实渲染，两者职责分离。
 * 检测输出必须映射到这张常量表之一，没有任何代码路径允许动态生成地区名。
 */
data class RegionLabel(
    val country: String,
    val administrativeArea: String?,
    val locality: String?,
    val emoji: String?,
) {
    /** 展示格式："中国大陆 · 上海市"（无障碍：emoji 不承担唯一信息表达）。 */
    val displayText: String
        get() = buildString {
            emoji?.let { append(it).append(' ') }
            append(country)
            administrativeArea?.let { append(" · ").append(it) }
            locality?.let { append(" · ").append(it) }
        }
}

/** 产品四标签常量表（硬编码，唯一合法来源）。 */
object ChinaRegionLabels {
    val MAINLAND = RegionLabel("中国大陆", null, null, "🇨🇳")
    val TAIWAN = RegionLabel("中国台湾", null, null, "🇹🇼")
    val HONG_KONG = RegionLabel("中国香港", null, null, "🇭🇰")
    val MACAU = RegionLabel("中国澳门", null, null, "🇲🇴")
    val ALL = listOf(MAINLAND, TAIWAN, HONG_KONG, MACAU)
}

/** 手动覆盖可选的其他常见地区（中性，非穷尽）。 */
object CommonRegionLabels {
    val SINGAPORE = RegionLabel("新加坡", null, null, "🇸🇬")
    val JAPAN = RegionLabel("日本", null, null, "🇯🇵")
    val UNITED_STATES = RegionLabel("美国", null, null, "🇺🇸")
    val ALL = ChinaRegionLabels.ALL + SINGAPORE + JAPAN + UNITED_STATES
}

/** 检测置信度（设计 §8：显式建模，绝不猜）。 */
enum class RegionConfidence { HIGH, MEDIUM, LOW, UNKNOWN }

data class RegionResult(
    val label: RegionLabel?,
    val confidence: RegionConfidence,
    /** 检测来源描述，设置页对用户透明展示。 */
    val source: String,
)