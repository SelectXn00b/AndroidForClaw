package com.ai.assistance.operit.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-039 (2026-06-16)：`session_search` 工具 wiring 守护。
 *
 * R-AGENT-038 phase 1 把摘要写入聚合到 3 个 root 节点 + 冷归档 jsonl，但 agent **不知道**这些节点存在。
 * R-AGENT-039 暴露 `session_search` 工具（与 Python 上游 `tools/session_search_tool.py` 工具名一致）让
 * agent 在用户开口"翻找历史"时主动调；底层先接 ObjectBox `MemoryRepository.searchMemories`（root 节点
 * + 老 `#auto_summary` 节点都能命中）。本阶段**不**读 jsonl 冷归档（留给 R-AGENT-042）。
 *
 * **测试策略**：源码字符串扫描守 wiring（仿 R-AGENT-013 / R-AGENT-017 / R-AGENT-038 同范式）。
 * 行为正确性由 `MemoryQueryToolExecutorSessionSearchTest` 覆盖；agent-level 调用由 §3 E2E `test_tool_call_e2e.sh` 兜底。
 *
 * 对应 TC-AGENT-039-a..e（见 docs/hermes-test-cases.md）。
 */
class SessionSearchToolWiringTest {

    private val toolRegistrationSource: String by lazy { File(toolRegistrationPath()).readText() }
    private val executorSource: String by lazy { File(executorPath()).readText() }
    private val systemToolPromptsSource: String by lazy { File(systemToolPromptsPath()).readText() }
    private val systemPromptConfigSource: String by lazy { File(systemPromptConfigPath()).readText() }

    /**
     * TC-AGENT-039-a: `ToolRegistration.kt` 必须注册 `session_search`，executor 桥接到
     * `MemoryQueryToolExecutor`（复用既有 dispatcher）。
     */
    @Test
    fun `TC-AGENT-039-a tool registration declares session_search bridged to memory query executor`() {
        assertTrue(
            "ToolRegistration.kt 必须含 `\"session_search\"` 字面值 —— agent dispatch 按 tool name 查 executor。",
            toolRegistrationSource.contains("\"session_search\"")
        )
        // session_search 注册块附近必须 reference getMemoryQueryToolExecutor（复用现有 executor）
        val sessionIdx = toolRegistrationSource.indexOf("\"session_search\"")
        assertTrue("找不到 session_search 注册位置。", sessionIdx >= 0)
        // 取 session_search 字面值前后 600 字符的窗口判断（一个 registerTool 块大约这个尺度）
        val windowStart = (sessionIdx - 200).coerceAtLeast(0)
        val windowEnd = (sessionIdx + 600).coerceAtMost(toolRegistrationSource.length)
        val window = toolRegistrationSource.substring(windowStart, windowEnd)
        assertTrue(
            "session_search 注册块附近必须 reference `getMemoryQueryToolExecutor` —— 复用记忆查询 executor。\n" +
                "实际窗口:\n$window",
            window.contains("getMemoryQueryToolExecutor")
        )
    }

    /**
     * TC-AGENT-039-b: `MemoryQueryToolExecutor.invoke` 的 `when` 调度块必须含
     * `"session_search" -> executeSessionSearch(...)` 分支。
     */
    @Test
    fun `TC-AGENT-039-b executor dispatches session_search to executeSessionSearch branch`() {
        assertTrue(
            "MemoryQueryToolExecutor.kt 必须含 `\"session_search\" ->` 分支 —— invoke when 调度按 tool name。\n" +
                "实际未命中。",
            Regex(""""session_search"\s*->\s*""").containsMatchIn(executorSource)
        )
        assertTrue(
            "MemoryQueryToolExecutor.kt 必须含 `executeSessionSearch(` 字面 —— 调度分支体调用此私有 suspend 函数。",
            Regex("""executeSessionSearch\s*\(""").containsMatchIn(executorSource)
        )
    }

    /**
     * TC-AGENT-039-c: `SystemToolPrompts.kt` 必须给 `session_search` 提供 EN + CN 两段工具描述，
     * 各自参数列表包含 `query` + `limit` 两个核心参数。
     */
    @Test
    fun `TC-AGENT-039-c system tool prompts describe session_search params in both locales`() {
        // session_search 字面值至少出现两次（EN + CN 各一次的 ToolPrompt(name = "session_search"...)）
        val occurrences =
            Regex("""ToolPrompt\s*\(\s*name\s*=\s*"session_search"""").findAll(systemToolPromptsSource).count()
        assertTrue(
            "SystemToolPrompts.kt 必须含至少两处 `ToolPrompt(name = \"session_search\")` —— EN + CN 各一份。\n" +
                "实际出现次数: $occurrences",
            occurrences >= 2
        )

        // 抓 session_search 块到下一个顶层 ToolPrompt(/categoryFooter 之间。这比 [\s\S]*?\)\s*\) 更稳：
        // 后者会被内部 ToolParameterSchema 的 ) 截断。
        val sessionBlocks = extractSessionSearchBlocks(systemToolPromptsSource)
        assertTrue(
            "未能抽出 session_search 的两段 ToolPrompt 块（期望 2，实际 ${sessionBlocks.size}）。",
            sessionBlocks.size >= 2
        )
        for ((idx, block) in sessionBlocks.withIndex()) {
            assertTrue(
                "session_search 第 ${idx + 1} 段 ToolPrompt 必须含 `query` 参数声明。\n实际:\n$block",
                Regex("""name\s*=\s*"query"""").containsMatchIn(block)
            )
            assertTrue(
                "session_search 第 ${idx + 1} 段 ToolPrompt 必须含 `limit` 参数声明。\n实际:\n$block",
                Regex("""name\s*=\s*"limit"""").containsMatchIn(block)
            )
        }
    }

    /**
     * TC-AGENT-039-d: `session_search` 描述段**不**得含 `auto_extracted` / `auto_summary` 字面值
     * （守 R-AGENT-017-g 红线：prompt 不泄露内部 tag 机制）。
     */
    @Test
    fun `TC-AGENT-039-d session_search description does not leak internal tag names`() {
        val sessionBlocks = extractSessionSearchBlocks(systemToolPromptsSource)
        assertTrue("未能抽出 session_search ToolPrompt 块。", sessionBlocks.isNotEmpty())
        for ((idx, block) in sessionBlocks.withIndex()) {
            assertFalse(
                "session_search 第 ${idx + 1} 段 ToolPrompt 不得含 `auto_extracted` 字面 —— 保留 R-AGENT-017-g 不泄露内部 tag 机制的口径。\n实际:\n$block",
                block.contains("auto_extracted")
            )
            assertFalse(
                "session_search 第 ${idx + 1} 段 ToolPrompt 不得含 `auto_summary` 字面 —— 保留 R-AGENT-017-g 不泄露内部 tag 机制的口径。\n实际:\n$block",
                block.contains("auto_summary")
            )
        }
    }

    /**
     * TC-AGENT-039-e: `SystemPromptConfig.kt` 的 `GATEWAY_AWARENESS_EN` + `GATEWAY_AWARENESS_CN`
     * 必须各含一处 `session_search` 字面（教 agent "翻找历史"时主动调本工具）。
     */
    @Test
    fun `TC-AGENT-039-e prompt teaches session_search in both locales`() {
        val enBlock = extractConstBlock(systemPromptConfigSource, "GATEWAY_AWARENESS_EN")
        val cnBlock = extractConstBlock(systemPromptConfigSource, "GATEWAY_AWARENESS_CN")
        assertTrue("找不到 GATEWAY_AWARENESS_EN 常量体。", enBlock.isNotBlank())
        assertTrue("找不到 GATEWAY_AWARENESS_CN 常量体。", cnBlock.isNotBlank())
        assertTrue(
            "GATEWAY_AWARENESS_EN 必须含 `session_search` 字面 —— 教 agent 在用户要求翻找历史时主动调本工具。",
            enBlock.contains("session_search")
        )
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须含 `session_search` 字面 —— 教 agent 在用户要求翻找历史时主动调本工具。",
            cnBlock.contains("session_search")
        )
    }

    // ----- helpers -----

    /**
     * 抽出所有 `ToolPrompt(name = "session_search", ...)` 块（精确闭合到外层 `)`），
     * 用括号深度计数避免被内部 ToolParameterSchema(...) 的 `)` 截断。
     */
    private fun extractSessionSearchBlocks(src: String): List<String> {
        val out = mutableListOf<String>()
        val anchorRe = Regex("""ToolPrompt\s*\(\s*name\s*=\s*"session_search"""")
        for (match in anchorRe.findAll(src)) {
            val start = match.range.first
            // 找 anchor 中的第一个 `(`
            val openParenIdx = src.indexOf('(', match.range.first)
            if (openParenIdx < 0) continue
            var depth = 1
            var i = openParenIdx + 1
            var inString = false
            while (i < src.length && depth > 0) {
                val c = src[i]
                if (inString) {
                    if (c == '\\' && i + 1 < src.length) { i += 2; continue }
                    if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '(' -> depth++
                        ')' -> depth--
                    }
                }
                i++
            }
            if (depth == 0) out.add(src.substring(start, i))
        }
        return out
    }

    private fun extractConstBlock(src: String, name: String): String {
        // 匹配 `private const val NAME = """ ... """` 或 `const val NAME = """ ... """`
        val pattern = Regex(
            """(?:private\s+)?const\s+val\s+$name\s*=\s*"{3}([\s\S]*?)"{3}""",
            RegexOption.DOT_MATCHES_ALL
        )
        return pattern.find(src)?.groupValues?.get(1) ?: ""
    }

    private fun toolRegistrationPath(): String = locate(
        "src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt",
        "app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt",
    )

    private fun executorPath(): String = locate(
        "src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt",
        "app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt",
    )

    private fun systemToolPromptsPath(): String = locate(
        "src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt",
        "app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt",
    )

    private fun systemPromptConfigPath(): String = locate(
        "src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt",
        "app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt",
    )

    private fun locate(vararg candidates: String): String {
        return candidates.map { File(it) }.firstOrNull { it.exists() }?.path
            ?: error(
                "Cannot locate any of: ${candidates.joinToString()} — cwd=${File(".").absolutePath}"
            )
    }
}
