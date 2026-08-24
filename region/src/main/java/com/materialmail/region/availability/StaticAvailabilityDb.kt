package com.materialmail.region.availability

import com.materialmail.core.capability.ServiceAvailabilityStatus

/**
 * 内置静态可用性数据库（设计 §8：随版本更新，支持远程配置但非必须）。
 *
 * 诚实原则：UNKNOWN ≠ UNAVAILABLE。没有可靠依据时一律 UNKNOWN。
 * 只记录有明确公开依据的条目。
 */
object StaticAvailabilityDb {

    data class Entry(
        val status: ServiceAvailabilityStatus,
        val detail: String,
    )

    /**
     * key = serviceId。按地区（RegionLabel.country）给出状态。
     * 未登记的 serviceId 或地区 → UNKNOWN。
     */
    private val TABLE: Map<String, Map<String, Entry>> = mapOf(
        "gmail" to mapOf(
            "中国大陆" to Entry(
                ServiceAvailabilityStatus.LIMITED,
                "Google 服务在中国大陆可能无法直接访问，取决于你的网络环境。",
            ),
        ),
        "microsoft_oauth" to mapOf(
            "中国大陆" to Entry(
                ServiceAvailabilityStatus.AVAILABLE,
                "Outlook/Office365 在中国大陆通常可用。",
            ),
        ),
        "connector_feishu" to mapOf(
            "中国大陆" to Entry(ServiceAvailabilityStatus.AVAILABLE, "飞书在中国大陆可用。"),
        ),
        "connector_dingtalk" to mapOf(
            "中国大陆" to Entry(ServiceAvailabilityStatus.AVAILABLE, "钉钉在中国大陆可用。"),
        ),
        "connector_wecom" to mapOf(
            "中国大陆" to Entry(ServiceAvailabilityStatus.AVAILABLE, "企业微信在中国大陆可用。"),
        ),
    )

    fun lookup(serviceId: String, regionCountry: String?): Entry {
        if (regionCountry == null) {
            return Entry(
                ServiceAvailabilityStatus.UNKNOWN,
                "无法确定所在地区，因此无法确认该服务的可用性。",
            )
        }
        return TABLE[serviceId]?.get(regionCountry)
            ?: Entry(
                ServiceAvailabilityStatus.UNKNOWN,
                "没有该服务在你所在地区的可用性记录，无法确认。",
            )
    }
}