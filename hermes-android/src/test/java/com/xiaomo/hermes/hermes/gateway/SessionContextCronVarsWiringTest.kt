package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (2026-06-15)：`SessionContext.kt` 必须扩出 cron 自动投递专用的 ThreadLocal vars，
 * 让 `Scheduler.deliverResult` 能根据 cron 触发 origin 选目标 IM。
 *
 * 对齐 Python `gateway/session_context.py:61-63 set_cron_auto_deliver_vars` +
 * `:73-75 clear_cron_auto_deliver_vars`。
 *
 * **测试策略**：源码扫描 —— ThreadLocal 行为本身可纯 JVM 跑，但本测试只盯 wiring 字面值
 * （是否存在 / 是否对外暴露 setter+clearer），与 R-AGENT-033 其它 TC 一致风格。行为正确性
 * 由后续行为测 + §3 E2E + 手测兜底。
 *
 * 对应 TC-AGENT-033-c（见 docs/hermes-test-cases.md）。
 */
class SessionContextCronVarsWiringTest {

    private val source: String by lazy { File(sessionContextPath()).readText() }

    /**
     * TC-AGENT-033-c: 三个 HERMES_CRON_AUTO_DELIVER_* 字面值 + setter / clearer 函数声明。
     */
    @Test
    fun `TC-AGENT-033-c cron auto-deliver vars registered`() {
        // 三个字面值——既是 _VAR_MAP 的 key（保证 getSessionEnv 能读），也是 cron 自动投递路由依据
        assertTrue(
            "TC-AGENT-033-c: SessionContext.kt 必须含 `HERMES_CRON_AUTO_DELIVER_PLATFORM` 字面值 —— " +
                "对齐 Python `session_context.py:61` 的 `HERMES_CRON_AUTO_DELIVER_PLATFORM`。",
            source.contains("HERMES_CRON_AUTO_DELIVER_PLATFORM")
        )
        assertTrue(
            "TC-AGENT-033-c: SessionContext.kt 必须含 `HERMES_CRON_AUTO_DELIVER_CHAT_ID` 字面值。",
            source.contains("HERMES_CRON_AUTO_DELIVER_CHAT_ID")
        )
        assertTrue(
            "TC-AGENT-033-c: SessionContext.kt 必须含 `HERMES_CRON_AUTO_DELIVER_THREAD_ID` 字面值。",
            source.contains("HERMES_CRON_AUTO_DELIVER_THREAD_ID")
        )

        // setter / clearer 函数声明
        assertTrue(
            "TC-AGENT-033-c: SessionContext.kt 必须含 `fun setCronAutoDeliverVars(` 函数声明 —— " +
                "对齐 Python `session_context.py:61-63 set_cron_auto_deliver_vars`。",
            Regex("""\bfun\s+setCronAutoDeliverVars\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "TC-AGENT-033-c: SessionContext.kt 必须含 `fun clearCronAutoDeliverVars(` 函数声明 —— " +
                "对齐 Python `session_context.py:73-75 clear_cron_auto_deliver_vars`。",
            Regex("""\bfun\s+clearCronAutoDeliverVars\s*\(""").containsMatchIn(source)
        )
    }

    // ----- helpers -----

    private fun sessionContextPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/SessionContext.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/SessionContext.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate SessionContext.kt — cwd=${File(".").absolutePath}")
    }
}
