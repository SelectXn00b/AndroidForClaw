package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-035 (2026-06-15)：cron tick 真实路径（CronAgentRunner.deliver）必须读
 * `job["origin"]` + `job["deliver"]` 字段，并把 deliver="origin" 路径委托给
 * `HermesGatewayController.dispatchOutgoing`。
 *
 * 起因：R-AGENT-033 把 cronOutboundDispatcher 注入到 `Scheduler.kt::deliverResult`，
 * 但 Android 实际 cron tick 走 CronTickWorker → CronAgentRunner.run() →
 * CronAgentRunner.deliver()，**完全 bypass** Scheduler.deliverResult（Scheduler.kt
 * 头注释 line 6-8 已写明），导致 R-AGENT-033 的 dispatcher 永不触达。本 R 把
 * origin → IM 投递分支搬到真实路径 CronAgentRunner.deliver()。
 *
 * 测试策略：源码扫描（同 R-AGENT-033 测试一致）—— `deliver()` 内含 suspend / 协程 /
 * gateway controller 单例查找，纯 JVM 单测难起，由 §3 E2E 兜底（手测：飞书设 15min
 * 定时任务，16min 后真收到 ai 消息）。
 *
 * 对应 TC-AGENT-035-a / TC-AGENT-035-b / TC-AGENT-035-c（见 docs/hermes-test-cases.md）。
 */
class CronAgentRunnerOriginDeliveryWiringTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    /**
     * TC-AGENT-035-a: deliver() 必须能读 `job["origin"]` + `job["deliver"]` 字段，
     * 且区分 "local" / "origin" 两个 deliver 模式。
     */
    @Test
    fun `TC-AGENT-035-a deliver reads origin and deliver fields`() {
        assertTrue(
            "TC-AGENT-035-a: CronAgentRunner.kt 必须含 `\"origin\"` 字面值 —— " +
                "deliver() 必须能读 job[\"origin\"] map（platform/chat_id/thread_id）。\n" +
                "实际源码:\n${source.take(4000)}",
            source.contains("\"origin\"")
        )
        assertTrue(
            "TC-AGENT-035-a: CronAgentRunner.kt 必须含 `\"deliver\"` 字面值 —— " +
                "deliver() 必须读 job[\"deliver\"] 字段判断模式（origin / local / platform:chat:thread）。",
            source.contains("\"deliver\"")
        )
        assertTrue(
            "TC-AGENT-035-a: CronAgentRunner.kt 必须含 `\"local\"` 字面值 —— " +
                "本地 fallback 模式必须显式判断（与 origin 模式互斥）。",
            source.contains("\"local\"")
        )
        // origin 子字段——至少 platform 和 chat_id 必须被读
        assertTrue(
            "TC-AGENT-035-a: CronAgentRunner.kt 必须含 `\"platform\"` 字面值 —— " +
                "origin map 内的 platform 子字段必须被读出来传给 dispatchOutgoing。",
            source.contains("\"platform\"")
        )
        assertTrue(
            "TC-AGENT-035-a: CronAgentRunner.kt 必须含 `\"chat_id\"` 字面值 —— " +
                "origin map 内的 chat_id 子字段必须被读出来传给 dispatchOutgoing。",
            source.contains("\"chat_id\"")
        )
    }

    /**
     * TC-AGENT-035-b: deliver() 必须委托 IM 投递给 HermesGatewayController.dispatchOutgoing。
     */
    @Test
    fun `TC-AGENT-035-b deliver invokes HermesGatewayController dispatchOutgoing`() {
        assertTrue(
            "TC-AGENT-035-b: CronAgentRunner.kt 必须 reference `HermesGatewayController` —— " +
                "origin 路径必须委托给 R-AGENT-033 已建的 IM 投递桥（不重复实现）。\n" +
                "实际源码:\n${source.take(4000)}",
            source.contains("HermesGatewayController")
        )
        assertTrue(
            "TC-AGENT-035-b: CronAgentRunner.kt 必须含 `dispatchOutgoing(` 调用 —— " +
                "委托给 R-AGENT-033 实现的 platform-adapter 投递入口。",
            Regex("""\bdispatchOutgoing\s*\(""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-035-c 红线：origin 路径不得删除本地 ChatHistoryManager 写入路径。
     * R-AGENT-031 的本地 chat 写入是用户在 app 里查看 cron 输出的唯一入口，本 R 是
     * **新增 origin 分支**而非替换。
     */
    @Test
    fun `TC-AGENT-035-c local fallback path preserved`() {
        assertTrue(
            "TC-AGENT-035-c 红线: CronAgentRunner.kt 必须仍 reference `ChatHistoryManager` —— " +
                "本地 chat 写入路径不得删除（用户在 app 里查看 cron 输出依赖这个）。",
            source.contains("ChatHistoryManager")
        )
        assertTrue(
            "TC-AGENT-035-c 红线: CronAgentRunner.kt 必须仍含 `addMessage(` 调用 —— " +
                "Room DB 持久化入口。",
            Regex("""\baddMessage\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "TC-AGENT-035-c 红线: CronAgentRunner.kt 必须仍 reference `GatewayChatEventBus` —— " +
                "活跃 chat 面板靠 ProcessingCompleted 事件 reload。",
            source.contains("GatewayChatEventBus")
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
