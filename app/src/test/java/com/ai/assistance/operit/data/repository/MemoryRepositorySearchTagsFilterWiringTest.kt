package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-014 (2026-06-07)：`MemoryRepository.searchMemories` 必须新增 `tags: List<String>?`
 * 可选参数（默认 null = 向后兼容），并在 `runSearchMemoriesWithDebug` 函数体内做**前置硬过滤**
 * （不是 `tagWeight` 那种打分加权）。
 *
 * **背景**：现有 `searchMemories` 的 `tagWeight` 是"命中 query 中的 tag-like 关键词加分"，
 * 不是"必须含某 tag 才进入结果集"的硬过滤。R-AGENT-014 要的是后者 —— `tags=#auto_summary`
 * 必须只返回带这个 tag 的 memory，其他全部排除。
 *
 * **测试策略**：`MemoryRepository` 重度依赖 ObjectBox runtime，JVM 单测要走 Robolectric 或
 * mock 一堆基础设施 ROI 太低，沿用 R-AGENT-010/011/013 同策略 —— 源码字符串扫描守 wiring。
 * 运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-014-h（见 docs/hermes-test-cases.md）。
 */
class MemoryRepositorySearchTagsFilterWiringTest {

    private val source: String by lazy { stripComments(File(repositoryPath()).readText()) }

    /**
     * TC-AGENT-014-h: `searchMemories` 公开签名必须含 `tags: List<String>?` 参数（默认 `null`），
     * 且 `runSearchMemoriesWithDebug` 函数体含按 tags 做"all-of-tags" 硬过滤的代码
     * （`tags.all { ... mem.tags.any ...}` 或等价语义）。
     */
    @Test
    fun `TC-AGENT-014-h searchMemories adds tags filter parameter`() {
        // (1) 公开 searchMemories 签名必须含 `tags: List<String>?` 参数（默认 null）
        // 抓 searchMemories 公开 fun 的签名块（从 `suspend fun searchMemories(` 到第一个 `)`）
        val searchMemoriesSignature = extractFunctionSignature(source, "searchMemories")
            ?: error("找不到 suspend fun searchMemories(...) 签名")

        assertTrue(
            "searchMemories 公开签名必须含 `tags: List<String>?` 参数（默认 null）—— " +
                "向后兼容所有既有 18+ 处调用方。\n实际签名:\n$searchMemoriesSignature",
            Regex("""tags\s*:\s*List<String>\?""").containsMatchIn(searchMemoriesSignature) &&
                Regex("""tags\s*:\s*List<String>\?\s*=\s*null""").containsMatchIn(searchMemoriesSignature)
        )

        // (2) runSearchMemoriesWithDebug 函数体必须把 tags 参数下传 + 含按 tag 过滤的代码
        val runBlock = extractFunctionBlock(source, "runSearchMemoriesWithDebug")
        assertTrue(
            "runSearchMemoriesWithDebug 必须接收 tags 参数并做前置硬过滤 —— " +
                "源码中需出现 `tags.all { ... mem.tags.any ...}` 或 `tags.all { tag -> ...tag.name ...}` " +
                "或等价语义。tagWeight 那种打分加权不算（那是 query 关键词命中加分，不是 tag 硬过滤）。",
            // 检查 1：函数签名包含 tags: List<String>? 参数
            Regex("""tags\s*:\s*List<String>\?""").containsMatchIn(runBlock)
        )

        // 检查 2：函数体里必须有按 tag 硬过滤的代码 ——
        // 接受两种风格：
        //   a) `tags.all { ... mem.tags.any ... }` （内存 filter）
        //   b) `tagBox.query(MemoryTag_.name.equal(...))` （ObjectBox query 风格）
        val hasInMemoryFilter =
            Regex("""\btags\s*\.\s*all\s*\{""").containsMatchIn(runBlock) &&
                Regex("""\.tags\s*\.\s*any\s*\{""").containsMatchIn(runBlock)
        val hasObjectBoxFilter =
            Regex("""MemoryTag_\.name\.equal""").containsMatchIn(runBlock) ||
                Regex("""tagBox\.query""").containsMatchIn(runBlock)
        assertTrue(
            "runSearchMemoriesWithDebug 必须含按 tag 硬过滤代码（内存 filter 或 ObjectBox query）—— " +
                "实际函数体（前 2000 chars）:\n${runBlock.take(2000)}",
            hasInMemoryFilter || hasObjectBoxFilter
        )
    }

    // ----- helpers -----

    /**
     * 抽公开函数签名：从 `fun <name>(` 起到第一个匹配的 `):` 或 `): X {` 截止
     * （只看签名行，不要函数体）。
     */
    private fun extractFunctionSignature(src: String, name: String): String? {
        val startMatch = Regex("""(suspend\s+)?fun\s+$name\s*\(""").find(src) ?: return null
        val startIdx = startMatch.range.first
        // 找匹配的 `)`（按括号深度计数；这里参数列表里可能有 `<` `,` 但不会有未配对的 `(`）
        var depth = 0
        var i = startIdx
        var seenOpen = false
        while (i < src.length) {
            val c = src[i]
            when (c) {
                '(' -> { depth++; seenOpen = true }
                ')' -> {
                    depth--
                    if (seenOpen && depth == 0) {
                        // 再吃到换行或 `{` 结束
                        val afterParen = src.substring(i + 1, (i + 200).coerceAtMost(src.length))
                        val tailEnd = Regex("""[\{\n]""").find(afterParen)?.range?.first ?: 0
                        return src.substring(startIdx, i + 1 + tailEnd)
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * 抓函数体：从签名行（含 `fun <name>(`）到下一个 top-level fun。
     */
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

    private fun repositoryPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt"),
            File("app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryRepository.kt — cwd=${File(".").absolutePath}")
    }
}
