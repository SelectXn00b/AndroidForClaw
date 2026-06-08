package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-015 (2026-06-08)：`EnhancedAIService.runAgentLoopViaHermes` 必须在 `openAiMessages =
 * requestHistory.toOpenAiMessages()` 之后、首次发请求之前，对末尾 user OpenAI message 原地拼一段
 * `<memory-context>...</memory-context>` 围栏（fence 内容来自 `MemoryRepository.searchMemories`
 * + `MemoryManager.buildMemoryContextBlock`）。这是 Python `run_agent.py:9087-9107` 行为的 1:1
 * Kotlin 翻译——让 agent 自动"用上"过去的 #auto_summary / 用户记录，无需依赖 agent 主动调
 * `query_memory`。
 *
 * **测试策略**：`EnhancedAIService` 重度依赖 Android Context / OkHttp / Hermes agent loop /
 * ObjectBox，JVM mock ROI 极低；与 R-AGENT-013/014 同策略 —— 走源码字符串扫描守住 wiring。运行时
 * 正确性由手测 + §3 E2E 兜底（agent-level TOKEN 回显验收）。
 *
 * 对应 TC-AGENT-015-a..f / h（见 docs/hermes-test-cases.md）。TC-AGENT-015-g（落库前 sanitize
 * 防御）在 `MessageCoordinationDelegateSummaryStripWiringTest` 内。
 */
class EnhancedAIServiceMemoryContextInjectionWiringTest {

    private val source: String by lazy { stripComments(File(servicePath()).readText()) }
    private val runAgentLoopBlock: String by lazy {
        extractFunctionBlock(source, "runAgentLoopViaHermes")
    }

    /**
     * TC-AGENT-015-a: `runAgentLoopViaHermes` 必须接通 prefetch + fence 拼接的关键 wiring。
     */
    @Test
    fun `TC-AGENT-015-a runAgentLoopViaHermes wires prefetch and fence`() {
        // (1) 必须有 enableMemoryQuery gate
        assertTrue(
            "runAgentLoopViaHermes 必须读 enableMemoryQuery 形参做 gate —— " +
                "用户关了记忆开关时跳过 prefetch。\n实际函数体（前 3000 chars）:\n${runAgentLoopBlock.take(3000)}",
            Regex("""\bif\s*\(\s*enableMemoryQuery\b""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""\benableMemoryQuery\s*&&""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""\bwhen\s*\([^)]*enableMemoryQuery""").containsMatchIn(runAgentLoopBlock)
        )

        // (2) 必须 reference MemoryRepository（实例化或字段引用）
        assertTrue(
            "runAgentLoopViaHermes 必须使用 MemoryRepository 做 prefetch source —— " +
                "源码需 reference `MemoryRepository(`（实例化）或 `memoryRepository.searchMemories(`。",
            Regex("""\bMemoryRepository\s*\(""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""\bmemoryRepository\b""").containsMatchIn(runAgentLoopBlock)
        )

        // (3) 必须调 searchMemories
        assertTrue(
            "runAgentLoopViaHermes 必须调 searchMemories(...) 拉取 prefetch 结果。",
            Regex("""\.searchMemories\s*\(""").containsMatchIn(runAgentLoopBlock)
        )

        // (4) 必须调 buildMemoryContextBlock 包 fence
        assertTrue(
            "runAgentLoopViaHermes 必须调 buildMemoryContextBlock(...) 把 prefetch 结果包成 " +
                "`<memory-context>` fence —— hermes-android 已 1:1 翻译该 helper（MemoryManager.kt:353）。",
            Regex("""\bbuildMemoryContextBlock\s*\(""").containsMatchIn(runAgentLoopBlock)
        )
    }

    /**
     * TC-AGENT-015-b: 拼接结果必须写回 `openAiMessages` 末尾 user message。
     */
    @Test
    fun `TC-AGENT-015-b openAiMessages last user message gets fence appended`() {
        // 必须对 openAiMessages 做修改，且修改在 buildMemoryContextBlock 调用之后
        val buildIdx = Regex("""\bbuildMemoryContextBlock\s*\(""")
            .find(runAgentLoopBlock)?.range?.first ?: -1
        assertTrue(
            "找不到 buildMemoryContextBlock 调用 —— 先满足 TC-AGENT-015-a。",
            buildIdx >= 0
        )

        val afterBuild = runAgentLoopBlock.substring(buildIdx)
        // 接受多种修改 openAiMessages 的方式：set / [idx] = / replaceAll / removeAt+add / 直接重赋值
        val mutatesOpenAiMessages =
            Regex("""openAiMessages\s*\[""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*set\s*\(""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*replaceAll\s*\{""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*removeAt\s*\(""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*add\s*\(""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*lastIndex""").containsMatchIn(afterBuild) ||
                Regex("""openAiMessages\s*\.\s*indexOfLast""").containsMatchIn(afterBuild)
        assertTrue(
            "buildMemoryContextBlock 调用之后必须修改 openAiMessages 末尾 user message —— " +
                "源码需含对 openAiMessages 的索引赋值 / set / replaceAll / removeAt+add 等操作。\n" +
                "实际 buildMemoryContextBlock 之后的代码片段（前 1500 chars）:\n${afterBuild.take(1500)}",
            mutatesOpenAiMessages
        )
    }

    /**
     * TC-AGENT-015-c: prefetch 必须强制 limit ≤ 5（防 token 爆炸）。
     */
    @Test
    fun `TC-AGENT-015-c prefetch caps limit at 5`() {
        val hasCap =
            Regex("""coerceAtMost\s*\(\s*5\s*\)""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""minOf\s*\([^)]*\b5\b""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""limit\s*=\s*5\b""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""MAX_PREFETCH_LIMIT\s*=\s*5\b""").containsMatchIn(source) ||
                Regex("""PREFETCH_LIMIT\s*=\s*5\b""").containsMatchIn(source)
        assertTrue(
            "runAgentLoopViaHermes 的 prefetch 必须强制 limit ≤ 5（防 token 爆炸）—— " +
                "源码需出现 coerceAtMost(5) / minOf(..., 5) / limit = 5 / 同名 const = 5 之一。",
            hasCap
        )
    }

    /**
     * TC-AGENT-015-d: 单条 memory content 必须按 800 字符截断。
     */
    @Test
    fun `TC-AGENT-015-d prefetch truncates content at 800 chars`() {
        val hasTake =
            Regex("""\.take\s*\(\s*800\s*\)""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""MAX_PREFETCH_CONTENT_CHARS\s*=\s*800\b""").containsMatchIn(source) ||
                Regex("""PREFETCH_CONTENT_LIMIT\s*=\s*800\b""").containsMatchIn(source) ||
                Regex("""\.take\s*\(\s*\w*PREFETCH\w*\s*\)""").containsMatchIn(runAgentLoopBlock)
        assertTrue(
            "runAgentLoopViaHermes 的 prefetch 必须对单条 content 做 800 字符截断 —— " +
                "源码需含 take(800) 调用或同名常量定义。",
            hasTake
        )
    }

    /**
     * TC-AGENT-015-e: prefetch 结果必须排除 `#persistent_instruction` 节点。
     */
    @Test
    fun `TC-AGENT-015-e prefetch excludes persistent_instruction tag`() {
        // 必须含字面字符串
        assertTrue(
            "runAgentLoopViaHermes 的 prefetch 必须显式 reference `#persistent_instruction` " +
                "tag 字面值 —— 否则没法过滤。",
            runAgentLoopBlock.contains("#persistent_instruction")
        )

        // 必须有 filter / filterNot 调用过滤
        val hasFilter =
            Regex("""\.filterNot\s*\{""").containsMatchIn(runAgentLoopBlock) ||
                Regex("""\.filter\s*\{""").containsMatchIn(runAgentLoopBlock)
        assertTrue(
            "runAgentLoopViaHermes 必须用 filter / filterNot 把含 #persistent_instruction tag 的 " +
                "memory 节点剔除（已通过 R-AGENT-009/245 注入到 system prompt，避免 token 重复）。",
            hasFilter
        )
    }

    /**
     * TC-AGENT-015-f: prefetch 流程不得碰到任何持久化层（chatHistoryDelegate / saveCurrentChat /
     * addMessage / ChatMessage）—— 只在 openAiMessages 局部变量上拼接。
     */
    @Test
    fun `TC-AGENT-015-f prefetch never touches persisted chat history`() {
        // 取 buildMemoryContextBlock 调用前后 50 行作为 prefetch 块
        val buildIdx = Regex("""\bbuildMemoryContextBlock\s*\(""")
            .find(runAgentLoopBlock)?.range?.first ?: -1
        assertTrue("找不到 buildMemoryContextBlock 调用", buildIdx >= 0)

        val before = runAgentLoopBlock.substring(0, buildIdx)
            .lines().takeLast(30).joinToString("\n")
        val after = runAgentLoopBlock.substring(buildIdx)
            .lines().take(30).joinToString("\n")
        val prefetchWindow = "$before\n$after"

        // chatHistoryDelegate.saveCurrentChat / addMessage / ChatMessage(... 全在禁止之列
        val forbidden = listOf(
            "chatHistoryDelegate",
            "saveCurrentChat",
            ".addMessage(",
            ".addUserMessage(",
            ".addAiMessage("
        )
        forbidden.forEach { needle ->
            assertFalse(
                "prefetch 块（buildMemoryContextBlock 前后 30 行）不得 reference `$needle` —— " +
                    "fence 注入只允许在 openAiMessages 局部变量上做，绝不能写回持久化层。\n" +
                    "实际窗口:\n$prefetchWindow",
                prefetchWindow.contains(needle)
            )
        }
    }

    /**
     * TC-AGENT-015-h: prefetch 流程必须 try-catch 包围（ObjectBox / embedding 异常不能拖垮
     * agent loop）。
     */
    @Test
    fun `TC-AGENT-015-h prefetch wrapped in try catch`() {
        val buildIdx = Regex("""\bbuildMemoryContextBlock\s*\(""")
            .find(runAgentLoopBlock)?.range?.first ?: -1
        assertTrue("找不到 buildMemoryContextBlock 调用", buildIdx >= 0)

        val before = runAgentLoopBlock.substring(0, buildIdx)
            .lines().takeLast(40).joinToString("\n")
        val after = runAgentLoopBlock.substring(buildIdx)
            .lines().take(40).joinToString("\n")

        assertTrue(
            "prefetch 块必须被 try { ... } catch (...) { ... } 包围 —— " +
                "ObjectBox / embedding / network 异常不能拖垮 agent loop。\n" +
                "buildMemoryContextBlock 前 40 行:\n$before\n后 40 行:\n$after",
            before.contains("try {") && after.contains("catch")
        )
    }

    // ----- helpers -----

    private fun extractFunctionBlock(src: String, name: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun $name(") || it.contains("fun $name ")
        }
        check(startIdx >= 0) { "找不到 fun $name 签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val nextFunOffset = rest.indexOfFirst { line ->
            val t = line.trimStart()
            Regex("""^(private |internal |public |protected )?(suspend )?fun \w+\s*[(<]""")
                .containsMatchIn(t)
        }
        val endIdx = if (nextFunOffset < 0) lines.size else startIdx + 1 + nextFunOffset
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    private fun stripComments(src: String): String {
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
                        if (c == '\n') out.append('\n')
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

    private fun servicePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt"),
            File("app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate EnhancedAIService.kt — cwd=${File(".").absolutePath}")
    }
}
