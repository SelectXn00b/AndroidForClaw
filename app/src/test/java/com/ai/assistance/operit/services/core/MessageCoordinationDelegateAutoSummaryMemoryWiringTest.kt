package com.ai.assistance.operit.services.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-013 (2026-06-07)：APP 内聊天的自动摘要必须**强行**写入长期记忆（绕过 LLM 价值判官）。
 *
 * 用户决策：摘要是"用户聊天的浓缩上下文"，不能让 MEMORY 模型的 generateAnalysis 判官说"不值钱"就丢掉。
 * `MessageCoordinationDelegate` 的两条摘要触发路径（`launchAsyncSummaryForSend` 与 `summarizeHistory`）
 * 在成功 `addSummaryMessage` 之后，必须直接调 `MemoryRepository.saveMemory(...)` —— 绕过
 * `MemoryLibrary.saveMemoryAsync` / `generateAnalysis`。同时强制打 `#auto_summary` + `#chat:<chatId>` tag。
 *
 * **测试策略**：`MessageCoordinationDelegate` 重度依赖 Android Context / EnhancedAIService / ChatHistoryDelegate /
 * ApiPreferences，JVM mock ROI 极低；与 R-AGENT-010/011 同策略 —— 走源码字符串扫描守住 wiring。
 * 运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-013-a..i（见 docs/hermes-test-cases.md）。
 */
class MessageCoordinationDelegateAutoSummaryMemoryWiringTest {

    private val source: String by lazy { stripComments(File(delegatePath()).readText()) }
    private val sendBlock: String by lazy { extractFunctionBlock(source, "launchAsyncSummaryForSend") }
    private val historyBlock: String by lazy { extractFunctionBlock(source, "summarizeHistory") }
    /**
     * 摘要落档可以是"直接 inline saveMemory"也可以是"调本类的 helper 函数"。后者的好处是 DRY，
     * 避免两条摘要路径重复 20+ 行落档逻辑。本测试接受任一形式 —— 只要在路径函数体内出现
     * `saveMemory(` 或 `forcePersistSummaryToMemory(`（helper 名约定，符合 CLAUDE.md §0.3
     * 的"测试是行为契约而非实现细节"）。
     */
    private fun blockReachesSaveMemory(block: String): Boolean =
        Regex("""\bsaveMemory\s*\(""").containsMatchIn(block) ||
            Regex("""\bforcePersistSummaryToMemory\s*\(""").containsMatchIn(block)

    private fun blockSaveMemoryIndex(block: String): Int {
        val direct = Regex("""\bsaveMemory\s*\(""").find(block)?.range?.first ?: -1
        val helper = Regex("""\bforcePersistSummaryToMemory\s*\(""").find(block)?.range?.first ?: -1
        return when {
            direct >= 0 && helper >= 0 -> minOf(direct, helper)
            direct >= 0 -> direct
            else -> helper
        }
    }

    @Test
    fun `TC-AGENT-013-a launchAsyncSummaryForSend invokes MemoryRepository saveMemory`() {
        assertTrue(
            "launchAsyncSummaryForSend 必须 reference saveMemory(...)（直接或经 helper forcePersistSummaryToMemory）—— " +
                "否则发送阈值摘要永远不进长期记忆。\n实际函数体:\n$sendBlock",
            blockReachesSaveMemory(sendBlock)
        )
    }

    @Test
    fun `TC-AGENT-013-b summarizeHistory invokes MemoryRepository saveMemory`() {
        assertTrue(
            "summarizeHistory 必须 reference saveMemory(...)（直接或经 helper forcePersistSummaryToMemory）—— " +
                "否则 token-limit 摘要永远不进长期记忆。\n实际函数体:\n$historyBlock",
            blockReachesSaveMemory(historyBlock)
        )
    }

    @Test
    fun `TC-AGENT-013-c MessageCoordinationDelegate does not call MemoryLibrary saveMemoryAsync`() {
        // 硬约束：绕过 LLM 判官。R-AGENT-010 的 saveMemoryAsync 调用点在 HermesGatewayController，
        // MessageCoordinationDelegate 全文不得有真实代码 调 saveMemoryAsync。
        // （注释/KDoc 里提到对照禁止用法是允许的，故 stripComments 已剥离）。
        assertFalse(
            "MessageCoordinationDelegate 严禁调用 MemoryLibrary.saveMemoryAsync —— " +
                "R-AGENT-013 要求绕过 generateAnalysis 价值判官，摘要必须强行落库。",
            source.contains("MemoryLibrary.saveMemoryAsync") ||
                Regex("""\bsaveMemoryAsync\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun `TC-AGENT-013-d adds auto_summary tag`() {
        assertTrue(
            "摘要保存必须打 #auto_summary tag —— 源码中需出现 addTagToMemory + \"#auto_summary\" 字面字符串。",
            source.contains("\"#auto_summary\"") &&
                Regex("""addTagToMemory\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun `TC-AGENT-013-e adds chat source tag`() {
        // chat 来源 tag 用 "#chat:" 前缀拼接 chatId（字符串模板形式："#chat:$chatId"），
        // 让用户在 MemoryScreen 区分摘要来自哪个 chat。
        assertTrue(
            "摘要保存必须打来源 chat tag —— 源码中需出现 \"#chat:\" 字面字符串作为 tag 前缀。",
            source.contains("#chat:")
        )
    }

    @Test
    fun `TC-AGENT-013-f gates on enableMemoryQueryFlow`() {
        // 与 R-AGENT-010 同一开关。两条路径任一只要引用即可（可能 send 路径引用、history 路径复用同一变量）。
        assertTrue(
            "saveMemory 路径必须读 enableMemoryQueryFlow 开关 —— 与 R-AGENT-010 / APP UI 路径一致。",
            source.contains("enableMemoryQueryFlow")
        )
    }

    @Test
    fun `TC-AGENT-013-g saveMemory wrapped in try catch`() {
        // 落档调用（含 helper）必须有 try-catch 隔离 —— 不能因 ObjectBox / embedding 网络异常
        // 拖垮 addSummaryMessage / refreshStableContextWindow。两条路径任一引用落档的地方都要被 try 包围。
        listOf("launchAsyncSummaryForSend" to sendBlock, "summarizeHistory" to historyBlock).forEach { (name, block) ->
            val idx = blockSaveMemoryIndex(block)
            assertTrue("$name: 找不到 saveMemory / forcePersistSummaryToMemory 调用点", idx >= 0)
            val beforeWindow = block.substring(0, idx)
                .lines()
                .takeLast(20)
                .joinToString("\n")
            val afterWindow = block.substring(idx)
                .lines()
                .take(40)
                .joinToString("\n")
            assertTrue(
                "$name: 落档调用必须被 try-catch 包围。前 20 行:\n$beforeWindow\n后 40 行:\n$afterWindow",
                beforeWindow.contains("try {") && afterWindow.contains("catch")
            )
        }
    }

    @Test
    fun `TC-AGENT-013-h saveMemory called after addSummaryMessage`() {
        // 两条路径函数体内 addSummaryMessage 字面位置 < 落档调用位置 ——
        // 确保摘要先入 chat 历史，再落长期记忆。
        listOf("launchAsyncSummaryForSend" to sendBlock, "summarizeHistory" to historyBlock).forEach { (name, block) ->
            val addIdx = block.indexOf("addSummaryMessage(")
            val saveIdx = blockSaveMemoryIndex(block)
            assertTrue("$name: 找不到 addSummaryMessage 调用", addIdx >= 0)
            assertTrue("$name: 找不到 saveMemory / forcePersistSummaryToMemory 调用", saveIdx >= 0)
            assertTrue(
                "$name: 落档调用必须在 addSummaryMessage 之后调用（addIdx=$addIdx, saveIdx=$saveIdx）",
                addIdx < saveIdx
            )
        }
    }

    @Test
    fun `TC-AGENT-013-i memory source set to auto_summary`() {
        assertTrue(
            "新建的 Memory 实例必须设置 source = \"auto_summary\" —— 让 MemoryScreen EditMemoryDialog 的 source 字段可见来源。",
            source.contains("\"auto_summary\"")
        )
    }

    // ----- helpers -----

    private fun extractFunctionBlock(src: String, name: String): String {
        // 抓函数体：从签名行（含 `fun <name>(`）到下一个 top-level fun。
        // 用"下一个 fun"作为终点，避免大括号深度计数被 string templates 误导。
        // 注意：扫描时跳过 helper 自己（forcePersistSummaryToMemory 等）的开头被误判为终点。
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun $name(") || it.contains("fun $name ")
        }
        check(startIdx >= 0) { "找不到 fun $name 签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val nextFunOffset = rest.indexOfFirst { line ->
            val t = line.trimStart()
            // 匹配各种顶级 fun 起始（包括 modifiers 组合）
            Regex("""^(private |internal |public |protected )?(suspend )?fun \w+\s*[(<]""")
                .containsMatchIn(t)
        }
        val endIdx = if (nextFunOffset < 0) lines.size else startIdx + 1 + nextFunOffset
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    private fun stripComments(src: String): String {
        // 先剥块注释（/* ... */ 和 /** ... */），再剥行注释（//）。
        // 简单实现：状态机扫一次。string-literal 内的 /* */ // 字符不算注释。
        val out = StringBuilder()
        var i = 0
        var inString = false
        var inChar = false
        var inBlock = false
        var inLine = false
        while (i < src.length) {
            val c = src[i]
            val next = if (i + 1 < src.length) src[i + 1] else '\u0000'
            when {
                inLine -> {
                    if (c == '\n') {
                        inLine = false
                        out.append('\n')
                    }
                    i++
                }
                inBlock -> {
                    if (c == '*' && next == '/') {
                        inBlock = false
                        i += 2
                    } else {
                        if (c == '\n') out.append('\n') // 保留行号
                        i++
                    }
                }
                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) {
                        out.append(src[i + 1])
                        i += 2
                        continue
                    }
                    if (c == '"') inString = false
                    i++
                }
                inChar -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) {
                        out.append(src[i + 1])
                        i += 2
                        continue
                    }
                    if (c == '\'') inChar = false
                    i++
                }
                c == '/' && next == '/' -> { inLine = true; i += 2 }
                c == '/' && next == '*' -> { inBlock = true; i += 2 }
                c == '"' -> { inString = true; out.append(c); i++ }
                c == '\'' -> { inChar = true; out.append(c); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageCoordinationDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
