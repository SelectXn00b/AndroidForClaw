package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-013 (2026-06-15)：Telegram 出站对齐 Python 的 retry_after / Markdown fallback / 长消息分段。
 *
 * **范围**：本类只盯 `Telegram.kt::send` 函数体的源码字面值与 helper 函数声明。其他出站方法
 * （`sendImage` / `sendDocument` 等）本轮不动；运行时行为（真 429 限流、真 Markdown 解析失败
 * 路径）由 §3 E2E + 手测兜底（hermes-android testImpl 无 MockWebServer 依赖）。
 *
 * 对应 TC-GW-013-a..c（详见 docs/hermes-test-cases.md）。
 */
class TelegramSendRetryWiringTest {

    private val source: String by lazy { File(telegramKtPath()).readText() }

    /**
     * 抓出 `override suspend fun send(...)` 函数体。
     * 起点：`override suspend fun send(`；终点：下一处 `override suspend fun ` 或 `override fun `。
     */
    private fun extractSendBody(): String {
        val startMarker = "override suspend fun send("
        val startIdx = source.indexOf(startMarker)
        if (startIdx < 0) return ""
        val rest = source.substring(startIdx)
        val endRegex = Regex("""\n\s+override\s+(?:suspend\s+)?fun\s+\w""")
        val match = endRegex.find(rest, startIndex = startMarker.length)
        return if (match != null) rest.substring(0, match.range.first) else rest
    }

    /**
     * TC-GW-013-a: send 函数体含 3-attempt 重试循环 + retry_after 解析 + Markdown fallback + 自愈分支。
     */
    @Test
    fun `TC-GW-013-a send wraps post in 3-attempt loop with retry_after and markdown fallback`() {
        val body = extractSendBody()
        assertTrue(
            "Telegram.kt 必须含 `override suspend fun send(` 函数声明。",
            body.isNotEmpty()
        )

        // 重试循环：必须含 for + attempt + 0 until 3
        assertTrue(
            "send 函数体必须含 `for ` 字面值（重试循环）—— 对齐 Python `telegram.py:1023-1106`。\n实际 send 函数体:\n$body",
            body.contains("for ")
        )
        assertTrue(
            "send 函数体必须含 `attempt` 字面值（循环变量名）。",
            body.contains("attempt")
        )
        assertTrue(
            "send 函数体必须含 `0 until 3` 字面值（3 次重试上限，对齐 Python `_NUM_RETRIES = 3`）。\n实际 send 函数体:\n$body",
            body.contains("0 until 3")
        )

        // 429 + retry_after 解析
        assertTrue(
            "send 函数体必须含 `429` 字面值（Telegram flood control HTTP code 检测）。",
            body.contains("429")
        )
        assertTrue(
            "send 函数体必须含 `retry_after` 字面值（Telegram Bot API response body 字段名）。\n实际 send 函数体:\n$body",
            body.contains("retry_after")
        )
        assertTrue(
            "send 函数体必须含 `parameters` 字面值（response body JSON 路径：`{\"parameters\":{\"retry_after\":N}}`）。\n实际 send 函数体:\n$body",
            body.contains("parameters")
        )

        // Markdown fallback 检测
        assertTrue(
            "send 函数体必须含 `parse` 字面值（用于 detect Markdown parse error，e.g. `can't parse entities`）。",
            body.contains("parse")
        )
        assertTrue(
            "send 函数体必须含 `Markdown` 字面值（parse_mode 设值或检测）。",
            body.contains("Markdown")
        )

        // 自愈分支
        assertTrue(
            "send 函数体必须含 `thread_not_found` 或 `message thread not found` 字面值（thread 自愈分支）。\n实际 send 函数体:\n$body",
            body.contains("thread_not_found") || body.contains("message thread not found")
        )
        assertTrue(
            "send 函数体必须含 `replied message not found` 字面值（reply_to 自愈分支）。\n实际 send 函数体:\n$body",
            body.contains("replied message not found")
        )
    }

    /**
     * TC-GW-013-b: _stripMarkdownToPlain + _splitForTelegram helpers 必须存在；send 函数体不再含静默 take()。
     */
    @Test
    fun `TC-GW-013-b helpers exist and silent take is gone`() {
        // helper 声明
        assertTrue(
            "Telegram.kt 必须含 `private fun _stripMarkdownToPlain(` 函数声明 —— Markdown fallback 用的去 markdown 字符 helper。",
            Regex("""private\s+fun\s+_stripMarkdownToPlain\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "Telegram.kt 必须含 `private fun _splitForTelegram(` 函数声明 —— 长消息按 4096 切多段 helper。",
            Regex("""private\s+fun\s+_splitForTelegram\s*\(""").containsMatchIn(source)
        )

        val body = extractSendBody()
        // 红线：不能再用 content.take(MAX_MESSAGE_LENGTH) 静默截断
        assertFalse(
            "send 函数体**不得**含 `content.take(MAX_MESSAGE_LENGTH)` —— 静默截断红线，长消息必须走 _splitForTelegram。\n实际 send 函数体:\n$body",
            Regex("""content\s*\.\s*take\s*\(\s*MAX_MESSAGE_LENGTH\s*\)""").containsMatchIn(body)
        )
    }

    /**
     * TC-GW-013-c (红线): SocketTimeoutException 不重试，直接返回失败（防重复发送）。
     */
    @Test
    fun `TC-GW-013-c socket timeout does not retry`() {
        val body = extractSendBody()
        assertTrue(
            "send 函数体必须含 `SocketTimeoutException` 字面值 —— 对齐 Python `TimedOut` 防重复发送红线。\n实际 send 函数体:\n$body",
            body.contains("SocketTimeoutException")
        )
        // SocketTimeoutException 后面必须紧跟 return（不重试），用窗口 500 字符抓
        val timeoutIdx = body.indexOf("SocketTimeoutException")
        val tail = body.substring(timeoutIdx, (timeoutIdx + 500).coerceAtMost(body.length))
        assertTrue(
            "SocketTimeoutException 后 500 字符内必须含 `return` —— 防重复发送红线（Python `telegram.py:1083-1084`）。\n实际窗口:\n$tail",
            tail.contains("return")
        )
    }

    // ----- helpers -----

    private fun hermesAndroidSrcMainRoot(): File {
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun telegramKtPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/gateway/platforms/Telegram.kt").path
}
