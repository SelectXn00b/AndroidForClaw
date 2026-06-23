package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-EXACT-i (2026-06-23 第三次 bugfix，0 字节输出文件根因 / R-AGENT-031)：
 *
 * 根因链：
 *   `CronExactAlarmReceiver.onReceive` → `CronAgentRunner.run`
 *   → `ExternalChatRequestExecutor.execute`
 *   → `StandardChatManagerTool.startMessageToAIStream`
 *   → `ensureServiceConnected()` 在 `FloatingChatService.getInstance() == null` 时
 *   `return false`（`StandardChatManagerTool.kt:609-615`）
 *   → `aiResponse=null` → `saveJobOutput("")` → 0 字节
 *   → `result.success=false` → `else if (result.success)` 跳过整个 deliver 块
 *   → chat history / IM 都收不到提醒。
 *
 * 证据：
 *   - dex 字符串 `"FloatingChatService not running; skip auto-start"` 命中
 *   - `dumpsys activity services` 显示 cron 触发后 FloatingChatService 不在跑
 *   - 16 个历史 output 文件全 0 字节
 *
 * 修法（对齐 Python 上游 `reference/hermes-agent/cron/scheduler.py::run_job`
 * 直接 `AIAgent.run_conversation(prompt)`，不经任何 UI service）：
 *   CronAgentRunner 走 **headless agent loop** —— 直接调
 *   `EnhancedAIService.sendMessage(isSubTask = true)` + `ChatHistoryManager` 持久化，
 *   摆脱 `ExternalChatRequestExecutor` → `StandardChatManagerTool` → `FloatingChatService`
 *   这条 UI-bound 链路。
 *
 * 测试策略：源码扫描（unit-scan）+ 手测兜底（见 hermes-test-cases.md TC-CRON-EXACT-g/i 备注）。
 */
class CronAgentRunnerHeadlessTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    /**
     * TC-CRON-EXACT-i-1: 回归红线 —— CronAgentRunner 源码**不得**含
     * `ExternalChatRequestExecutor` 或 `StandardChatManagerTool` 字面值。
     *
     * 这两个 class 都强依赖 `FloatingChatService`：
     *   - `ExternalChatRequestExecutor.kt:165` 内部直接 `StandardChatManagerTool(appContext)`
     *   - `StandardChatManagerTool.kt:550` `private var floatingService: FloatingChatService?`
     *
     * 走它们 = cron 触发场景下 silent-bail = 0 字节 bug 复现。
     */
    @Test
    fun `TC-CRON-EXACT-i-1 cron runner does not depend on FloatingChatService-bound classes`() {
        assertFalse(
            "CronAgentRunner.kt 不得含 `ExternalChatRequestExecutor` 字面值 —— " +
                "这条路径 silent-bail 在 FloatingChatService.getInstance()==null 时 " +
                "（StandardChatManagerTool.kt:609-615），cron 必须走 headless 路径。" +
                "若需要重新借用此 executor，必须在该 executor 内部去除 " +
                "FloatingChatService 强依赖。",
            source.contains("ExternalChatRequestExecutor")
        )
        assertFalse(
            "CronAgentRunner.kt 不得含 `StandardChatManagerTool` 字面值 —— " +
                "此 tool 强绑 FloatingChatService，cron 触发场景永远 silent-bail。",
            source.contains("StandardChatManagerTool")
        )
        assertFalse(
            "CronAgentRunner.kt 不得含 `FloatingChatService` 字面值 —— " +
                "headless cron 路径绝不应触及 floating UI service。",
            source.contains("FloatingChatService")
        )
    }

    /**
     * TC-CRON-EXACT-i-2: CronAgentRunner 必须含 headless agent loop 入口
     * `EnhancedAIService.sendMessage(isSubTask = true)`。
     *
     * 这是 Python 上游 `scheduler.py:914`
     *   `_cron_pool.submit(_cron_context.run, agent.run_conversation, prompt)`
     * 的 Kotlin 对照。`EnhancedAIService.sendMessage` 不依赖 FloatingChatService，
     * `isSubTask = true` 跳过 `startAiService` 前台通知和 `_inputProcessingState`
     * UI state 更新（`EnhancedAIService.kt:816-859` 都有 `if (!isSubTask)` 守卫）。
     */
    @Test
    fun `TC-CRON-EXACT-i-2 cron runner uses EnhancedAIService headless entry`() {
        assertTrue(
            "CronAgentRunner.kt 必须含 `EnhancedAIService` —— headless agent 调用入口。",
            source.contains("EnhancedAIService")
        )
        assertTrue(
            "CronAgentRunner.kt 必须调 `EnhancedAIService.getInstance(` —— singleton 取实例。",
            source.contains("EnhancedAIService.getInstance(")
        )
        assertTrue(
            "CronAgentRunner.kt 必须调 `sendMessage(` —— headless agent 入口函数。",
            source.contains("sendMessage(")
        )
        assertTrue(
            "CronAgentRunner.kt 必须传 `isSubTask = true` —— " +
                "跳过 startAiService 前台通知 + _inputProcessingState UI hop " +
                "（EnhancedAIService.kt:816-859 的 `if (!isSubTask)` 守卫）。",
            source.contains("isSubTask = true") || source.contains("isSubTask=true")
        )
    }

    /**
     * TC-CRON-EXACT-i-3: CronAgentRunner 必须收完 `EnhancedAIService.sendMessage`
     * 返回的 `Stream<String>` token deltas，合成完整 AI 回复文本。
     *
     * Python `run_conversation` 同步返回 dict，Kotlin 走 streaming。collect 完
     * 整个 Stream 才能拿到 `final_response` 等价物。空 stream / blank 必须能记
     * `success=false` 而不是默认成功。
     */
    @Test
    fun `TC-CRON-EXACT-i-3 cron runner collects streaming response into final text`() {
        assertTrue(
            "CronAgentRunner.kt 必须含 `collect` —— " +
                "EnhancedAIService.sendMessage 返回 Stream<String>，需 collect 才拿到完整回复。",
            source.contains(".collect")
        )
        assertTrue(
            "CronAgentRunner.kt 必须含 StringBuilder 或等价聚合器累积 token delta。",
            source.contains("StringBuilder") || source.contains("buildString")
        )
    }

    /**
     * TC-CRON-EXACT-i-4: ThreadLocal origin / cron auto-deliver 必须在 finally 块清理
     * （R-AGENT-033/045 红线，避免协程切线程残留导致下一个回合 origin 错乱）。
     *
     * 同时必须用 `withContext(sessionContextElement())` 包住 agent 调用，让 IO 线程
     * 也能读到 ThreadLocal snapshot（对齐 Python `copy_context().run(func)`，
     * `SessionContext.kt:187-199`）。
     */
    @Test
    fun `TC-CRON-EXACT-i-4 cron runner manages session vars with finally-clear`() {
        assertTrue(
            "CronAgentRunner.kt 必须调 `setSessionVars(` —— origin 写入 ThreadLocal " +
                "（对齐 Python scheduler.py:761-765）。",
            source.contains("setSessionVars(")
        )
        assertTrue(
            "CronAgentRunner.kt 必须调 `clearSessionVars()` —— finally 块清理 ThreadLocal。",
            source.contains("clearSessionVars(")
        )
        // 跨行 regex 验证 clearSessionVars 在 finally 块内
        val finallyClearsSession = Regex(
            """finally\s*\{[\s\S]{0,800}clearSessionVars\(""",
            RegexOption.DOT_MATCHES_ALL
        ).containsMatchIn(source)
        assertTrue(
            "CronAgentRunner.kt 必须在 `finally { ... }` 块内调 `clearSessionVars()` —— " +
                "R-AGENT-033 红线：协程切线程残留 origin 会污染下一回合。",
            finallyClearsSession
        )
        assertTrue(
            "CronAgentRunner.kt 必须用 `sessionContextElement()` 包住 agent 调用 —— " +
                "对齐 Python copy_context().run(func)，让 IO 线程读到 ThreadLocal snapshot " +
                "（SessionContext.kt:187-199）。",
            source.contains("sessionContextElement()")
        )
    }

    /**
     * TC-CRON-EXACT-i-5: R-AGENT-031 [CRON CONTEXT] 双语前缀必须保留；
     * R-AGENT-035 IM origin dispatch 必须保留（`HermesGatewayController.dispatchOutgoing`）。
     *
     * 这两条是 headless 重写不能丢的下游契约。
     */
    @Test
    fun `TC-CRON-EXACT-i-5 cron runner preserves cron-context prefix and IM dispatch`() {
        // R-AGENT-031 prompt 前缀
        assertTrue(
            "CronAgentRunner.kt 必须保留 `[CRON CONTEXT]` 英文前缀（R-AGENT-031 递归 cronjob 软防御）。",
            source.contains("[CRON CONTEXT]")
        )
        assertTrue(
            "CronAgentRunner.kt 必须保留 `[CRON 上下文]` 中文前缀（中文 locale 等价）。",
            source.contains("[CRON 上下文]")
        )
        assertTrue(
            "CronAgentRunner.kt 必须保留 `Do NOT register additional cron jobs` 软防御语句。",
            source.contains("Do NOT register additional cron jobs")
        )
        // R-AGENT-035 IM dispatch
        assertTrue(
            "CronAgentRunner.kt 必须保留 `HermesGatewayController` 引用 —— " +
                "IM origin dispatch（R-AGENT-035）。",
            source.contains("HermesGatewayController")
        )
        assertTrue(
            "CronAgentRunner.kt 必须保留 `dispatchOutgoing(` 调用 —— IM 投递入口。",
            source.contains("dispatchOutgoing(")
        )
        // 持久化层
        assertTrue(
            "CronAgentRunner.kt 必须保留 `ChatHistoryManager` 引用 —— 路径 4 持久化层。",
            source.contains("ChatHistoryManager")
        )
        assertTrue(
            "CronAgentRunner.kt 必须保留 `saveJobOutput(` 引用 —— 写入 cron output 文件。",
            source.contains("saveJobOutput(")
        )
        assertTrue(
            "CronAgentRunner.kt 必须保留 `markJobRun(` 引用 —— 标记 last_run 状态。",
            source.contains("markJobRun(")
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
