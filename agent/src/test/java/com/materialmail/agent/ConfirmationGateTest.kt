package com.materialmail.agent

import com.materialmail.agent.audit.ActionLogSink
import com.materialmail.agent.audit.ActionResult
import com.materialmail.agent.audit.AuthorizationType
import com.materialmail.agent.execution.AgentResult
import com.materialmail.agent.execution.ConfirmationGate
import com.materialmail.agent.execution.ConfirmationTokenIssuer
import com.materialmail.agent.permissions.CapabilityGrantChecker
import com.materialmail.core.capability.AgentAction
import com.materialmail.core.capability.AgentCapability
import com.materialmail.core.capability.ConfirmationToken
import com.materialmail.core.capability.RiskLevel
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 确认协议不可绕过的机器证明（设计 §6/§45）。
 * 这组测试是 Agent 地基的验收标准：任何重构导致它们变绿 → 红，即为架构事故。
 */
class ConfirmationGateTest {

    // ── 测试替身 ────────────────────────────────────────────

    private data class LogEntry(
        val authorization: AuthorizationType,
        val result: ActionResult,
        val capability: AgentCapability,
    )

    private class FakeAudit : ActionLogSink {
        val entries = CopyOnWriteArrayList<LogEntry>()

        override suspend fun log(
            agentName: String,
            action: AgentAction,
            authorization: AuthorizationType,
            result: ActionResult,
            connectorUsed: String?,
            error: String?,
        ) {
            entries += LogEntry(authorization, result, action.capability)
        }
    }

    private fun gate(
        granted: Set<AgentCapability>,
        audit: FakeAudit = FakeAudit(),
        issuer: ConfirmationTokenIssuer = ConfirmationTokenIssuer(),
        yolo: com.materialmail.agent.execution.YoloStateProvider =
            com.materialmail.agent.execution.InactiveYoloProvider,
    ): ConfirmationGate = ConfirmationGate(
        permissionStore = CapabilityGrantChecker { it in granted },
        tokenIssuer = issuer,
        audit = audit,
        yolo = yolo,
    )

    private fun action(
        capability: AgentCapability,
        risk: RiskLevel,
        permanent: Boolean = false,
    ) = AgentAction(
        capability = capability,
        risk = risk,
        description = "test",
        affectedCount = 1,
        permanent = permanent,
    )

    private val alwaysConfirm = com.materialmail.agent.execution.ConfirmationRequester { true }
    private val alwaysDeny = com.materialmail.agent.execution.ConfirmationRequester { false }

    // ── 铁律 1：确认协议 ─────────────────────────────────────

    @Test
    fun `读取类能力在授权内直接执行`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.READ), audit = audit)
        val result = g.execute("agent", action(AgentCapability.READ, RiskLevel.READ_ONLY), alwaysDeny) { 42 }
        assertTrue(result is AgentResult.Success)
        assertEquals(42, (result as AgentResult.Success).value)
        assertEquals(AuthorizationType.CAPABILITY_GRANT, audit.entries.single().authorization)
    }

    @Test
    fun `能力未授权时拒绝且不执行`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = emptySet(), audit = audit)
        val result = g.execute("agent", action(AgentCapability.SEARCH, RiskLevel.READ_ONLY), alwaysConfirm) {
            fail("未授权时执行块绝不能被调用")
        }
        assertEquals(AgentResult.CapabilityNotGranted, result)
        assertEquals(ActionResult.DENIED, audit.entries.single().result)
    }

    @Test
    fun `发送类必须确认 - 用户拒绝则不执行`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.SEND), audit = audit)
        val result = g.execute("agent", action(AgentCapability.SEND, RiskLevel.SEND), alwaysDeny) {
            fail("用户拒绝后执行块绝不能被调用")
        }
        assertEquals(AgentResult.UserDenied, result)
        assertEquals(ActionResult.DENIED, audit.entries.single().result)
    }

    @Test
    fun `发送类确认后执行并记 USER_CONFIRMED`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.SEND), audit = audit)
        var executed = false
        val result = g.execute("agent", action(AgentCapability.SEND, RiskLevel.SEND), alwaysConfirm) {
            executed = true
        }
        assertTrue(result is AgentResult.Success)
        assertTrue(executed)
        assertEquals(AuthorizationType.USER_CONFIRMED, audit.entries.single().authorization)
    }

    @Test
    fun `执行失败记 FAILED 且不静默`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.ARCHIVE), audit = audit)
        val result = g.execute<Unit>(
            "agent", action(AgentCapability.ARCHIVE, RiskLevel.MODIFY), alwaysConfirm,
        ) { error("disk full") }
        assertTrue(result is AgentResult.Failed)
        assertEquals(ActionResult.FAILED, audit.entries.single().result)
    }

    // ── 永久删除硬确认（需求 §45）────────────────────────────

    @Test
    fun `永久删除在 YOLO 全授权下仍强制确认`() = runTest {
        val yoloAll = object : com.materialmail.agent.execution.YoloStateProvider {
            override val active = true
            override fun allowsAutonomous(c: AgentCapability, r: RiskLevel) = true
        }
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.DELETE), audit = audit, yolo = yoloAll)
        var executed = false
        // 用户拒绝 → 即使 YOLO 全开也不能执行
        val result = g.execute(
            "agent", action(AgentCapability.DELETE, RiskLevel.DELETE, permanent = true), alwaysDeny,
        ) { executed = true }
        assertEquals(AgentResult.UserDenied, result)
        assertFalse(executed)
    }

    // ── 令牌协议（设计 §7）──────────────────────────────────

    @Test
    fun `令牌一次性 - 复用被拒绝`() = runTest {
        val issuer = ConfirmationTokenIssuer()
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.CONNECTOR_USE), audit = audit, issuer = issuer)
        val fp = ConfirmationTokenIssuer.fingerprintOf("feishu", "msg-1")
        val token = g.confirmAndIssueToken(
            "agent", action(AgentCapability.CONNECTOR_USE, RiskLevel.SEND), fp, alwaysConfirm,
        )!!
        assertTrue(g.consumeToken("agent", action(AgentCapability.CONNECTOR_USE, RiskLevel.SEND), token, fp, "feishu"))
        // 第二次使用同一令牌：必须失败
        assertFalse(g.consumeToken("agent", action(AgentCapability.CONNECTOR_USE, RiskLevel.SEND), token, fp, "feishu"))
    }

    @Test
    fun `伪造令牌被拒绝并记审计`() = runTest {
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.CONNECTOR_USE), audit = audit)
        val forged = ConfirmationToken("forged-token")
        val fp = ConfirmationTokenIssuer.fingerprintOf("feishu", "msg-1")
        assertFalse(
            g.consumeToken("agent", action(AgentCapability.CONNECTOR_USE, RiskLevel.SEND), forged, fp, "feishu"),
        )
        assertEquals(ActionResult.DENIED, audit.entries.single().result)
    }

    @Test
    fun `令牌绑定指纹 - 张冠李戴被拒绝`() = runTest {
        var now = 0L
        val issuer = ConfirmationTokenIssuer(nowMs = { now })
        val fp1 = ConfirmationTokenIssuer.fingerprintOf("feishu", "msg-1")
        val fp2 = ConfirmationTokenIssuer.fingerprintOf("feishu", "msg-2")
        val token = issuer.issue(fp1)
        assertFalse(issuer.validateAndConsume(token, fp2))
    }

    @Test
    fun `令牌过期被拒绝`() {
        var now = 1_000L
        val issuer = ConfirmationTokenIssuer(nowMs = { now })
        val fp = ConfirmationTokenIssuer.fingerprintOf("x")
        val token = issuer.issue(fp, validityMs = 500)
        now = 2_000L
        assertFalse(issuer.validateAndConsume(token, fp))
    }

    @Test
    fun `YOLO 授权集内自动执行并记 YOLO`() = runTest {
        val yoloPartial = object : com.materialmail.agent.execution.YoloStateProvider {
            override val active = true
            override fun allowsAutonomous(c: AgentCapability, r: RiskLevel) =
                c == AgentCapability.ARCHIVE && r == RiskLevel.MODIFY
        }
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.ARCHIVE), audit = audit, yolo = yoloPartial)
        val result = g.execute("agent", action(AgentCapability.ARCHIVE, RiskLevel.MODIFY), alwaysDeny) { "ok" }
        assertTrue(result is AgentResult.Success)
        assertEquals(AuthorizationType.YOLO, audit.entries.single().authorization)
    }

    @Test
    fun `YOLO 授权集外仍走确认`() = runTest {
        val yoloPartial = object : com.materialmail.agent.execution.YoloStateProvider {
            override val active = true
            override fun allowsAutonomous(c: AgentCapability, r: RiskLevel) =
                c == AgentCapability.ARCHIVE
        }
        val audit = FakeAudit()
        val g = gate(granted = setOf(AgentCapability.SEND), audit = audit, yolo = yoloPartial)
        val result = g.execute("agent", action(AgentCapability.SEND, RiskLevel.SEND), alwaysDeny) {
            fail("YOLO 集外的 SEND 必须走确认")
        }
        assertEquals(AgentResult.UserDenied, result)
    }

    @Test
    fun `confirmAndIssueToken 能力未授权不签发`() = runTest {
        val g = gate(granted = emptySet())
        val token = g.confirmAndIssueToken(
            "agent", action(AgentCapability.CONNECTOR_USE, RiskLevel.SEND),
            "fp", alwaysConfirm,
        )
        assertNull(token)
    }
}