package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-035 + R-AGENT-031 (2026-06-23 第四次 bugfix)：cron 在 IM origin 路径派发前必须
 * **先唤醒** `GatewayForegroundService` 并等 `HermesGatewayController.status == RUNNING`
 * 再调 `gateway.dispatchOutgoing(...)`。
 *
 * 根因（用户实测复现）：
 *   - 用户在微信里跟 agent 说"5 分钟后提醒我喝水" → agent 调 `cronjob(action="create")`
 *     → jobs.json 写入 `origin = {platform: "weixin", chat_id: <wxid>}` ✅
 *   - 用户关 app → OEM ROM 在后台杀掉 `GatewayForegroundService`
 *   - 5 分钟后 `CronExactAlarmReceiver` 触发 → `CronAgentRunner.run` 跑 headless agent ✅
 *   - `CronAgentRunner.deliver` 写 local chat ✅ → 进入 IM 派发分支：
 *     `gateway.dispatchOutgoing("weixin", ...)`
 *     → `HermesGatewayController.kt:198-203` 检查 `runner == null` → return false ❌
 *     → 抛 `IllegalStateException("dispatchOutgoing returned false ...")`
 *     → markJobRun(deliveryError) — chat 看不到错误，用户只看到"微信没收到"
 *
 * 修法：`CronAgentRunner.deliver` 进 IM 分支前先唤醒 `GatewayForegroundService`，
 * 并 `withTimeoutOrNull(30_000)` 等 `status.first { it == RUNNING }`，确保 `runner` 非
 * null 再调 `dispatchOutgoing`。app-origin 路径不触达 warmup（短路在前）。
 *
 * 测试策略：源码扫描（同 R-AGENT-035 / R-AGENT-045 测试一致）—— `deliver()` 内含 suspend /
 * 协程 / service 启动 + status 等待，纯 JVM 单测难起；E2E 由 TC-CRON-EXACT-j-4 手测兜底
 * （在微信里设 cron → 关 gateway service → 等 5–6 分钟 → 微信收到提醒）。
 *
 * 对应 TC-CRON-EXACT-j-1 / j-2 / j-3（见 docs/hermes-test-cases.md）。
 */
class CronGatewayWarmupTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    /**
     * TC-CRON-EXACT-j-1: deliver() 必须在 IM 派发前先 `GatewayForegroundService.start(context)`
     * 并等 `HermesGatewayController.status` 变 `RUNNING`。
     */
    @Test
    fun `TC-CRON-EXACT-j-1 deliver warms up gateway service before IM dispatch`() {
        val deliverBody = extractDeliverBody()

        assertTrue(
            "TC-CRON-EXACT-j-1: CronAgentRunner.deliver 必须 reference `GatewayForegroundService` —— " +
                "IM origin 派发前唤醒 gateway service，避免 OEM ROM 杀 service 后 runner==null。\n" +
                "实际 deliver body:\n${deliverBody.take(4000)}",
            deliverBody.contains("GatewayForegroundService")
        )
        assertTrue(
            "TC-CRON-EXACT-j-1: CronAgentRunner.deliver 必须含 `GatewayForegroundService.start(` 调用 —— " +
                "实际唤醒前台 service 的入口。",
            Regex("""GatewayForegroundService\.start\s*\(""").containsMatchIn(deliverBody)
        )
        assertTrue(
            "TC-CRON-EXACT-j-1: CronAgentRunner.deliver 必须 reference `HermesGatewayController` —— " +
                "拿 status StateFlow 等 RUNNING。",
            deliverBody.contains("HermesGatewayController")
        )
        assertTrue(
            "TC-CRON-EXACT-j-1: CronAgentRunner.deliver 必须含 `status` + `first` + `RUNNING` 字面值 —— " +
                "等 `HermesGatewayController.getInstance(ctx).status.first { it == Status.RUNNING }` 完成。",
            deliverBody.contains("status") &&
                deliverBody.contains("first") &&
                deliverBody.contains("RUNNING")
        )
        assertTrue(
            "TC-CRON-EXACT-j-1: CronAgentRunner.deliver 必须含 `withTimeoutOrNull` 字面值 —— " +
                "warmup 不能无限等（service 启动失败时整个 cron 派发会卡死）。",
            deliverBody.contains("withTimeoutOrNull")
        )
    }

    /**
     * TC-CRON-EXACT-j-2: warmup 只在 IM origin 触发；app-origin 路径不应触达 warmup。
     */
    @Test
    fun `TC-CRON-EXACT-j-2 warmup only triggers on IM origin (after app short-circuit)`() {
        val deliverBody = extractDeliverBody()
        val warmupIdx = deliverBody.indexOf("GatewayForegroundService.start(")
        assertTrue(
            "TC-CRON-EXACT-j-2: CronAgentRunner.deliver 必须含 `GatewayForegroundService.start(` —— " +
                "warmup 入口存在的前提。",
            warmupIdx >= 0
        )
        val dispatchIdx = deliverBody.indexOf("gateway.dispatchOutgoing(")
        assertTrue(
            "TC-CRON-EXACT-j-2: deliver 必须仍含 `gateway.dispatchOutgoing(` —— IM 派发路径不能被回归删掉。",
            dispatchIdx >= 0
        )
        assertTrue(
            "TC-CRON-EXACT-j-2: `GatewayForegroundService.start(` 必须在 `gateway.dispatchOutgoing(` 之前 —— " +
                "warmup 先于 IM dispatch，否则 runner==null 时 dispatchOutgoing 立即返回 false。",
            warmupIdx < dispatchIdx
        )

        // app-origin 短路必须在 warmup 之前出现：进入 IM 分支前已经把 app 滤掉，warmup 才不会
        // 误触发到 app-origin cron。
        val appShortCircuit = Regex("""originPlatform\s*==\s*"app"""")
        val appShortCircuitMatch = appShortCircuit.find(deliverBody)
        assertTrue(
            "TC-CRON-EXACT-j-2: deliver 必须含 `originPlatform == \"app\"` 短路（保留 TC-AGENT-045-b 行为）。",
            appShortCircuitMatch != null
        )
        assertTrue(
            "TC-CRON-EXACT-j-2: `originPlatform == \"app\"` 短路必须出现在 `GatewayForegroundService.start(` 之前 —— " +
                "app-origin cron 不应触发 service warmup（多余开销 + 干扰用户）。",
            (appShortCircuitMatch?.range?.first ?: Int.MAX_VALUE) < warmupIdx
        )
    }

    /**
     * TC-CRON-EXACT-j-3: warmup timeout 必须明确，且 timeout 后不能静默继续 dispatchOutgoing。
     */
    @Test
    fun `TC-CRON-EXACT-j-3 warmup uses bounded timeout and surfaces failure`() {
        val deliverBody = extractDeliverBody()
        // withTimeoutOrNull 必须带数字字面值（30_000 / 30000 / 30L * 1000L 等均可）；
        // 拒绝 `withTimeoutOrNull(someVar)` 这种把 timeout 藏在变量后无法静态守的写法 ——
        // 至少要看见明确数字。
        val timeoutPattern = Regex("""withTimeoutOrNull\s*\(\s*[\d_lL\s*+]+\s*[\)]""")
        assertTrue(
            "TC-CRON-EXACT-j-3: deliver 必须含 `withTimeoutOrNull(<数字>)` 形式 —— " +
                "明确 ms 字面值，避免不可见的 timeout 值改写。\n" +
                "实际 deliver body:\n${deliverBody.take(4000)}",
            timeoutPattern.containsMatchIn(deliverBody)
        )

        // warmup timeout 必须有可见的失败语义：要么 throw IllegalStateException 提示 warmup
        // timeout，要么 return / 早返避免无意义 dispatchOutgoing。
        // 我们的实现选 throw —— deliver 是 suspend，外层 try-catch 已经会把 e.message 写进
        // markJobRun(deliveryError=...)，用户能在 jobs.json 看到。
        val warmupTimeoutErrorMsg = Regex("""IllegalStateException\s*\([^)]*warmup""", RegexOption.IGNORE_CASE)
        val warmupFailReturn = Regex("""warmup[^"]*timeout""", RegexOption.IGNORE_CASE)
        assertTrue(
            "TC-CRON-EXACT-j-3: warmup timeout 失败必须显式可见 —— 含 `IllegalStateException(... warmup ...)` " +
                "或显式 `\"warmup timeout\"` 字符串。否则静默继续到 dispatchOutgoing 等同没修。",
            warmupTimeoutErrorMsg.containsMatchIn(deliverBody) ||
                warmupFailReturn.containsMatchIn(deliverBody)
        )
    }

    // ----- helpers -----

    /** 提取 deliver(...) 函数体 — 简化版 brace-walker。 */
    private fun extractDeliverBody(): String {
        val idx = source.indexOf("private suspend fun deliver(")
        if (idx < 0) error("deliver(...) 没找到")
        val braceStart = source.indexOf('{', idx)
        if (braceStart < 0) error("deliver(...) 没找到开 brace")
        var depth = 0
        var i = braceStart
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(braceStart + 1, i)
            }
            i++
        }
        error("deliver(...) brace walk 未闭合")
    }

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
