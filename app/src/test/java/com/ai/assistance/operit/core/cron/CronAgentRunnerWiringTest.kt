package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031: CronAgentRunner 把 cron job 的 prompt 包成 [CRON CONTEXT] / [CRON 上下文]
 * 前缀，然后通过 ExternalChatRequestExecutor 执行 agent 回合，最后把结果写回
 * ChatHistoryManager 并 emit GatewayChatEventBus.ProcessingCompleted。
 *
 * 路径 3：递归 cronjob 软防御 —— bilingual 前缀让 agent 知道这一回合是被 cron 触发的，
 * 别再注册新的 cronjob。
 *
 * 路径 4：通过 ChatHistoryManager（持久层）写回，不通过 ChatHistoryDelegate（UI-bound）。
 *
 * 对应 TC-AGENT-031-g, h, i。
 */
class CronAgentRunnerWiringTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    /**
     * TC-AGENT-031-g: prompt 必须被 bilingual `[CRON CONTEXT]` / `[CRON 上下文]` 包裹。
     */
    @Test
    fun `TC-AGENT-031-g cron agent runner wraps prompt with bilingual cron-context prefix`() {
        assertTrue(
            "CronAgentRunner.kt 必须含英文前缀字面值 `[CRON CONTEXT]` —— " +
                "agent 通过这个前缀判断本回合是 cron 触发的。",
            source.contains("[CRON CONTEXT]")
        )
        assertTrue(
            "CronAgentRunner.kt 必须含中文前缀字面值 `[CRON 上下文]` —— " +
                "中文 locale 的 agent 也要能识别。",
            source.contains("[CRON 上下文]")
        )
        // 前缀必须 mention "Do NOT register additional cron jobs" / "不要再注册新的 cronjob"
        assertTrue(
            "CronAgentRunner 英文前缀必须含 `Do NOT register additional cron jobs` —— " +
                "递归 cronjob 软防御核心语句。",
            source.contains("Do NOT register additional cron jobs")
        )
        assertTrue(
            "CronAgentRunner 中文前缀必须含 `不要再注册新的 cronjob` —— " +
                "中文 locale 同上。",
            source.contains("不要再注册新的 cronjob")
        )
    }

    /**
     * TC-AGENT-031-h: agent invocation 必须走 headless agent 入口（EnhancedAIService）。
     *
     * 历史背景（2026-06-23 第三次 bugfix，原 TC-AGENT-031-h 断言被修正）：
     * 原 h 断言 `ExternalChatRequestExecutor`。事实证明这条路径**不是 headless** ——
     * `ExternalChatRequestExecutor.execute` → `StandardChatManagerTool.sendMessageToAI`
     * → `ensureServiceConnected()` 在 `FloatingChatService.getInstance() == null` 时
     * silent-bail（`StandardChatManagerTool.kt:609-615`），cron 触发的典型场景
     * （设备空闲、用户没在用 app）下 `aiResponse=null` → `saveJobOutput("")` → 0 字节
     * 文件，`success=false` 让 deliver 块整个 skip，chat 收不到提醒。
     *
     * 修正后断言（对齐 Python 上游 `reference/hermes-agent/cron/scheduler.py::run_job`
     * 直接调 `AIAgent.run_conversation`，不经任何 UI service）：
     * CronAgentRunner 必须直接调 `EnhancedAIService.sendMessage(isSubTask=true)`，
     * 这条路径不依赖 FloatingChatService（`EnhancedAIService.sendMessage` 内的
     * `startAiService` / `_inputProcessingState` UI hop 由 `isSubTask=true` 跳过）。
     *
     * TC-CRON-EXACT-i 在 `CronAgentRunnerHeadlessTest.kt` 进一步覆盖
     * "不得含 ExternalChatRequestExecutor / StandardChatManagerTool 字面值" 的回归红线。
     */
    @Test
    fun `TC-AGENT-031-h cron agent runner invokes headless agent loop (EnhancedAIService)`() {
        assertTrue(
            "CronAgentRunner 必须调 `EnhancedAIService` —— " +
                "headless agent 调用入口（不依赖 FloatingChatService，对齐 Python 上游 " +
                "scheduler.py::run_job 直接 AIAgent.run_conversation 的路径）。",
            source.contains("EnhancedAIService")
        )
        assertTrue(
            "CronAgentRunner 必须调 `sendMessage(` —— EnhancedAIService 的 agent 入口；" +
                "必须传 `isSubTask = true` 才能跳过 startAiService 前台通知 + UI state 更新。",
            source.contains("sendMessage(") && source.contains("isSubTask")
        )
    }

    /**
     * TC-AGENT-031-i: 结果必须通过 ChatHistoryManager.addMessage 写回（路径 4），
     * 然后 emit GatewayChatEventBus.ProcessingCompleted。
     */
    @Test
    fun `TC-AGENT-031-i cron agent runner persists via ChatHistoryManager and emits gateway event`() {
        assertTrue(
            "CronAgentRunner 必须用 `ChatHistoryManager` —— " +
                "Worker 不能用 UI-bound 的 ChatHistoryDelegate，必须走持久层。",
            source.contains("ChatHistoryManager")
        )
        assertTrue(
            "CronAgentRunner 必须调 `historyManager.addMessage` —— Room 持久化入口。",
            source.contains("historyManager.addMessage") ||
                Regex("""ChatHistoryManager[^\n]*addMessage""").containsMatchIn(source)
        )
        assertTrue(
            "CronAgentRunner 必须 emit `GatewayChatEventBus.Event.ProcessingCompleted` —— " +
                "活跃 chat 面板要从 DB 重载新消息。",
            source.contains("GatewayChatEventBus.Event.ProcessingCompleted") ||
                source.contains("Event.ProcessingCompleted")
        )
        assertTrue(
            "CronAgentRunner 必须调 `markJobRun(` —— Jobs.kt 持久化 last_run / last_error 字段。",
            source.contains("markJobRun(")
        )
        assertTrue(
            "CronAgentRunner 必须调 `saveJobOutput(` —— Jobs.kt 持久化最近一次输出。",
            source.contains("saveJobOutput(")
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun runnerPath(): String =
        File(appSrcMainRoot(), "core/cron/CronAgentRunner.kt").path
}
