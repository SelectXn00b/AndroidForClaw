package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-STREAMING-g (R-CRON-STREAMING-001, 2026-06-25 第三次 bugfix)：
 *
 * **In-flight dispatch 不被 cancel 砍**。
 *
 * 真实日志证据（用户提供的 jobId=3a8fb2349744）：
 *   streaming AssistantDelta turn=1 rawLen=155 strippedLen=121 isBlank=false
 *   streaming dispatch turn=1 jobId=... textLen=121
 *   dispatchOutgoing OUT success=false error=threw:StandaloneCoroutine was cancelled
 *   streaming dispatch failed turn=1 jobId=... reason=ok=false
 *   streaming sidecar summary ... dispatchCalls=1 dispatchSuccess=0 streamingDelivered=false
 *
 * 时间线（同步推理）：
 *   T1  responseStream.collect { ... } 跑完
 *   T2  CronFileLogger "streaming sidecar summary"
 *   T3  sidecarJob?.cancel()                          ← 砍掉 sidecar 协程
 *   T4  sidecar 内 in-flight 的 dispatchOutgoing 抛 CancellationException
 *   T5  gateway adapter 抓异常返回 ok=false
 *   T6  streamingDelivered=false → deliver(...) 走主路兜底 → 用户收到一坨
 *
 * 修法：
 *  (a) `gateway.dispatchOutgoing(...)` 必须包在 `withContext(NonCancellable) { ... }` 内 ——
 *      即使 sidecarJob 被 cancel，已经在飞的网络请求也能跑完拿到 ok=true 返回值。
 *  (b) 主路 cancel 之前应给 sidecar 一个收尾机会：用 `withTimeoutOrNull(...)` join 等待
 *      in-flight dispatch 完成（一个合理的超时保护，避免无限等待）。
 *
 * 源码扫描理由（同其他 CronStreamingXxxWiringTest）：
 * sidecar 实际取消时序需要 live coroutines + 真的 gateway 网络栈，pure JVM unit test 无法重现。
 * 字面级别的 wiring 守住关键正交修复点（NonCancellable 包 dispatch + cancel 前 drain）
 * 已足够防止 regression。
 */
class CronStreamingDispatchNotCancelledWiringTest {

    /** Comment-stripped source —— 防止 KDoc 里 "NonCancellable" / "withTimeoutOrNull" 引用造成假阳性。 */
    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }

    /** Comment-stripped body of the `run` function only. */
    private val runBody: String by lazy { extractFunctionBody(source, "suspend fun run(") }

    @Test
    fun `TC-CRON-STREAMING-g sidecar dispatch is non-cancellable and main path drains before cancel`() {
        assertTrue(
            "TC-CRON-STREAMING-g: `CronAgentRunner.run` 函数体未找到，结构变了。",
            runBody.isNotEmpty()
        )

        // (1) sidecar 必须引用 `NonCancellable`
        assertTrue(
            "TC-CRON-STREAMING-g: `CronAgentRunner.run` 必须引用 `NonCancellable`，否则主路 cancel " +
                "会把 sidecar 内 in-flight 的 `dispatchOutgoing(...)` 网络调用一并砍掉，" +
                "导致 `streaming dispatch threw ... reason=StandaloneCoroutine was cancelled`。",
            runBody.contains("NonCancellable")
        )

        // (2) `NonCancellable` 必须跟 `withContext(` 配对（不是空写一个 import 就完事）
        assertTrue(
            "TC-CRON-STREAMING-g: `NonCancellable` 必须与 `withContext(` 配对使用，" +
                "形如 `withContext(NonCancellable) { gateway.dispatchOutgoing(...) }`。",
            runBody.contains("withContext(NonCancellable") ||
                runBody.contains("withContext(kotlinx.coroutines.NonCancellable")
        )

        // (3) `withContext(NonCancellable` 的字符 index 必须出现在 `dispatchOutgoing(` 之前，
        //     证明 NonCancellable 真包住了 dispatch 调用，而不是包了别的东西。
        val nonCancelIdx = listOf(
            runBody.indexOf("withContext(NonCancellable"),
            runBody.indexOf("withContext(kotlinx.coroutines.NonCancellable")
        ).filter { it >= 0 }.minOrNull() ?: -1
        val dispatchIdx = runBody.indexOf("dispatchOutgoing(")
        assertTrue(
            "TC-CRON-STREAMING-g: `withContext(NonCancellable` (idx=$nonCancelIdx) 必须出现在 " +
                "`dispatchOutgoing(` (idx=$dispatchIdx) **之前**，证明 NonCancellable 真包住 " +
                "了 dispatch 调用。",
            nonCancelIdx in 0 until dispatchIdx
        )

        // (4) 主路 `responseStream.collect { ... }` 完成之后到 `sidecarJob?.cancel()` 之间，
        //     必须有 in-flight drain 机制（`withTimeoutOrNull(` 或 `.join()`）：
        //     给 sidecar 内已经派出去的 dispatch 一个跑完的机会，再 cancel 掉等下一个 event 的 collect。
        val collectIdx = runBody.indexOf("responseStream.collect")
        val cancelIdx = runBody.indexOf("sidecarJob?.cancel(")
        assertTrue(
            "TC-CRON-STREAMING-g: 必须能定位 `responseStream.collect` (idx=$collectIdx) 和 " +
                "`sidecarJob?.cancel(` (idx=$cancelIdx) 在 `run` 函数体内的位置。",
            collectIdx >= 0 && cancelIdx > collectIdx
        )
        val drainWindow = runBody.substring(collectIdx, cancelIdx)
        val hasDrain = drainWindow.contains("withTimeoutOrNull(") || drainWindow.contains(".join()")
        assertTrue(
            "TC-CRON-STREAMING-g: 在 `responseStream.collect` (idx=$collectIdx) 跑完到 " +
                "`sidecarJob?.cancel(` (idx=$cancelIdx) 之间必须含 `withTimeoutOrNull(` 或 `.join()`，" +
                "给 sidecar 内 in-flight dispatch 一个跑完的窗口。否则 cancel 会立刻打断在飞的 " +
                "`dispatchOutgoing`，复现"
                + "本 bug。drainWindow head:\n${drainWindow.take(800)}",
            hasDrain
        )
    }

    // =====================================================================
    // helpers (与 CronStreamingDispatchWiringTest 同款，复制以保持测试类自包含)
    // =====================================================================

    private fun extractFunctionBody(text: String, signaturePrefix: String): String {
        val anchor = text.indexOf(signaturePrefix)
        if (anchor < 0) return ""
        val openBrace = text.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < text.length) {
            val c = text[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return text.substring(openBrace, i + 1)
            }
            i++
        }
        return text.substring(openBrace)
    }

    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun runnerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        return alt.path
    }
}
