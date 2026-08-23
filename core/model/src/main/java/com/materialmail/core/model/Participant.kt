package com.materialmail.core.model

import kotlinx.serialization.Serializable

/** 邮件参与者（发件人 / 收件人 / 抄送……）。 */
@Serializable
data class Participant(
    val address: String,
    val name: String? = null,
) {
    /** 展示名：有名字用名字，否则退化为地址本地部分。 */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: address.substringBefore('@')
}