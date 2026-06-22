package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-AGENT-045-i-5: `StandardChatManagerTool.startMessageToAIStream` 必须
 * 从 `tool.parameters` 读 `__origin_platform` / `__origin_chat_id`，并通过
 * 新增的 `originPlatformOverride` / `originChatIdOverride` 参数 forward 到
 * `core.sendUserMessage(...)`。
 *
 * 这是 C-route 5 层显式参数管道的第一段。`ExternalChatRequestExecutor`
 * 已经把 origin 写进 `AITool.parameters`（见 TC-AGENT-045-i-4），但工具层
 * 必须把它**取出来**并往下传，否则 `core.sendUserMessage` 收不到。
 *
 * 源码扫描测试（不实例化 Robolectric）。
 */
class StandardChatManagerToolOriginParamsTest {

    private val source: String by lazy { File(toolPath()).readText() }

    @Test
    fun `TC-AGENT-045-i-5 forwards origin params to ChatServiceCore`() {
        // 1) 必须从 tool.parameters 读 `__origin_platform`
        assertTrue(
            "StandardChatManagerTool.kt 必须从 tool.parameters 读 `__origin_platform` —— " +
                "C-route 起点（ExternalChatRequestExecutor 注入的 ToolParameter）。",
            Regex(
                """tool\.parameters\.find\s*\{\s*it\.name\s*==\s*"__origin_platform"\s*\}"""
            ).containsMatchIn(source)
        )

        // 2) 必须从 tool.parameters 读 `__origin_chat_id`
        assertTrue(
            "StandardChatManagerTool.kt 必须从 tool.parameters 读 `__origin_chat_id`。",
            Regex(
                """tool\.parameters\.find\s*\{\s*it\.name\s*==\s*"__origin_chat_id"\s*\}"""
            ).containsMatchIn(source)
        )

        // 3) 必须把读出来的值 forward 给 `core.sendUserMessage(...)` —— 至少出现
        //    `originPlatformOverride =` 命名实参
        assertTrue(
            "StandardChatManagerTool.kt 必须把 `__origin_platform` 通过 `originPlatformOverride =` 命名参数 " +
                "forward 到 `core.sendUserMessage(...)` —— 否则停在工具层没传下去。",
            Regex("""originPlatformOverride\s*=""").containsMatchIn(source)
        )
        assertTrue(
            "StandardChatManagerTool.kt 必须把 `__origin_chat_id` 通过 `originChatIdOverride =` 命名参数 " +
                "forward 到 `core.sendUserMessage(...)`。",
            Regex("""originChatIdOverride\s*=""").containsMatchIn(source)
        )

        // 4) `originPlatformOverride =` 必须出现在 `core.sendUserMessage(` 调用块内 —— 简单宽松校验：
        //    至少有一处 `core.sendUserMessage(` 调用，且其后 600 字符内含 `originPlatformOverride =`。
        val sendCalls = Regex("""core\.sendUserMessage\s*\(""").findAll(source).map { it.range.first }.toList()
        assertTrue(
            "StandardChatManagerTool.kt 必须保留至少一个 `core.sendUserMessage(...)` 调用。",
            sendCalls.isNotEmpty()
        )
        val originForwarded = sendCalls.any { idx ->
            val tail = source.substring(idx, (idx + 1500).coerceAtMost(source.length))
            tail.contains("originPlatformOverride =") && tail.contains("originChatIdOverride =")
        }
        assertTrue(
            "至少一个 `core.sendUserMessage(...)` 调用块内必须含 `originPlatformOverride = ...` + " +
                "`originChatIdOverride = ...` 两个命名实参 —— 守 forward 真的发生在 sendUserMessage 调用上而不是别处。",
            originForwarded
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun toolPath(): String =
        File(
            appSrcMainRoot(),
            "core/tools/defaultTool/standard/StandardChatManagerTool.kt"
        ).path
}
