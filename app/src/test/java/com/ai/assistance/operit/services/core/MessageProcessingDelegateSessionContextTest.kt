package com.ai.assistance.operit.services.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-045 跨 service-scope launch 边界修：
 * `MessageProcessingDelegate.sendUserMessage` 内部 fire-and-forget 启动
 * `coroutineScope.launch(Dispatchers.IO) { ... }` 跑 agent loop。这条 launch
 * 是 service scope（`ChatServiceCore.serviceScope`），**不**继承 caller 的
 * `CoroutineContext`——纯靠上游 `ExternalChatRequestExecutor.execute()` 的
 * `withContext(sessionContextElement())` 包裹只能管到当前协程内部，跨过
 * service-scope `launch` 边界后 ThreadLocal 仍然丢，下游 `_originFromEnv()`
 * 拿到空 → jobs.json `origin` = null。
 *
 * 修法：launch context 上 `+ sessionContextElement()` —— launch 调用点
 * 同步读当前线程的 ThreadLocal 快照，绑到新协程的 CoroutineContext。
 * 1:1 对齐 Python `copy_context().run(func)`（`reference/hermes-agent/gateway/run.py:8108-8112`）
 * 在派生新 task / threadpool work item 时立即捕获 contextvars 快照的语义。
 *
 * 源码扫描守 wiring，配合 `SessionContextElementTest#TC-AGENT-045-i-2` 的
 * 行为测试（实际跑一个 launch 验证 ThreadLocal 真的随快照过去）。
 *
 * 对应 TC-AGENT-045-i-1。
 */
class MessageProcessingDelegateSessionContextTest {

    private val source: String by lazy { File(delegatePath()).readText() }

    /**
     * TC-AGENT-045-i-1: `coroutineScope.launch(Dispatchers.IO + sessionContextElement()) { ... }`
     * 必须出现在 sendUserMessage() 中负责 dispatch agent loop 的 launch 调用上。
     */
    @Test
    fun `TC-AGENT-045-i-1 launches sendJob with sessionContextElement`() {
        // 1) 必须 import sessionContextElement
        assertTrue(
            "MessageProcessingDelegate.kt 必须 import `sessionContextElement` —— " +
                "R-AGENT-045 跨 service-scope launch 边界 origin 传播 helper " +
                "（等价 Python copy_context().run）。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.sessionContextElement""")
                .containsMatchIn(source)
        )

        // 2) 必须有 `coroutineScope.launch(Dispatchers.IO + sessionContextElement())` 模式
        //    在 sendJob 赋值附近——容忍空格、换行
        val launchPattern = Regex(
            """coroutineScope\.launch\s*\(\s*Dispatchers\.IO\s*\+\s*sessionContextElement\s*\(\s*\)\s*\)"""
        )
        assertTrue(
            "MessageProcessingDelegate.kt 中 sendUserMessage 内部的 " +
                "`coroutineScope.launch(Dispatchers.IO) { ... }` 必须改成 " +
                "`coroutineScope.launch(Dispatchers.IO + sessionContextElement()) { ... }` —— " +
                "service-scope launch 不继承 caller CoroutineContext，没这个 `+` " +
                "ThreadLocal 快照（含 platform=\"app\" + chat_id）跨不过 launch 边界，" +
                "下游 `_originFromEnv()` 拿到空 → jobs.json origin = null。",
            launchPattern.containsMatchIn(source)
        )

        // 3) 旧的不带 `+ sessionContextElement()` 的 `coroutineScope.launch(Dispatchers.IO)`
        //    在主 sendJob 这条上必须**不再**单独出现（语义上回归保险）。
        //    具体方法：sendJob 赋值附近 800 字符内必须含 sessionContextElement 引用。
        val sendJobIdx = Regex("""val\s+sendJob\s*=""").find(source)?.range?.first ?: -1
        assertTrue(
            "MessageProcessingDelegate.kt 必须含 `val sendJob =` 赋值（agent dispatch 的入口）。",
            sendJobIdx >= 0
        )
        val nearby = source.substring(sendJobIdx, (sendJobIdx + 800).coerceAtMost(source.length))
        assertTrue(
            "sendJob 赋值附近 800 字符内必须含 `sessionContextElement` —— " +
                "证明 launch context 上确实 `+` 了快照，没回归成裸 `Dispatchers.IO`。",
            nearby.contains("sessionContextElement")
        )
    }

    /**
     * TC-AGENT-045-i-7: launch 块**第一行**必须把 origin 显式参数重写回 ThreadLocal。
     *
     * 即使 launch context 上有 `+ sessionContextElement()`（TC-i-1），那个快照
     * 也只能携带"调用 launch 的那一刻当前线程上 ThreadLocal 的值"。但 caller 链
     * 里 `MessageCoordinationDelegate.sendUserMessage` 的 chatId-blank 分支会
     * `coroutineScope.launch { sendMessageInternal(...) }` 派生新协程，那次
     * 派生**不**继承上游的 sessionContextElement —— 等到 `sendMessageInternal` 调到
     * `messageProcessingDelegate.sendUserMessage` 时，当前线程的 session ThreadLocal
     * 已经空了，line ~530 的 `+ sessionContextElement()` 拍下来的也是空快照。
     *
     * C-route 的修法：通过新增的 `originPlatformOverride` / `originChatIdOverride`
     * 显式参数（4 层管道，TC-i-6）把 origin 一路顺到 `MessageProcessingDelegate.sendUserMessage`，
     * 再在 line ~530 launch 块**进入后第一行**调 `setSessionVars(...)` /
     * `setCronAutoDeliverVars(...)` 把它们重写回 launch 内线程的 ThreadLocal。
     * 同时 finally 里调 `clearSessionVars()` / `clearCronAutoDeliverVars()` 防泄漏。
     *
     * 这样下游 `AIMessageManager` cold stream / agent loop / `_originFromEnv()` 都能读到。
     */
    @Test
    fun `TC-AGENT-045-i-7 writes session vars from origin params inside launch`() {
        // 1) 必须 import 4 个 R-AGENT-033 ThreadLocal API
        for (sym in listOf(
            "setSessionVars", "setCronAutoDeliverVars",
            "clearSessionVars", "clearCronAutoDeliverVars"
        )) {
            assertTrue(
                "MessageProcessingDelegate.kt 必须 import `$sym` —— " +
                    "C-route launch 边界另一侧重写 ThreadLocal。",
                Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.$sym""").containsMatchIn(source)
            )
        }

        // 2) `val sendJob =` 赋值后的 launch 块第一段（≤ 800 字符）必须含
        //    `setSessionVars(platform = originPlatformOverride` 调用 —— 用显式参数
        //    重写 ThreadLocal。
        val sendJobIdx = Regex("""val\s+sendJob\s*=""").find(source)?.range?.first ?: -1
        assertTrue(
            "MessageProcessingDelegate.kt 必须含 `val sendJob =` 赋值。",
            sendJobIdx >= 0
        )
        val launchHead = source.substring(sendJobIdx, (sendJobIdx + 3000).coerceAtMost(source.length))
        assertTrue(
            "launch 块进入后必须立即调 `setSessionVars(platform = originPlatformOverride...` —— " +
                "用 C-route 4 层管道传进来的显式参数把 origin 重写回 launch 内线程的 ThreadLocal，" +
                "下游 AIMessageManager cold stream / agent loop / `_originFromEnv()` 才读得到。",
            Regex(
                """setSessionVars\s*\(\s*platform\s*=\s*originPlatformOverride"""
            ).containsMatchIn(launchHead)
        )
        assertTrue(
            "launch 块进入后必须立即调 `setCronAutoDeliverVars(platform = originPlatformOverride...` —— " +
                "对称同步 cron auto-deliver ThreadLocal。",
            Regex(
                """setCronAutoDeliverVars\s*\(\s*platform\s*=\s*originPlatformOverride"""
            ).containsMatchIn(launchHead)
        )

        // 3) finally 块必须调 clearSessionVars() + clearCronAutoDeliverVars() —— 防泄漏。
        //    宽松：源里出现 `clearSessionVars()` + `clearCronAutoDeliverVars()` 即可
        //    （行为测试覆盖更细的位置正确性）。
        assertTrue(
            "MessageProcessingDelegate.kt 必须含 `clearSessionVars()` 调用 —— " +
                "launch 块结束后清空 ThreadLocal 防止线程复用时污染下个请求。",
            source.contains("clearSessionVars()")
        )
        assertTrue(
            "MessageProcessingDelegate.kt 必须含 `clearCronAutoDeliverVars()` 调用。",
            source.contains("clearCronAutoDeliverVars()")
        )
    }

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageProcessingDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
