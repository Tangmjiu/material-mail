package com.materialmail.core.capability

/**
 * 服务可用性状态。UNKNOWN 严格区别于 UNAVAILABLE：
 * 无法确认时必须如实告知"无法确认"，绝不猜测。
 */
enum class ServiceAvailabilityStatus {
    AVAILABLE,
    LIMITED,
    UNKNOWN,
    UNAVAILABLE,
}

data class AvailabilityInfo(
    val serviceId: String,
    val status: ServiceAvailabilityStatus,
    /** 面向用户的说明文案 key / 简短描述。 */
    val detail: String? = null,
)

/**
 * Core 对 region 模块的唯一接触点。
 * region 模块存在时替换默认实现；删除 region 模块后 App 行为 = 永不提示。
 */
interface ServiceAvailabilityChecker {
    suspend fun check(serviceId: String): AvailabilityInfo
}

/** 默认实现：一切可用。编译产物中 region 缺席时就是它。 */
class AllowAllAvailabilityChecker : ServiceAvailabilityChecker {
    override suspend fun check(serviceId: String): AvailabilityInfo =
        AvailabilityInfo(serviceId, ServiceAvailabilityStatus.AVAILABLE)
}