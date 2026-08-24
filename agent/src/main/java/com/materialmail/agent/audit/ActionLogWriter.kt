package com.materialmail.agent.audit

import com.materialmail.core.database.MaterialMailDatabase
import com.materialmail.core.database.dao.ActionLogDao
import com.materialmail.core.database.entity.ActionLogEntity
import com.materialmail.core.capability.AgentAction
import com.materialmail.core.capability.AgentCapability
import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class AuthorizationType {
    /** 只读类：能力授权内自动放行。 */
    CAPABILITY_GRANT,

    /** 用户在确认卡片上点了确认。 */
    USER_CONFIRMED,

    /** YOLO 模式授权范围内自动执行。 */
    YOLO,

    /** 被拒绝（未授权 / 用户取消 / 令牌校验失败）。 */
    DENIED,
}

enum class ActionResult { SUCCESS, FAILED, DENIED }

/** 审计输出抽象（Gate 只依赖它，Room 是实现细节）。 */
interface ActionLogSink {
    suspend fun log(
        agentName: String,
        action: AgentAction,
        authorization: AuthorizationType,
        result: ActionResult,
        connectorUsed: String? = null,
        error: String? = null,
    )
}

/** 审计写入器（设计 §6 铁律 3：每次执行都写，包括被拒绝和失败的）。 */
class ActionLogWriter(private val database: MaterialMailDatabase) : ActionLogSink {

    override suspend fun log(
        agentName: String,
        action: AgentAction,
        authorization: AuthorizationType,
        result: ActionResult,
        connectorUsed: String?,
        error: String?,
    ) {
        database.actionLogDao().insert(
            ActionLogEntity(
                timestampEpochMs = Instant.now().toEpochMilli(),
                agentName = agentName,
                capability = action.capability.name,
                riskLevel = action.risk.name,
                targetDescription = action.description,
                affectedCount = action.affectedCount,
                authorization = authorization.name,
                result = result.name,
                connectorUsed = connectorUsed,
                error = error,
            ),
        )
    }
}

/** 审计读取（设置页 ActionLog 界面后续阶段消费）。 */
class ActionLogReader(private val database: MaterialMailDatabase) {
    fun observeRecent(limit: Int = 200): Flow<List<ActionLogEntity>> =
        database.actionLogDao().observeRecent(limit)
}