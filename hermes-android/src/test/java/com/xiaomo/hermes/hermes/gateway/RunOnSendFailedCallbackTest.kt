package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix (2026-06-06)：飞书发送失败救命包契约 in `Run.kt` + `GatewayRunner`。
 *
 * 守住两个接线点：
 *   1. `GatewayRunner` 必须暴露 `onSendFailed` 回调属性（仿 `agentRunner` 模式）
 *   2. `Run.kt` 最终失败分支必须 invoke 该回调，第一次失败不调（避免 retry 救活的误报）
 *
 * 对应 TC-GW-172-a / TC-GW-172-b（见 docs/hermes-test-cases.md）。
 */
class RunOnSendFailedCallbackTest {

    /**
     * TC-GW-172-a: `GatewayRunner` 必须定义 `onSendFailed` 回调属性。
     * 类型 lambda：(platform, chatId, text, error) -> Unit。
     */
    @Test
    fun `TC-GW-172-a defines onSendFailed callback property`() {
        val source = File(runKtPath()).readText()

        // 必须有 @Volatile + var onSendFailed
        val hasVolatile = Regex("""@Volatile\s+var\s+onSendFailed""").containsMatchIn(source)
        assertTrue(
            "GatewayRunner 必须定义 @Volatile var onSendFailed —— 仿 agentRunner 的 platform-bridge 模式，" +
                "用于通知 controller 哪条回复发不出去。",
            hasVolatile
        )

        // 必须是 4 参 lambda，且包含 platform / chatId / text / error 关键词
        val signature = Regex(
            """var\s+onSendFailed\s*:\s*\(\s*[^)]*platform[^)]*chatId[^)]*text[^)]*error[^)]*\)\s*->\s*Unit""",
            RegexOption.IGNORE_CASE
        )
        assertTrue(
            "onSendFailed 签名必须是 (platform, chatId, text, error) -> Unit —— 4 个字段缺一不可" +
                "（controller 需要全部信息才能存盘 + 弹通知）。",
            signature.containsMatchIn(source)
        )
    }

    /**
     * TC-GW-172-b: `Run.kt` 最终失败分支（retry 也失败）必须 invoke `onSendFailed?.invoke(...)`。
     * 第一次失败那个分支**不**调（注释里要说明 retry 可能救活）。
     */
    @Test
    fun `TC-GW-172-b invokes onSendFailed only on final failure`() {
        val source = File(runKtPath()).readText()

        // 找最终失败分支：含 "Failed to send response after retry" 的那行附近
        val lines = source.lines()
        val finalFailIdx = lines.indexOfFirst { it.contains("Failed to send response after retry") }
        assertTrue(
            "找不到 'Failed to send response after retry' 日志行，Run.kt 结构可能被改 —— " +
                "TC-GW-170-a 已守住该行存在性，请先看那个测试是否也红",
            finalFailIdx >= 0
        )

        // 取该行前后 5 行作为窗口，必须包含 onSendFailed?.invoke
        val start = (finalFailIdx - 5).coerceAtLeast(0)
        val end = (finalFailIdx + 6).coerceAtMost(lines.size)
        val window = lines.subList(start, end).joinToString("\n")

        assertTrue(
            "Run.kt 最终失败分支必须紧贴 'Failed to send response after retry' 日志调用 " +
                "onSendFailed?.invoke(platform, chatId, text, error) —— 否则 controller 收不到信号，" +
                "Store + Notifier 都不会被触发。窗口：\n$window",
            Regex("""onSendFailed\?\.invoke\s*\(""").containsMatchIn(window)
        )

        // 第一次失败那个分支（"First delivery attempt failed"）周围**不**应有 onSendFailed.invoke
        // 因为 retry 可能救活；如果第一次就触发救命包，retry 成功了反而误报"未送达"。
        val firstFailIdx = lines.indexOfFirst { it.contains("First delivery attempt failed") }
        if (firstFailIdx >= 0) {
            val firstStart = (firstFailIdx - 2).coerceAtLeast(0)
            val firstEnd = (firstFailIdx + 5).coerceAtMost(lines.size)
            val firstWindow = lines.subList(firstStart, firstEnd).joinToString("\n")
            assertTrue(
                "Run.kt 第一次失败分支不应 invoke onSendFailed —— retry 可能救活，过早触发会误报。" +
                    "窗口：\n$firstWindow",
                !Regex("""onSendFailed\?\.invoke\s*\(""").containsMatchIn(firstWindow)
            )
        }
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
