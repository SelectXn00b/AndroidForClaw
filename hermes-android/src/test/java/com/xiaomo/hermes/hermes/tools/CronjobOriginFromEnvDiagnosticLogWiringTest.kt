package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (continuation): Bug B 的 fix 已让 `_originFromEnv` 走 `getSessionEnv(...)`
 * 读 ThreadLocal（TC-AGENT-033-d 覆盖），但跨 `Dispatchers.IO` 切线程后 ThreadLocal 丢失
 * 时仍然会 silent return null。线上回归（jobId=d137dd1dd6c0 微信触发但 cron.log 写
 * `deliver mode=local originPlatform= originChatId=`）就是这个症状，**没有任何 log 提示
 * 是这一层失败**，导致排查只能靠肉眼比对 cron.log 多个字段。
 *
 * 修复办法：`_originFromEnv` 必须两条路径都打 log：
 *   - success 路径：`Log.i(TAG, "origin captured platform=$p chatId=$c threadId=$t")`
 *   - missing 路径：`Log.w(TAG, "origin missing platform=$p chatId=$c (ThreadLocal not propagated?)")`
 *
 * 这样下次类似回归从 logcat / cron.log 一行就能看出 ThreadLocal 是不是没传过来。
 *
 * 对应 TC-AGENT-033-j（见 docs/hermes-test-cases.md）。
 */
class CronjobOriginFromEnvDiagnosticLogWiringTest {

    private val source: String by lazy { File(cronjobToolsPath()).readText() }

    /**
     * 抓 `_originFromEnv()` 函数体。从 `fun _originFromEnv(` 起，跨大括号深度到 0。
     */
    private fun extractOriginFromEnvBody(): String {
        val anchor = source.indexOf("fun _originFromEnv(")
        if (anchor < 0) return ""
        val openBrace = source.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(openBrace, i + 1)
            }
            i++
        }
        return source.substring(openBrace)
    }

    /**
     * TC-AGENT-033-j: `_originFromEnv` 必须在 success / missing 两条路径都打诊断 log。
     */
    @Test
    fun `TC-AGENT-033-j _originFromEnv logs capture and missing path`() {
        val body = extractOriginFromEnvBody()
        assertTrue(
            "TC-AGENT-033-j: 找不到 `_originFromEnv` 函数体 —— 结构可能被改。",
            body.isNotEmpty()
        )

        // (1) 至少 2 次 logger 调用 (Log. 或 AppLogger.)
        val loggerCount = Regex("""\b(?:Log|AppLogger)\.[wide]\s*\(""").findAll(body).count()
        assertTrue(
            "TC-AGENT-033-j: `_originFromEnv` 函数体必须至少含 2 处 `Log.{i,w,d,e}(` 或 `AppLogger.{i,w,d,e}(` " +
                "调用（success / missing 各一）—— 实际找到 $loggerCount 处。\n实际函数体:\n$body",
            loggerCount >= 2
        )

        // (2) 必须含 "origin captured" 字符串（success 路径标记）
        assertTrue(
            "TC-AGENT-033-j: success 路径必须 log `origin captured ...` —— 这是 logcat / cron.log " +
                "排查 ThreadLocal 是否正确传过来 的唯一线索。\n实际函数体:\n$body",
            body.contains("origin captured")
        )

        // (3) 必须含 "origin missing" 字符串（missing 路径标记）
        assertTrue(
            "TC-AGENT-033-j: missing 路径必须 log `origin missing ...` —— 否则跨线程 ThreadLocal 丢失" +
                "时静默 return null，无法定位是哪一层失败。\n实际函数体:\n$body",
            body.contains("origin missing")
        )
    }

    // ----- helpers -----

    private fun cronjobToolsPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/tools/CronjobTools.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/CronjobTools.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate CronjobTools.kt — cwd=${File(".").absolutePath}")
    }
}
