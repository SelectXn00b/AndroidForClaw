package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-014 (2026-06-07)：`MemoryQueryToolExecutor.executeQueryMemory` 必须解析新的可选
 * `tags` 参数并下传到 `memoryRepository.searchMemories(...)`，否则 tool description 加了
 * 参数但执行器忽略 —— 等于没加。
 *
 * **测试策略**：`MemoryQueryToolExecutor` 重度依赖 Android Context / MemoryRepository /
 * memorySearchSettingsPreferences / ObjectBox，JVM mock ROI 极低；与 R-AGENT-010/011/013
 * 同策略 —— 走源码字符串扫描守住 wiring。运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-014-f / g（见 docs/hermes-test-cases.md）。
 */
class MemoryQueryToolExecutorTagsWiringTest {

    private val source: String by lazy { stripComments(File(executorPath()).readText()) }
    private val executeBlock: String by lazy { extractFunctionBlock(source, "executeQueryMemory") }

    /**
     * TC-AGENT-014-f: `executeQueryMemory` 必须解析 `"tags"` 参数，并把解析结果下传给
     * `searchMemories(...)`（参数名也叫 `tags`）。
     */
    @Test
    fun `TC-AGENT-014-f executor parses tags param and forwards to searchMemories`() {
        // 解析入口：必须有 tool.parameters.find { it.name == "tags" } 风格的查找
        assertTrue(
            "executeQueryMemory 必须解析 \"tags\" 参数 —— 否则 tool description 加了 tags 参数" +
                "但 executor 忽略。\n实际函数体:\n$executeBlock",
            Regex(""""tags"""").containsMatchIn(executeBlock) &&
                Regex("""tool\.parameters\.find""").containsMatchIn(executeBlock)
        )

        // 下传：searchMemories(...) 调用必须含 `tags = ` 命名参数
        assertTrue(
            "executeQueryMemory 必须把解析后的 tags 下传给 searchMemories(...) —— " +
                "源码中需出现 `tags = ` 命名参数（kotlin 风格）。",
            Regex("""searchMemories\s*\([\s\S]*?tags\s*=""").containsMatchIn(executeBlock)
        )
    }

    /**
     * TC-AGENT-014-g: tags 参数解析必须支持按 `|` 切分得到多个 tag（与 query 参数关键词分隔
     * 风格一致），让 agent 可以传 `tags=#auto_summary|#chat:abc` 同时匹配多 tag。
     */
    @Test
    fun `TC-AGENT-014-g executor splits tags by pipe`() {
        // 必须有 split('|') 或 split("|") 或 split("\\|".toRegex()) 等等价调用
        val hasPipeSplit =
            Regex("""\.split\s*\(\s*'\|'""").containsMatchIn(executeBlock) ||
                Regex("""\.split\s*\(\s*"\|"""").containsMatchIn(executeBlock) ||
                Regex("""\.split\s*\([^)]*\\\\\|""").containsMatchIn(executeBlock)
        assertTrue(
            "executeQueryMemory 的 tags 参数解析必须按 `|` 切分支持多 tag —— " +
                "与 query 参数关键词分隔风格一致。\n实际函数体:\n$executeBlock",
            hasPipeSplit
        )
    }

    // ----- helpers -----

    private fun extractFunctionBlock(src: String, name: String): String {
        // 抓函数体：从签名行（含 `fun <name>(`）到下一个 top-level fun。
        // 用"下一个 fun"作为终点，避免大括号深度计数被 string templates 误导。
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
        // 与 MessageCoordinationDelegateAutoSummaryMemoryWiringTest.stripComments 同实现：
        // 状态机一遍剥块注释 + 行注释，保留 string literal 内的 /* */ // 字符。
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

    private fun executorPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryQueryToolExecutor.kt — cwd=${File(".").absolutePath}")
    }
}
