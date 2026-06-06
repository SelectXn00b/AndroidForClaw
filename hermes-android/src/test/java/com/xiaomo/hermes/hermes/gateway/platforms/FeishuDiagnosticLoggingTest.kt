package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-002 bugfix (2026-06-06)：飞书入站偶发不到。
 *
 * 静态审查发现两类"沉默吞错"点在 release 包里完全没有证据可查：
 *
 *   1. dedup 命中（`Feishu.kt:561-564`）—— `Log.d`，release 包 strip
 *   2. allowlist 拒绝（`Feishu.kt:567-570`）—— `Log.d`，release 包 strip
 *   3. SDK WS 静默断线 —— 启动后只有一句 `Starting official Feishu WS client`，
 *      之后再无任何活动证据，无法区分"网静"vs"WS 死了"
 *
 * Commit 3 把 (1)(2) 升级到 `Log.i`，并在 (3) 之后加一条 SDK 启动后的 liveness
 * 日志（让我们至少能看到"WS 已经握手成功并收到第一个 ping/事件"）。
 *
 * **测试策略**：源码字符串扫描。
 *
 * 对应 TC-GW-171-a / TC-GW-171-b / TC-GW-171-c（见 docs/hermes-test-cases.md）。
 */
class FeishuDiagnosticLoggingTest {

    /**
     * TC-GW-171-a: dedup 命中分支必须升级到 `Log.i`，让 release 包能看到
     * "这个 message_id 是因为 dedup 被丢的，不是因为没收到"。
     */
    @Test
    fun `TC-GW-171-a duplicate hit logged at INFO`() {
        val source = File(feishuKtPath()).readText()

        // 抽 isDuplicate 调用块（前后 3 行）
        val lines = source.lines()
        val idx = lines.indexOfFirst { it.contains("_dedup.isDuplicate") }
        assertTrue("找不到 _dedup.isDuplicate 调用，Feishu.kt 结构可能被改", idx >= 0)

        // 取该行 + 后 6 行作为窗口
        val window = lines.subList(idx, (idx + 7).coerceAtMost(lines.size)).joinToString("\n")

        // 必须有 Log.i (不是 Log.d) 且消息内容含 "Duplicate"
        assertTrue(
            "Feishu.kt 的 dedup 命中分支必须用 `Log.i` 打日志（release 包不 strip），" +
                "实际窗口:\n$window",
            Regex("""Log\.i\([^)]*[Dd]uplicate""").containsMatchIn(window)
        )
    }

    /**
     * TC-GW-171-b: allowlist 拒绝分支也必须升级到 `Log.i`。
     */
    @Test
    fun `TC-GW-171-b allowlist rejection logged at INFO`() {
        val source = File(feishuKtPath()).readText()

        // 找 allowlist 拒绝点：通常会有 "not in allowlist" / "User not in allowlist" 字串
        val occurrences = Regex("""Log\.[idw]\([^)]*[Aa]llowlist[^)]*\)""")
            .findAll(source).toList()

        assertTrue(
            "Feishu.kt 必须有 allowlist 拒绝日志 —— 实际找到 ${occurrences.size} 处",
            occurrences.isNotEmpty()
        )

        // 至少有一条是 Log.i（升级后）；不再允许全部是 Log.d
        val infoOrWarn = occurrences.filter {
            it.value.startsWith("Log.i") || it.value.startsWith("Log.w")
        }
        assertTrue(
            "Feishu.kt 的 allowlist 日志必须至少有一条 Log.i 或 Log.w（release 包要看到证据）—— " +
                "实际所有匹配:\n${occurrences.joinToString("\n") { it.value }}",
            infoOrWarn.isNotEmpty()
        )
    }

    /**
     * TC-GW-171-c: SDK WS 启动 (`Starting official Feishu WS client`) 之后必须有
     * "live 证据"日志（如 onConnect / onMessage 入口处的一行 Log.i），帮助区分
     * "WS 卡死了" vs "刚好没人发消息"。
     *
     * 实现方式：在 SDK 的 `onP2MessageReceiveV1.handle` 入口加一行 `Log.i`，
     * 不影响转发逻辑。
     */
    @Test
    fun `TC-GW-171-c WS lifecycle has post-start liveness log`() {
        val source = File(feishuKtPath()).readText()

        // SDK handle() 入口必须有 Log.i 显示 "WS event"/"received from SDK" 之类的活体证据
        // 这条 commit 3 必加的日志位于 onP2MessageReceiveV1 内部
        val lines = source.lines()
        val handleStart = lines.indexOfFirst { it.contains("onP2MessageReceiveV1") }
        assertTrue("找不到 onP2MessageReceiveV1 注册点", handleStart >= 0)

        // 取后 30 行（涵盖 handle 内部）
        val window = lines.subList(handleStart, (handleStart + 30).coerceAtMost(lines.size))
            .joinToString("\n")

        assertTrue(
            "Feishu.kt 的 onP2MessageReceiveV1 handler 必须有一条 `Log.i` 作为 'WS 收到事件' " +
                "的活体证据日志 —— 否则无法区分'WS 静默死了' vs '没人发消息'。窗口:\n$window",
            Regex("""Log\.i\(""").containsMatchIn(window)
        )
    }

    // ----- helpers -----

    private fun feishuKtPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Feishu.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Feishu.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Feishu.kt — cwd=${File(".").absolutePath}")
    }
}
