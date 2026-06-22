package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-045 跨 share() / shareRevisable() 边界修：
 *
 * `AIMessageManager.kt` 顶部声明的 `private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())`
 * 是**静态**的，process 级单例。`sendMessage()` 把 `EnhancedAIService.sendMessage(...)`
 * 返回的 cold `stream { }` 块通过 `.share(scope = scope, ...)` / `.shareRevisable(scope = scope, ...)`
 * 派给一条 upstream 收集协程：内部实现见 `util/stream/HotStream.kt:316`：
 *
 *   ```kotlin
 *   upstreamJob = scope.launch {
 *       this@share.collect { ... }   // ← cold stream 的 producer 块在这里跑
 *   }
 *   ```
 *
 * 这是**裸 launch**（不带 caller 的 ThreadContextElement），**不**继承 caller 当前
 * `withContext(sessionContextElement())` 包的 ThreadLocal 快照——session vars 断在这里。
 * cold `stream { }` 块里跑的是 agent loop、tool dispatch、`CronjobTools._originFromEnv()`，
 * 这些代码在没 session ThreadLocal 的线程上读 `getSessionEnv(...)` → 空 →
 * `_originFromEnv()` 返回 null → jobs.json `origin` 字段为 null。
 *
 * 修法（Option A1）：派生一个生命周期跟静态 scope 一致但带本次请求 session 快照的子 scope：
 *
 *   ```kotlin
 *   scope = CoroutineScope(scope.coroutineContext + sessionContextElement())
 *   ```
 *
 * `sessionContextElement()` 在调用点同步读当前线程的 ThreadLocal 当下值
 * （此时 caller 已通过 `MessageProcessingDelegate.kt:521` `coroutineScope.launch(Dispatchers.IO + sessionContextElement())`
 * 把快照带到了 IO 线程，所以这一刻线程上确实有 platform="app" + chat_id），
 * `asContextElement` 把这份快照绑到新派生的 scope 上，scope.launch 拉的 IO 线程
 * `updateThreadContext` 时再把值塞到目标线程的 ThreadLocal。
 *
 * 1:1 对齐 Python `copy_context().run(func)`（`reference/hermes-agent/gateway/run.py:8108-8112`）
 * 在派生新 task / threadpool work item 时立即捕获 contextvars 快照的语义。
 *
 * 对应 TC-AGENT-045-i-3。
 */
class AIMessageManagerSessionContextTest {

    private val source: String by lazy { File(managerPath()).readText() }

    /**
     * TC-AGENT-045-i-3: 两处 share / shareRevisable 调用必须用
     * `CoroutineScope(scope.coroutineContext + sessionContextElement())` 而不是裸 `scope`。
     */
    @Test
    fun `TC-AGENT-045-i-3 wraps share scope with sessionContextElement`() {
        // 1) 必须 import sessionContextElement
        assertTrue(
            "AIMessageManager.kt 必须 import `sessionContextElement` —— " +
                "R-AGENT-045 跨 share() 边界 origin 传播 helper（等价 Python copy_context().run）。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.sessionContextElement""")
                .containsMatchIn(source)
        )

        // 2) 计数：原本两处 `scope = scope,` 必须**全部**改成
        //    `scope = CoroutineScope(scope.coroutineContext + sessionContextElement())`。
        //    残留任意一处裸 `scope = scope,` 即视为 wiring 不完整。
        val bareSharePattern = Regex("""(?<![A-Za-z0-9_])scope\s*=\s*scope\s*,""")
        val bareCount = bareSharePattern.findAll(source).count()
        assertTrue(
            "AIMessageManager.kt 中 `scope = scope,` 字面残留 $bareCount 处 —— " +
                "share() / shareRevisable() 必须用 `CoroutineScope(scope.coroutineContext + sessionContextElement())` " +
                "包，否则 cold stream producer 在没 session ThreadLocal 的线程上跑 → jobs.json origin = null。",
            bareCount == 0
        )

        // 3) 必须出现至少 2 处 session-aware scope wrapping —— 对应 plugin 路径 + 默认路径
        val wrapPattern = Regex(
            """CoroutineScope\s*\(\s*scope\.coroutineContext\s*\+\s*sessionContextElement\s*\(\s*\)\s*\)"""
        )
        val wrapCount = wrapPattern.findAll(source).count()
        assertTrue(
            "AIMessageManager.kt 中 `CoroutineScope(scope.coroutineContext + sessionContextElement())` " +
                "至少要出现 2 处（plugin 路径 + 默认路径），实际 $wrapCount 处。",
            wrapCount >= 2
        )

        // 4) Wrap 必须出现在 sendMessage() 函数体内 —— 简单校验：source 含 `fun sendMessage(`
        //    且每个 wrap 距离最近的 sendMessage / share / shareRevisable 关键词不远（800 字符内）。
        //    这是宽松的"位置正确性"守卫，避免 wrap 挂到完全无关的位置。
        val wrapMatches = wrapPattern.findAll(source).toList()
        for (m in wrapMatches) {
            val window = 800
            val headStart = (m.range.first - window).coerceAtLeast(0)
            val tailEnd = (m.range.last + window).coerceAtMost(source.length)
            val context = source.substring(headStart, tailEnd)
            assertTrue(
                "session-aware scope wrap (source[${m.range.first}..${m.range.last}]) " +
                    "周围 ${window} 字符内必须含 `share(` 或 `shareRevisable(` 或 `sendMessage` " +
                    "—— 守 wrap 不被挂到无关位置。",
                context.contains("share(") ||
                    context.contains("shareRevisable(") ||
                    context.contains("sendMessage")
            )
        }
    }

    private fun managerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/chat/AIMessageManager.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/chat/AIMessageManager.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate AIMessageManager.kt — cwd=${File(".").absolutePath}")
    }
}
