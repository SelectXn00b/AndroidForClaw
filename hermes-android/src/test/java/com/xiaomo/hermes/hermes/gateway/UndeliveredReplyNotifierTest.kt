package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix (2026-06-06)：`UndeliveredReplyNotifier` 必须做这三件事：
 *
 *   1. 创建 NotificationChannel，id 含 `undelivered` 关键字（方便 Settings 里识别）
 *   2. 用 `NotificationManager.notify` 弹本地通知（不是飞书消息——飞书可能正好挂了）
 *   3. 点击通知后必须把 text 写入剪贴板（`ClipboardManager.setPrimaryClip`）
 *
 * JVM 单测里 NotificationManager + Context 强依赖 Android，走源码字符串扫描。
 * 运行时正确性由手动 smoke + §3 E2E 兜底。
 *
 * 对应 TC-GW-174-a（见 docs/hermes-test-cases.md）。
 */
class UndeliveredReplyNotifierTest {

    @Test
    fun `TC-GW-174-a notifies on local channel and copies text on click`() {
        val source = File(notifierPath()).readText()

        // 1. NotificationChannel id 含 "undelivered"
        assertTrue(
            "UndeliveredReplyNotifier 必须用一个 channel id 含 'undelivered' 的 NotificationChannel —— " +
                "方便用户在系统 Settings 识别这是哪个通知。",
            Regex("""NotificationChannel\s*\([^)]*"[^"]*undelivered""", RegexOption.IGNORE_CASE)
                .containsMatchIn(source) ||
                Regex(""""[^"]*undelivered[^"]*"""", RegexOption.IGNORE_CASE).containsMatchIn(source)
        )

        // 2. 必须调用 NotificationManager.notify
        assertTrue(
            "UndeliveredReplyNotifier 必须调用 NotificationManagerCompat.notify (或 NotificationManager.notify) " +
                "弹本地通知 —— 不是把通知发回飞书（飞书可能正在挂）",
            Regex("""(NotificationManagerCompat|NotificationManager)[^.]*\.notify\s*\(""")
                .containsMatchIn(source)
        )

        // 3. 必须涉及 ClipboardManager（点击通知 → 复制全文）
        assertTrue(
            "UndeliveredReplyNotifier 必须用 ClipboardManager（点击通知后把回复文本复制到剪贴板，" +
                "用户手动粘贴回飞书）",
            source.contains("ClipboardManager") || source.contains("CLIPBOARD_SERVICE")
        )

        // 4. 必须有 setPrimaryClip 或等价的剪贴板写入调用
        assertTrue(
            "UndeliveredReplyNotifier 必须调用 setPrimaryClip 写剪贴板",
            source.contains("setPrimaryClip") || source.contains("ClipData.newPlainText")
        )
    }

    // ----- helpers -----

    private fun notifierPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/UndeliveredReplyNotifier.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/UndeliveredReplyNotifier.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate UndeliveredReplyNotifier.kt — cwd=${File(".").absolutePath}")
    }
}
