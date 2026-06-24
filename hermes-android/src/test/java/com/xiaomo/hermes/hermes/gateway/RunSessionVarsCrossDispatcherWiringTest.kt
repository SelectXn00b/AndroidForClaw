package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (continuation): Bug A 的 fix 不完整。`Run.kt::_handleMessage` 在 R-AGENT-033 初版
 * 已经调用了 `setSessionVars(platform = event.source.platform, ...)`（TC-AGENT-033-a 覆盖），但
 * 仅靠 ThreadLocal.set 不够：下游 agent loop 在 `withContext(Dispatchers.IO)` 切线程后，
 * ThreadLocal 在新线程上读不到值 —— `CronjobTools._originFromEnv()` 仍然拿空字符串，`origin`
 * 仍然 null，`effectiveDeliver` 退化成 "local"，微信永远收不到 cron 触发的回复。
 *
 * 修复办法：参考 R-AGENT-045 在 `SessionContext.kt:187-199` 给出的 `sessionContextElement()`
 * helper（基于 `ThreadLocal<T>.asContextElement(value)`，Kotlin coroutines 对 Python contextvars
 * 的等价实现），把 `setSessionVars(...)` 之后的整个 try 块下游用 `withContext(sessionContextElement()) { ... }`
 * 包住，这样 ThreadLocal 的值跟随协程上下文跨线程传播。
 *
 * 对齐 Python `gateway/run.py`：Python 用 `copy_context().run(...)` 让 ContextVar 跨 task 传播，
 * Kotlin 这里用 `withContext(sessionContextElement())` 是同一个意图。
 *
 * **测试策略**：源码扫描 —— 协程跨线程 ThreadLocal 传播在纯 JVM 单测里很难精准复现（涉及
 * `Dispatchers.IO` 真实线程池），与 R-AGENT-031 / R-AGENT-033-a 同策略走字面值断言；实际行为
 * 由 §3 E2E + 手测（"在微信里让 agent 创建定时任务 → 等到点 → 微信收到"）兜底。
 *
 * 对应 TC-AGENT-033-i（见 docs/hermes-test-cases.md）。
 */
class RunSessionVarsCrossDispatcherWiringTest {

    private val source: String by lazy { File(runKtPath()).readText() }

    /** 抓 `_handleMessage` 函数体（从签名行起到下一个同级 fun 声明前）。 */
    private fun extractHandleMessageBody(): String {
        val anchor = source.indexOf("private suspend fun _handleMessage(")
            .takeIf { it >= 0 }
            ?: source.indexOf("suspend fun _handleMessage(")
            .takeIf { it >= 0 }
            ?: return ""
        val rest = source.substring(anchor)
        val endRegex = Regex("""\n\s+(?:private\s+|internal\s+|public\s+)?(?:suspend\s+)?fun\s+\w+\s*\(""")
        val m = endRegex.find(rest, startIndex = "private suspend fun _handleMessage(".length)
        return if (m != null) rest.substring(0, m.range.first) else rest
    }

    /**
     * TC-AGENT-033-i: `_handleMessage` 在 `setSessionVars(...)` 之后必须用
     * `withContext(sessionContextElement()) { ... }` 包住下游，保证 ThreadLocal 跨线程传播。
     */
    @Test
    fun `TC-AGENT-033-i _handleMessage wraps downstream in sessionContextElement`() {
        val body = extractHandleMessageBody()
        assertTrue(
            "TC-AGENT-033-i: 找不到 `_handleMessage` 函数体 —— 结构可能被改。",
            body.isNotEmpty()
        )

        // (1) 必须出现 `withContext(sessionContextElement(` 字面值
        val withCtxPattern = Regex("""\bwithContext\s*\(\s*sessionContextElement\s*\(""")
        assertTrue(
            "TC-AGENT-033-i 红线: `_handleMessage` 必须含 `withContext(sessionContextElement()` 调用 —— " +
                "对齐 R-AGENT-045 的 helper，否则 setSessionVars 写入的 ThreadLocal 一旦协程切到 " +
                "`Dispatchers.IO` 就丢失，下游 `CronjobTools._originFromEnv` 拿空字符串，origin=null，" +
                "deliver 退化成 local，微信永远收不到 cron 回复。\n" +
                "实际函数体:\n${body.take(4000)}",
            withCtxPattern.containsMatchIn(body)
        )

        // (2) 顺序：setSessionVars(...) -> withContext(sessionContextElement(...)) -> 下游 (agent loop / process / handle)
        val setIdx = Regex("""\bsetSessionVars\s*\(""").find(body)?.range?.first ?: -1
        val wrapIdx = withCtxPattern.find(body)?.range?.first ?: -1
        assertTrue(
            "TC-AGENT-033-i: `setSessionVars(` 必须出现在 `withContext(sessionContextElement(` 之前 " +
                "(setIdx=$setIdx, wrapIdx=$wrapIdx) —— 否则包裹时 ThreadLocal 还没设值，没意义。\n" +
                "实际函数体:\n${body.take(4000)}",
            setIdx in 0 until wrapIdx
        )

        // (3) 包裹的 withContext 之后必须还有下游调用（agent runner / deliveryRouter / sessionStore）
        //     这一条保证不是"写个 withContext 空壳但不真用"，下游确实在里面。
        val downstreamPatterns = listOf(
            "runner(",
            "deliveryRouter.deliverText",
            "sessionStore.getOrCreate",
            "hookPipeline.run"
        )
        val downstreamFound = downstreamPatterns.any { lit ->
            val idx = body.indexOf(lit)
            idx > wrapIdx
        }
        assertTrue(
            "TC-AGENT-033-i: `withContext(sessionContextElement(...))` 之后必须真包住下游 " +
                "(runner / deliveryRouter / sessionStore 之类) —— 找不到任何下游 keyword 在它之后，" +
                "说明包裹位置错了或下游没塞进去。\n" +
                "实际函数体:\n${body.take(4000)}",
            downstreamFound
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
