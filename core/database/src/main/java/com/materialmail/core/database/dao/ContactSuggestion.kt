package com.materialmail.core.database.dao

/** 联系人联想查询的投影行。 */
data class ContactSuggestion(
    val fromAddress: String,
    val fromJson: String,
)