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

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageProcessingDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
