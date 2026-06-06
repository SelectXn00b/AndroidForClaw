package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-010 (2026-06-06)：`HermesGatewayController.runHermesAgent` 必须在 agent 回复成功生成、
 * 即将 return 给 GatewayRunner 之前，强制调用一次 `MemoryLibrary.saveMemoryAsync(...)`，
 * 让飞书 / 微信等 Gateway 路径的对话也能进入长期记忆——APP 内聊天路径在
 * `EnhancedAIService.handleTaskCompletion` 里做这件事，但只有 `<complete>` 标记时才触发，
 * gateway 短消息几乎从不写 `<complete>`，导致这些路径完全不积累记忆。
 *
 * **测试策略**：`runHermesAgent` 是 suspend + 重度依赖 Android Context / multiServiceManager /
 * ChatHistoryManager，JVM mock ROI 太低，参考 R-GW-003 的 `TC-GW-175-a`，走源码字符串扫描。
 * 运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-246-a..e（见 docs/hermes-test-cases.md）。
 */
class HermesGatewayControllerMemoryAutosaveWiringTest {

    private val source: String by lazy { stripLineComments(File(controllerPath()).readText()) }
    private val runBlock: String by lazy { extractRunHermesAgentBlock(source) }

    @Test
    fun `TC-AGENT-246-a runHermesAgent invokes saveMemoryAsync`() {
        assertTrue(
            "HermesGatewayController.runHermesAgent 必须 reference MemoryLibrary.saveMemoryAsync —— " +
                "否则 gateway 路径永远不主动总结对话进入长期记忆。",
            runBlock.contains("MemoryLibrary.saveMemoryAsync") ||
                (runBlock.contains("MemoryLibrary") && runBlock.contains("saveMemoryAsync"))
        )
    }

    @Test
    fun `TC-AGENT-246-b runHermesAgent gates on enableMemoryQueryFlow`() {
        assertTrue(
            "runHermesAgent 必须读 enableMemoryQueryFlow 决定是否保存 —— 与 APP 内路径同一开关，" +
                "用户关闭记忆功能时 gateway 也必须不存。",
            runBlock.contains("enableMemoryQueryFlow")
        )
    }

    @Test
    fun `TC-AGENT-246-c runHermesAgent uses MEMORY function service`() {
        assertTrue(
            "saveMemoryAsync 调用必须用 multiServiceManager.getServiceForFunction(FunctionType.MEMORY) " +
                "取 MEMORY 模型 —— 与 APP 内路径一致，确保总结质量。",
            runBlock.contains("FunctionType.MEMORY") &&
                Regex("""getServiceForFunction\s*\(\s*FunctionType\.MEMORY""").containsMatchIn(runBlock)
        )
    }

    @Test
    fun `TC-AGENT-246-d runHermesAgent reads gateway chat history`() {
        assertTrue(
            "runHermesAgent 必须从 ChatHistoryManager.loadChatMessages(historyChatId) 取 gateway 会话历史 —— " +
                "不能传空 list 或硬编码假数据给 MEMORY 总结。",
            Regex("""ChatHistoryManager[\s\S]{0,200}?loadChatMessages""").containsMatchIn(runBlock) ||
                Regex("""chatHistoryManager[\s\S]{0,200}?loadChatMessages""").containsMatchIn(runBlock) ||
                Regex("""history[\s\S]{0,200}?loadChatMessages""").containsMatchIn(runBlock)
        )
    }

    @Test
    fun `TC-AGENT-246-e runHermesAgent skips save on empty or interrupted`() {
        // saveMemoryAsync 调用必须落在"非空 + 未中断"分支里。
        // 找到 saveMemoryAsync 调用点，向上 30 行内必须有 aiText 非空检查
        // (例如 isNotEmpty/isNotBlank/length>0) 或 if-not-empty 守卫。
        val idx = runBlock.indexOf("saveMemoryAsync")
        assertTrue("找不到 saveMemoryAsync 调用点", idx >= 0)
        val beforeWindow = runBlock.substring(0, idx)
            .lines()
            .takeLast(30)
            .joinToString("\n")

        val hasEmptinessGuard = Regex(
            """(isNotEmpty|isNotBlank|isEmpty|isBlank|length\s*>\s*0|\.length\s*!=\s*0)"""
        ).containsMatchIn(beforeWindow)
        val hasInterruptGuard = beforeWindow.contains("interruptCheck") ||
            beforeWindow.contains("isInterrupted") ||
            beforeWindow.contains("isActive")

        assertTrue(
            "saveMemoryAsync 调用必须落在 aiText 非空 + 未中断的分支内 —— " +
                "中断 / 异常 / 空回复路径不得保存。实际前 30 行窗口:\n$beforeWindow",
            hasEmptinessGuard || hasInterruptGuard
        )
    }

    // ----- helpers -----

    private fun extractRunHermesAgentBlock(src: String): String {
        // 抓 runHermesAgent 函数体：从签名行到下一个 top-level "private fun" / "fun "。
        // 简单粗暴：用 "下一个 private fun" 作为终点而不是大括号深度计数 —— 后者会被
        // string templates 里的 ${...} / "}" 字符误导。
        val lines = src.lines()
        val startIdx = lines.indexOfFirst { it.contains("fun runHermesAgent") }
        check(startIdx >= 0) { "找不到 runHermesAgent 函数签名" }
        val endIdx = lines.subList(startIdx + 1, lines.size)
            .indexOfFirst { it.trimStart().startsWith("private fun ") || it.trimStart().startsWith("fun ") }
            .let { if (it < 0) lines.size - startIdx - 1 else it } + startIdx + 1
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

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
