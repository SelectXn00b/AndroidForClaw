package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix (2026-06-15)：pending-event 路径的发送失败救命包对称对齐。
 *
 * 用户报告："gateway 有时候飞书和 telegram 无法收到结果"。Top-1 根因：当 agent 处理过程中
 * 用户又发新消息（触发 pending-event 路径），如果 pending 的回复发送失败，Run.kt 只 `Log.w`
 * 但**不**调 `onSendFailed?.invoke(...)` —— 结果是 UndeliveredReplyStore 不收录、Notifier 不弹
 * 通知，用户永远收不到 agent 的回复（既不在 chat 也不在本地通知）。
 *
 * 正常路径（line 445-461）已经做了正确的接线（TC-GW-172-b 已守）；本测试要求 pending 路径
 * （line 560-565）做对称接线。
 *
 * 对应 TC-GW-176-a / TC-GW-176-b（见 docs/hermes-test-cases.md）。
 */
class RunPendingSendFailureWiringTest {

    private val source: String by lazy { File(runKtPath()).readText() }

    /**
     * TC-GW-176-a: pending 失败分支必须 invoke onSendFailed。
     *
     * 抓取窗口：从 "Pending delivery failed after retry" 那行 Log.w 起前后 5 行；
     * 该窗口必须含 `onSendFailed?.invoke(...)` 且参数引用 `pendingEvent.source.chatId`
     * （不是初始 `event.source.chatId` —— pending 路径必须用 pendingEvent 自己的 chatId）。
     */
    @Test
    fun `TC-GW-176-a pending failure branch invokes onSendFailed`() {
        val lines = source.lines()
        val pendingFailIdx = lines.indexOfFirst {
            it.contains("Pending delivery failed after retry")
        }
        assertTrue(
            "找不到 'Pending delivery failed after retry' 日志行 —— Run.kt pending 分支可能被改。",
            pendingFailIdx >= 0
        )

        val start = (pendingFailIdx - 5).coerceAtLeast(0)
        val end = (pendingFailIdx + 6).coerceAtMost(lines.size)
        val window = lines.subList(start, end).joinToString("\n")

        assertTrue(
            "Run.kt pending 失败分支必须紧贴 'Pending delivery failed after retry' 调用 " +
                "onSendFailed?.invoke(...) —— 否则 pending 路径上发送失败时静默丢消息" +
                "（对称对齐 TC-GW-172-b 已守住的正常路径）。窗口：\n$window",
            Regex("""onSendFailed\?\.invoke\s*\(""").containsMatchIn(window)
        )

        assertTrue(
            "pending 路径的 onSendFailed?.invoke(...) 必须用 pendingEvent.source.chatId " +
                "（不是初始 event.source.chatId）—— 否则通知里 chatId 错位。窗口：\n$window",
            window.contains("pendingEvent.source.chatId")
        )
    }

    /**
     * TC-GW-176-b: pending 失败 Log.w 必须含 chatId + len + error 字面值。
     *
     * 对齐 TC-GW-170-a 已守住的正常路径要求：release 包诊断要能拿到 chatId 与文本长度。
     * 用 Log.w(...) 整调用的 `[^)]*` 跨行 regex 匹配（同 TC-GW-170-a 套路）。
     */
    @Test
    fun `TC-GW-176-b pending failure log carries chatId and length`() {
        val occurrences = Regex("""Log\.w\([^)]*Pending delivery failed after retry[^)]*\)""")
            .findAll(source).toList()
        assertTrue(
            "Run.kt 必须有 'Pending delivery failed after retry' Log.w 调用 —— 实际找到 ${occurrences.size} 处",
            occurrences.isNotEmpty()
        )

        val joined = occurrences.joinToString("\n") { it.value }
        assertTrue(
            "pending 失败 Log.w 必须 reference `chatId` —— 对齐 TC-GW-170-a 已守住的正常路径。\n实际：\n$joined",
            joined.contains("chatId")
        )
        assertTrue(
            "pending 失败 Log.w 必须 reference 文本长度（`len=` 或 `.length`）—— " +
                "对齐 TC-GW-170-a 已守住的正常路径。\n实际：\n$joined",
            joined.contains("len=") || joined.contains(".length")
        )
        assertTrue(
            "pending 失败 Log.w 必须 reference `error=` —— 对齐 TC-GW-170-a 已守住的正常路径。\n实际：\n$joined",
            joined.contains("error=")
        )
    }

    // ----- helpers -----

    private fun runKtPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Run.kt — cwd=${File(".").absolutePath}")
    }
}
