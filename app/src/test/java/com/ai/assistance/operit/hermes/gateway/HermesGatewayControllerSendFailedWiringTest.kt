package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix (2026-06-06)：`HermesGatewayController.start()` 必须把 `UndeliveredReplyStore`
 * 和 `UndeliveredReplyNotifier` 接到 `GatewayRunner.onSendFailed` 上 —— 否则 Store + Notifier
 * 写了也是死代码。
 *
 * **测试策略**：`HermesGatewayController.start()` 强依赖 Android Context / GatewayRunner /
 * suspend 启动，JVM mock 收益低。改走源码字符串扫描（参考 `PersistentInstructionInjectionTest`
 * 成熟模式）：守住"agentRunner 设置后必须紧跟 onSendFailed 设置且内部调用 Store + Notifier"。
 *
 * 对应 TC-GW-175-a（见 docs/hermes-test-cases.md）。
 */
class HermesGatewayControllerSendFailedWiringTest {

    @Test
    fun `TC-GW-175-a wires Store and Notifier into onSendFailed`() {
        val source = stripLineComments(File(controllerPath()).readText())

        // 1. 必须设置 instance.onSendFailed
        assertTrue(
            "HermesGatewayController.start() 必须设置 instance.onSendFailed —— " +
                "否则 GatewayRunner 触发失败回调时没人接，Store + Notifier 形同虚设。",
            Regex("""instance\.onSendFailed\s*=""").containsMatchIn(source)
        )

        // 2. onSendFailed 的 lambda 体内必须 reference UndeliveredReplyStore（接 Store）
        // 找 onSendFailed = { ... } 块，取后 15 行作为窗口
        val lines = source.lines()
        val wiringIdx = lines.indexOfFirst { it.contains("instance.onSendFailed") }
        assertTrue("找不到 instance.onSendFailed 设置点", wiringIdx >= 0)
        val window = lines.subList(wiringIdx, (wiringIdx + 15).coerceAtMost(lines.size))
            .joinToString("\n")

        assertTrue(
            "onSendFailed lambda 内必须 reference UndeliveredReplyStore 把失败 entry 存盘 —— " +
                "实际窗口:\n$window",
            window.contains("UndeliveredReplyStore")
        )

        assertTrue(
            "onSendFailed lambda 内必须 reference UndeliveredReplyNotifier 弹本地通知 —— " +
                "实际窗口:\n$window",
            window.contains("UndeliveredReplyNotifier")
        )
    }

    // ----- helpers -----

    private fun stripLineComments(src: String): String =
        src.lines().joinToString("\n") { line ->
            val idx = findUncommentedSlashSlash(line)
            if (idx >= 0) line.substring(0, idx) else line
        }

    private fun findUncommentedSlashSlash(line: String): Int {
        var i = 0
        var inString = false
        while (i < line.length - 1) {
            val c = line[i]
            val next = line[i + 1]
            when {
                c == '\\' -> { i += 2; continue }
                inString && c == '"' -> inString = false
                !inString && c == '"' -> inString = true
                !inString && c == '/' && next == '/' -> return i
            }
            i++
        }
        return -1
    }

    private fun controllerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"),
            File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate HermesGatewayController.kt — cwd=${File(".").absolutePath}")
    }
}
