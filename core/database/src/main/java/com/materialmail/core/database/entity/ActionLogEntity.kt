package com.materialmail.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Agent 操作审计日志（设计 §6 铁律 3：每一次执行都记录，
 * 包括被拒绝和失败的）。只增不改不删 —— 审计完整性。
 */
@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val agentName: String,
    val capability: String,
    val riskLevel: String,
    val targetDescription: String,
    val affectedCount: Int,
    /** CAPABILITY_GRANT / USER_CONFIRMED / YOLO / DENIED / FAILED */
    val authorization: String,
    val result: String,
    val connectorUsed: String?,
    val error: String?,
)