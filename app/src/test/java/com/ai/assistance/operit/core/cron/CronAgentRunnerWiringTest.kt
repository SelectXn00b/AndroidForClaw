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
     * TC-AGENT-031-h: agent invocation 必须走 ExternalChatRequestExecutor.execute。
     */
    @Test
    fun `TC-AGENT-031-h cron agent runner invokes ExternalChatRequestExecutor`() {
        assertTrue(
            "CronAgentRunner 必须调 `ExternalChatRequestExecutor` —— " +
                "headless agent 调用入口（与 external-chat broadcast 共用）。",
            source.contains("ExternalChatRequestExecutor")
        )
        assertTrue(
            "CronAgentRunner 必须构造 `ExternalChatRequest` —— executor 的入参类型。",
            source.contains("ExternalChatRequest")
        )
        assertTrue(
            "CronAgentRunner 必须调 `executor.execute(` —— 执行 agent 回合的具体调用点。",
            source.contains("executor.execute(")
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
