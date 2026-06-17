package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-045: `CronAgentRunner.deliver()` 必须识别 `origin_platform="app"` 并
 * 跳过 `gateway.dispatchOutgoing` 的 IM 路径 —— in-app chat 的 origin 不存在
 * IM adapter，调 dispatchOutgoing 会返回 false → 抛 IllegalStateException →
 * markJobRun 误记 last_delivery_error。
 *
 * 同时要求 `writeLocalChatNote` 仍然被调，cron 输出在 in-app chat 可见
 * （这一行已经无条件出现在 deliver 顶部，本测试守它不被回归挪走或加 if 分支屏蔽）。
 *
 * 源码扫描测试 —— 校验 deliver() 的 when/if 分支与 writeLocalChatNote 调用点。
 *
 * 对应 TC-AGENT-045-b、TC-AGENT-045-c、TC-AGENT-045-d、TC-AGENT-045-e。
 */
class CronAgentRunnerAppOriginTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    /**
     * TC-AGENT-045-b: deliver() 必须有 app-origin 短路分支，跳过 dispatchOutgoing。
     */
    @Test
    fun `TC-AGENT-045-b deliver short-circuits IM dispatch when origin platform is app`() {
        val deliverBody = extractDeliverBody()
        assertTrue(
            "CronAgentRunner.deliver() 必须含 `\"app\"` 字面值 —— in-app origin 的 platform sentinel。",
            deliverBody.contains("\"app\"")
        )
        // 必须有"短路条件"：platform == "app" 时 return / 不调 dispatchOutgoing
        // 我们松绑成：deliverBody 中 `gateway.dispatchOutgoing(` 调用之前必须出现 "app" 比较
        // （要么 if，要么 when 分支）
        val dispatchIdx = deliverBody.indexOf("gateway.dispatchOutgoing(")
        assertTrue(
            "CronAgentRunner.deliver() 必须实际调 `gateway.dispatchOutgoing(` —— IM 路径仍存在（gateway origin 不能被回归砍掉）。",
            dispatchIdx >= 0
        )
        val beforeDispatch = deliverBody.substring(0, dispatchIdx)
        assertTrue(
            "CronAgentRunner.deliver() 必须在 `dispatchOutgoing(` 之前出现 `originPlatform == \"app\"` " +
                "或等价的 app-origin 短路检查 —— 否则 in-app origin 会跑进 IM 派发并失败。",
            Regex("""originPlatform\s*==\s*"app"""").containsMatchIn(beforeDispatch) ||
                Regex(""""app"\s*==\s*originPlatform""").containsMatchIn(beforeDispatch) ||
                Regex(""""app"\s*->""").containsMatchIn(beforeDispatch)
        )
    }

    /**
     * TC-AGENT-045-c: app-origin 路径必须仍调 writeLocalChatNote
     * （不是只跳过 IM 就完事；cron 输出要落进 in-app chat 历史）。
     */
    @Test
    fun `TC-AGENT-045-c app origin still writes local chat note`() {
        val deliverBody = extractDeliverBody()
        // writeLocalChatNote 必须无条件出现（在 originMatched 检查之前），
        // 否则 in-app origin 短路掉之后用户在 app 看不到 cron 输出。
        assertTrue(
            "CronAgentRunner.deliver() 顶部必须无条件调 `writeLocalChatNote(` —— " +
                "无论 origin 是 app/telegram/local，cron 输出都要落进 in-app chat 历史。",
            deliverBody.contains("writeLocalChatNote(")
        )
        // 进一步约束：writeLocalChatNote 必须出现在第一个 `if (` / `when (` 之前
        // 即 deliver 一进来就先写 chat note。
        val noteIdx = deliverBody.indexOf("writeLocalChatNote(")
        val firstIfIdx = deliverBody.indexOf("if (")
        // 如果有 if，note 必须在它之前；否则没有 if 也 OK
        if (firstIfIdx >= 0) {
            assertTrue(
                "writeLocalChatNote(...) 必须在第一个 `if (` 之前调用 —— " +
                    "确保所有 origin 类型都写本地 chat note，不被 if 分支屏蔽。",
                noteIdx < firstIfIdx
            )
        }
    }

    /**
     * TC-AGENT-045-d: null origin 时 deliver 不抛异常，走 local-only。
     * 源码扫描守 `originMatched` 计算 —— 必须含 `origin != null` null check。
     */
    @Test
    fun `TC-AGENT-045-d deliver gracefully handles null origin`() {
        val deliverBody = extractDeliverBody()
        assertTrue(
            "CronAgentRunner.deliver() 必须含 `origin != null` 检查 —— " +
                "兼容旧 jobs.json 没 origin 字段的记录，不能 NPE。",
            deliverBody.contains("origin != null")
        )
        // 当 originMatched=false 时必须 return —— 不进 IM 派发
        assertTrue(
            "CronAgentRunner.deliver() 必须含 `if (!originMatched)` 短路 return —— " +
                "null origin / deliver=local 都走这条 fast-return。",
            Regex("""if\s*\(\s*!\s*originMatched\s*\)""").containsMatchIn(deliverBody)
        )
    }

    /**
     * TC-AGENT-045-e: telegram origin 仍然走 gateway.dispatchOutgoing
     * （app 短路只能屏蔽 platform=="app"，不能误伤其他 origin）。
     */
    @Test
    fun `TC-AGENT-045-e non-app origin still routes via gateway dispatchOutgoing`() {
        val deliverBody = extractDeliverBody()
        // dispatchOutgoing 调用必须仍然存在（gateway origin 不能被回归砍掉）
        assertTrue(
            "CronAgentRunner.deliver() 必须保留 `gateway.dispatchOutgoing(` 路径 —— " +
                "telegram/weixin/etc origin 还要走 IM 派发，不能因 app 短路把它一起去掉。",
            Regex("""gateway\.dispatchOutgoing\s*\(""").containsMatchIn(deliverBody)
        )
        // 短路必须是"specific to app"，不是无条件 return
        // 验证：dispatchOutgoing 之前的某个 return 是 app-conditional，不是 unconditional
        val dispatchIdx = deliverBody.indexOf("dispatchOutgoing")
        val beforeDispatch = deliverBody.substring(0, dispatchIdx)
        // app 短路必须是 "if (originPlatform == \"app\") { ... return }" 形式
        // 即 return 前必须有 "app" 检查
        val unconditionalReturn = Regex("""^\s*return\s*$""", RegexOption.MULTILINE)
        // 我们要求：每个 unconditional `return`（前面没有 if）若存在，必须是 originMatched 已经检查过的语境
        // 简化：验证 deliver 中既有 `originPlatform == "app"`（精确短路） 又有 `dispatchOutgoing`（IM 派发还在）
        assertTrue(
            "CronAgentRunner.deliver() 中 app 短路必须 `originPlatform == \"app\"` 形式（specific），" +
                "不是无条件 return。",
            Regex("""originPlatform\s*==\s*"app"""").containsMatchIn(deliverBody)
        )
    }

    // ----- helpers -----

    /** 提取 deliver(...) 函数体 — 简化版 brace-walker。 */
    private fun extractDeliverBody(): String {
        val idx = source.indexOf("private suspend fun deliver(")
        if (idx < 0) error("deliver(...) 没找到")
        // 找到第一个 "{" 后启动 brace walk
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
