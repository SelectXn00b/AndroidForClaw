package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 (2026-06-15) Bug B: `CronjobTools._originFromEnv` 必须经 `getSessionEnv(...)`
 * 走 ThreadLocal，不能用 `System.getenv("HERMES_SESSION_*")` —— Android 上 `System.getenv`
 * 永远拿不到这些动态注入的 session vars，导致 cron job 创建时 origin 全是 null，
 * `deliver=origin` 时 `_resolveSingleDeliveryTarget` 拿不到 platform/chat_id 直接 return null。
 *
 * 对齐 Python `tools/cronjob_tools.py:71-88 _origin_from_env`。
 *
 * 对应 TC-AGENT-033-d（见 docs/hermes-test-cases.md）。
 */
class CronjobOriginFromEnvWiringTest {

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
     * TC-AGENT-033-d: `_originFromEnv` 用 getSessionEnv，**不**用 System.getenv 读 HERMES_SESSION_*。
     */
    @Test
    fun `TC-AGENT-033-d _originFromEnv reads ThreadLocal not OS env`() {
        val body = extractOriginFromEnvBody()
        assertTrue(
            "TC-AGENT-033-d: 找不到 `_originFromEnv` 函数体 —— 结构可能被改。",
            body.isNotEmpty()
        )

        // 至少 3 处 getSessionEnv 调用（platform / chat_id / thread_id）
        val getSessionEnvCount = Regex("""\bgetSessionEnv\s*\(""").findAll(body).count()
        assertTrue(
            "TC-AGENT-033-d: `_originFromEnv` 函数体必须至少含 3 处 `getSessionEnv(` 调用 " +
                "(platform / chat_id / thread_id 三个 session var 读取) —— 实际找到 $getSessionEnvCount 处。\n" +
                "实际函数体:\n$body",
            getSessionEnvCount >= 3
        )

        // 红线：不得再含 System.getenv("HERMES_SESSION_") 字面值
        val systemGetenvPattern = Regex("""System\.getenv\s*\(\s*"HERMES_SESSION_""")
        assertFalse(
            "TC-AGENT-033-d 红线: `_originFromEnv` 函数体**不得**含 `System.getenv(\"HERMES_SESSION_...\")` " +
                "字面值 —— Android 上拿不到，必须走 `getSessionEnv(\"HERMES_SESSION_...\")`。\n" +
                "实际函数体:\n$body",
            systemGetenvPattern.containsMatchIn(body)
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
