package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (2026-06-15) Bug A: `Run.kt::_handleMessage` 必须给 ThreadLocal session vars
 * 注入入站事件来源（platform / chatId / threadId / userId / userName / sessionKey），并在 finally
 * 块清掉。否则 `getSessionEnv("HERMES_SESSION_*")` 整条链路都拿不到值——`CronjobTools._originFromEnv`
 * 会一直返回 null，cron job 从 IM 创建后 `origin` 永远丢，后续 `deliver=origin` 投不出去。
 *
 * 对齐 Python `gateway/run.py:3964 _set_session_env` + `:4772 _clear_session_env`。
 *
 * **测试策略**：源码扫描 —— `_handleMessage` 内含 GatewayRunner / 协程 / suspend，纯 JVM 单测
 * 难起，与 R-AGENT-031 同策略走字面值断言；ThreadLocal 行为正确性由 §3 E2E + 手测兜底。
 *
 * 对应 TC-AGENT-033-a / TC-AGENT-033-b（见 docs/hermes-test-cases.md）。
 */
class RunSessionVarsWiringTest {

    private val source: String by lazy { File(runKtPath()).readText() }

    /** 抓 `_handleMessage` 函数体（从签名行起到下一个同级 fun 声明前）。 */
    private fun extractHandleMessageBody(): String {
        val anchor = source.indexOf("private suspend fun _handleMessage(")
            .takeIf { it >= 0 }
            ?: source.indexOf("suspend fun _handleMessage(")
            .takeIf { it >= 0 }
            ?: return ""
        val rest = source.substring(anchor)
        // 找下一个 fun 声明作为终点（保险用 `\n    private` 缩进锚定方法级别）
        val endRegex = Regex("""\n\s+(?:private\s+|internal\s+|public\s+)?(?:suspend\s+)?fun\s+\w+\s*\(""")
        val m = endRegex.find(rest, startIndex = "private suspend fun _handleMessage(".length)
        return if (m != null) rest.substring(0, m.range.first) else rest
    }

    /**
     * TC-AGENT-033-a: `_handleMessage` 必须 setSessionVars(platform=..., chatId=..., threadId=...)。
     */
    @Test
    fun `TC-AGENT-033-a _handleMessage sets session vars from event source`() {
        val body = extractHandleMessageBody()
        assertTrue(
            "Run.kt 必须含 `_handleMessage` 函数声明 —— 找不到说明结构被改。",
            body.isNotEmpty()
        )

        assertTrue(
            "TC-AGENT-033-a: `_handleMessage` 函数体必须含 `setSessionVars(` 调用 —— " +
                "对齐 Python `gateway/run.py:3964 _set_session_env`，否则下游 getSessionEnv 全拿空。\n" +
                "实际函数体:\n${body.take(4000)}",
            Regex("""\bsetSessionVars\s*\(""").containsMatchIn(body)
        )

        // 参数串必须引用 event.source.platform / chatId / threadId
        assertTrue(
            "TC-AGENT-033-a: setSessionVars(...) 参数必须引用 `event.source.platform` —— " +
                "否则平台名注不进 ThreadLocal，CronjobTools._originFromEnv 拿不到 platform。\n" +
                "实际函数体:\n${body.take(4000)}",
            body.contains("event.source.platform")
        )
        assertTrue(
            "TC-AGENT-033-a: setSessionVars(...) 参数必须引用 `event.source.chatId` —— " +
                "否则 chatId 注不进 ThreadLocal。\n实际函数体:\n${body.take(4000)}",
            body.contains("event.source.chatId")
        )
        assertTrue(
            "TC-AGENT-033-a: setSessionVars(...) 参数必须引用 `event.source.threadId` —— " +
                "否则 Telegram thread 路由信息全丢。\n实际函数体:\n${body.take(4000)}",
            body.contains("event.source.threadId")
        )
    }

    /**
     * TC-AGENT-033-b: `clearSessionVars()` 必须出现在 finally 块内（红线：避免协程切线程残留 ThreadLocal）。
     */
    @Test
    fun `TC-AGENT-033-b clearSessionVars called in finally block`() {
        val body = extractHandleMessageBody()
        assertTrue("找不到 `_handleMessage` 函数体。", body.isNotEmpty())

        assertTrue(
            "TC-AGENT-033-b: `_handleMessage` 必须含 `clearSessionVars(` 调用 —— " +
                "对齐 Python `gateway/run.py:4772 _clear_session_env`。\n实际函数体:\n${body.take(4000)}",
            Regex("""\bclearSessionVars\s*\(""").containsMatchIn(body)
        )

        // finally { ... clearSessionVars ... } 跨行 regex（500 字符内出现 clearSessionVars）
        val finallyClearPattern = Regex(
            """finally\s*\{[\s\S]{0,800}?\bclearSessionVars\s*\("""
        )
        assertTrue(
            "TC-AGENT-033-b 红线: `clearSessionVars()` 必须位于 `finally { ... }` 块内 —— " +
                "否则协程异常 / 取消时 ThreadLocal 残留，下一个事件可能复用上一个事件的 session vars。\n" +
                "实际函数体:\n${body.take(4000)}",
            finallyClearPattern.containsMatchIn(body)
        )
    }

    // ----- helpers -----

    private fun runKtPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Run.kt — cwd=${File(".").absolutePath}")
    }
}
