package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-001 bugfix (2026-06-06)：飞书双向偶发丢消息。
 *
 * `Run.kt:425` 出站重试失败原本只 `Log.w(_TAG, "Failed to send response after retry: ${result.error}")`
 * —— release 包里看到这一行也没法定位是哪个 chat / 哪条消息丢了。Commit 3 升级两处出站
 * 失败日志（first-attempt + final-retry），把 `chatId` + 消息长度 + error 都打出来。
 *
 * **测试策略**：源码字符串扫描（`Run.kt` 在 hermes-android 里，单测里跑要起整个
 * GatewayRunner，依赖 Android Context + GatewayConfig，JVM 单测不可行）。这个套路在
 * 本仓库里有先例（`PersistentInstructionInjectionTest` / `MemoryDedupTest`）。
 *
 * 对应 TC-GW-170-a / TC-GW-170-b（见 docs/hermes-test-cases.md）。
 */
class RunSendFailureLoggingTest {

    /**
     * TC-GW-170-a: 最终重试失败那行 `Log.w`，必须把 chatId + 文本长度 + error 都带上。
     */
    @Test
    fun `TC-GW-170-a final send-failure log carries chatId and length`() {
        val source = stripLineComments(File(runKtPath()).readText())

        // 找 "Failed to send response after retry" 那行
        val occurrences = Regex("""Log\.w\([^)]*Failed to send response after retry[^)]*\)""")
            .findAll(source).toList()

        assertTrue(
            "Run.kt 必须有 'Failed to send response after retry' 日志行 —— 实际找到 ${occurrences.size} 处",
            occurrences.isNotEmpty()
        )

        // 该日志行必须 reference chatId 和 sendText 长度（防止 release 包里只看到一句模糊错误）
        val joined = occurrences.joinToString("\n") { it.value }
        assertTrue(
            "Run.kt 'Failed to send response after retry' 日志必须 reference chatId —— 否则丢消息时不知道是哪个 chat",
            joined.contains("chatId")
        )
        assertTrue(
            "Run.kt 'Failed to send response after retry' 日志必须带文本长度（如 `len=` 或 `.length`）" +
                "—— 帮助区分 '空回复被丢' vs '正常长度被丢'",
            joined.contains("len=") || joined.contains(".length")
        )
    }

    /**
     * TC-GW-170-b: 第一次发送失败、要 retry 之前那行 `Log.w`，也必须带 chatId。
     */
    @Test
    fun `TC-GW-170-b first attempt failure log carries chatId`() {
        val source = stripLineComments(File(runKtPath()).readText())

        // 找 "First delivery attempt failed" 那行
        val occurrences = Regex("""Log\.w\([^)]*First delivery attempt failed[^)]*\)""")
            .findAll(source).toList()

        assertTrue(
            "Run.kt 必须有 'First delivery attempt failed' 日志行 —— 实际找到 ${occurrences.size} 处",
            occurrences.isNotEmpty()
        )

        val joined = occurrences.joinToString("\n") { it.value }
        assertTrue(
            "Run.kt 'First delivery attempt failed' 日志必须 reference chatId —— " +
                "用于 grep 出'某个 chat 在某时间窗的失败链'",
            joined.contains("chatId")
        )
    }

    // ----- helpers -----

    /** 剥掉 Kotlin 单行注释（避免注释里的"反模式样本"被 regex 撞上） */
    private fun stripLineComments(src: String): String =
        src.lines().joinToString("\n") { line ->
            val idx = findUncommentedSlashSlash(line)
            if (idx >= 0) line.substring(0, idx) else line
        }

    private fun findUncommentedSlashSlash(line: String): Int {
        var i = 0
        var inString = false
        var inChar = false
        while (i < line.length - 1) {
            val c = line[i]
            val next = line[i + 1]
            when {
                c == '\\' -> { i += 2; continue }
                inString && c == '"' -> inString = false
                inChar && c == '\'' -> inChar = false
                !inString && !inChar && c == '"' -> inString = true
                !inString && !inChar && c == '\'' -> inChar = true
                !inString && !inChar && c == '/' && next == '/' -> return i
            }
            i++
        }
        return -1
    }

    private fun runKtPath(): String {
        // hermes-android 单测的 cwd 通常是模块根
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Run.kt — cwd=${File(".").absolutePath}")
    }
}
