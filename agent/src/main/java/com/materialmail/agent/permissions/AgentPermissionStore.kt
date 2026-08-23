package com.materialmail.agent.permissions

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.materialmail.core.capability.AgentCapability
import kotlinx.coroutines.flow.first

private val Context.agentPermissionStore by preferencesDataStore(name = "agent_permissions")

/**
 * Agent 能力授权存储（设计 §6：能力必须经权限控制）。
 *
 * Community 默认集：READ / SEARCH / SUMMARIZE 开启（只读类），
 * 其余一律默认关闭，由用户在 UI 中逐项授权。
 * 注意区分：这里是"Agent 是否被允许拥有该能力"，
 * 与"执行时是否需要确认"（ConfirmationGate 按风险分级决定）是两层。
 */
class AgentPermissionStore(private val context: Context) : CapabilityGrantChecker {

    private fun key(capability: AgentCapability) =
        booleanPreferencesKey("cap_" + capability.name.lowercase())

    override suspend fun isGranted(capability: AgentCapability): Boolean =
        context.agentPermissionStore.data.first()[key(capability)]
            ?: (capability in DEFAULT_GRANTED)

    suspend fun setGranted(capability: AgentCapability, granted: Boolean) {
        context.agentPermissionStore.edit { it[key(capability)] = granted }
    }

    suspend fun grantedCapabilities(): Set<AgentCapability> =
        AgentCapability.entries.filter { isGranted(it) }.toSet()

    companion object {
        val DEFAULT_GRANTED = setOf(
            AgentCapability.READ,
            AgentCapability.SEARCH,
            AgentCapability.SUMMARIZE,
        )
    }
}