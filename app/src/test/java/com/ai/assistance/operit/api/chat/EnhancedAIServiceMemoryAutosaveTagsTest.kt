package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-011 (2026-06-06)：`EnhancedAIService` 里的 APP UI 路径调用
 * `MemoryLibrary.saveMemoryAsync` 时，**不得** 传 `extraTags` 参数，必须走默认 `emptyList()`，
 * 这样 APP 内聊天产生的记忆节点不会被打 `#gateway:` tag —— 只有 gateway 路径
 * (`HermesGatewayController.runHermesAgent`) 才传 `extraTags = listOf("#gateway:$platform")`。
 *
 * 两个 APP UI 调用点：
 *   - `handleTaskCompletion` 分支：agent 输出 `<complete>` 时自动总结 (line 1474 附近)
 *   - 手动触发分支（manual memory update，line 2105 附近）
 *
 * **测试策略**：`EnhancedAIService` 重依赖 Android Context / multiServiceManager，走源码字符串扫描。
 *
 * 对应 TC-AGENT-247-f（见 docs/hermes-test-cases.md）。
 */
class EnhancedAIServiceMemoryAutosaveTagsTest {

    private val source: String by lazy { stripLineComments(File(enhancedAIServicePath()).readText()) }

    @Test
    fun `TC-AGENT-247-f handleTaskCompletion saveMemoryAsync does not pass extraTags`() {
        // 找到所有 MemoryLibrary.saveMemoryAsync 调用点，每个后续 600 字符窗口都不得含 `extraTags`。
        // 600 字符足以覆盖一个 saveMemoryAsync 调用块（命名参数 + onSuccess/onError lambda）。
        var idx = source.indexOf("MemoryLibrary.saveMemoryAsync")
        var callIndex = 0
        var totalCalls = 0
        while (idx >= 0) {
            totalCalls++
            val callWindow = source.substring(idx, (idx + 600).coerceAtMost(source.length))
            assertTrue(
                "EnhancedAIService 第 ${callIndex + 1} 个 MemoryLibrary.saveMemoryAsync 调用块内出现了 `extraTags` 参数 —— " +
                    "APP UI 路径不应传 extraTags（必须走默认 emptyList()），" +
                    "否则 APP 内聊天产生的记忆会被打 `#gateway:` 之类 tag，污染 UI 过滤逻辑。\n" +
                    "实际调用窗口:\n$callWindow",
                !callWindow.contains("extraTags")
            )
            callIndex++
            idx = source.indexOf("MemoryLibrary.saveMemoryAsync", idx + 1)
        }
        // 保护性断言：至少有一处调用（防止 EnhancedAIService 里 saveMemoryAsync 被整个删掉
        // 或路径改名后这个测试无声沉默）。
        assertTrue(
            "在 EnhancedAIService 里找不到 MemoryLibrary.saveMemoryAsync 调用 —— APP UI 自动总结路径可能断了。",
            totalCalls > 0
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

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun enhancedAIServicePath(): String =
        File(appSrcMainRoot(), "api/chat/EnhancedAIService.kt").path
}
