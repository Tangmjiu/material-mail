package com.materialmail.region.detection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialmail.region.model.ChinaRegionLabels
import com.materialmail.region.model.CommonRegionLabels
import com.materialmail.region.model.RegionConfidence
import com.materialmail.region.model.RegionLabel
import com.materialmail.region.model.RegionResult
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.regionStore by preferencesDataStore(name = "region")

/**
 * 地区检测（设计 §8 + 需求 §22/§23）：
 *
 * - 只用 Locale + TimeZone：零网络、零权限（IP 定位是未来的 opt-in，默认关）；
 * - 置信度显式建模：两源一致 = HIGH，单源 = MEDIUM，都没有 = UNKNOWN（绝不猜）；
 * - 与邮箱账户/邮件内容完全解耦：本模块不读任何邮件数据；
 * - 手动覆盖优先于自动检测（需求 §31），可一键恢复自动；
 * - 检测结果不进备份、不作为身份属性。
 */
class RegionDetector(private val context: Context) {

    private val manualOverrideKey = stringPreferencesKey("manual_region")

    /** 手动覆盖的地区名（RegionLabel.country），null = 自动检测。 */
    val manualOverride: Flow<String?> =
        context.regionStore.data.map { it[manualOverrideKey] }

    suspend fun setManualOverride(country: String?) {
        context.regionStore.edit {
            if (country == null) it.remove(manualOverrideKey) else it[manualOverrideKey] = country
        }
    }

    /** 当前生效的地区：手动覆盖优先。 */
    suspend fun currentRegion(): RegionResult {
        val override = manualOverride.first()
        if (override != null) {
            val label = CommonRegionLabels.ALL.firstOrNull { it.country == override }
            return if (label != null) {
                RegionResult(label, RegionConfidence.HIGH, "手动设置")
            } else {
                // 覆盖值不在常量表：视为无效并清除，回退自动检测
                setManualOverride(null)
                detectAutomatic()
            }
        }
        return detectAutomatic()
    }

    private fun detectAutomatic(): RegionResult {
        val localeCountry = Locale.getDefault().country.uppercase()
        val timezoneId = TimeZone.getDefault().id

        val fromLocale = mapLocaleCountry(localeCountry)
        val fromTimezone = mapTimezone(timezoneId)

        return when {
            fromLocale != null && fromTimezone != null && fromLocale.country == fromTimezone.country ->
                RegionResult(fromLocale, RegionConfidence.HIGH, "系统地区 + 时区一致")

            fromLocale != null && fromTimezone != null ->
                // 两源冲突：置信度降级，文案用"可能位于"
                RegionResult(fromLocale, RegionConfidence.LOW, "系统地区与时区不一致")

            fromLocale != null ->
                RegionResult(fromLocale, RegionConfidence.MEDIUM, "系统地区")

            fromTimezone != null ->
                RegionResult(fromTimezone, RegionConfidence.MEDIUM, "系统时区")

            else -> RegionResult(null, RegionConfidence.UNKNOWN, "无法确定所在地区")
        }
    }

    /** Locale 国家码 → 标签。只识别常量表内的映射，其余返回 null（→ UNKNOWN 路径）。 */
    private fun mapLocaleCountry(countryCode: String): RegionLabel? = when (countryCode) {
        "CN" -> ChinaRegionLabels.MAINLAND
        "TW" -> ChinaRegionLabels.TAIWAN
        "HK" -> ChinaRegionLabels.HONG_KONG
        "MO" -> ChinaRegionLabels.MACAU
        "SG" -> CommonRegionLabels.SINGAPORE
        "JP" -> CommonRegionLabels.JAPAN
        "US" -> CommonRegionLabels.UNITED_STATES
        else -> null
    }

    private fun mapTimezone(timezoneId: String): RegionLabel? = when (timezoneId) {
        "Asia/Shanghai", "Asia/Chongqing", "Asia/Urumqi" -> ChinaRegionLabels.MAINLAND
        "Asia/Taipei" -> ChinaRegionLabels.TAIWAN
        "Asia/Hong_Kong" -> ChinaRegionLabels.HONG_KONG
        "Asia/Macau" -> ChinaRegionLabels.MACAU
        "Asia/Singapore" -> CommonRegionLabels.SINGAPORE
        "Asia/Tokyo" -> CommonRegionLabels.JAPAN
        else -> null
    }
}