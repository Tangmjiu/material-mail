package com.materialmail.region.availability

import com.materialmail.core.capability.AvailabilityInfo
import com.materialmail.core.capability.ServiceAvailabilityChecker
import com.materialmail.region.detection.RegionDetector
import com.materialmail.region.model.RegionConfidence

/**
 * Region 模块对 Core 契约的实现：替换 AllowAllAvailabilityChecker。
 *
 * 低置信度 → 状态降级为 UNKNOWN（VPN/时区异常时绝不乱报"不可用"）。
 * 删掉 region 模块后 Core 回到 AllowAll 默认实现，核心功能零影响。
 */
class RegionAvailabilityChecker(
    private val detector: RegionDetector,
) : ServiceAvailabilityChecker {

    override suspend fun check(serviceId: String): AvailabilityInfo {
        val region = detector.currentRegion()
        if (region.confidence == RegionConfidence.UNKNOWN || region.label == null) {
            return AvailabilityInfo(
                serviceId,
                com.materialmail.core.capability.ServiceAvailabilityStatus.UNKNOWN,
                "无法确定所在地区，因此无法确认该服务的可用性。",
            )
        }
        val entry = StaticAvailabilityDb.lookup(serviceId, region.label.country)
        // 低置信度降级：宁可说"无法确认"
        if (region.confidence == RegionConfidence.LOW &&
            entry.status != com.materialmail.core.capability.ServiceAvailabilityStatus.AVAILABLE
        ) {
            return AvailabilityInfo(
                serviceId,
                com.materialmail.core.capability.ServiceAvailabilityStatus.UNKNOWN,
                "地区推断不可靠（" + region.source + "），无法确认该服务的可用性。",
            )
        }
        return AvailabilityInfo(serviceId, entry.status, entry.detail)
    }
}