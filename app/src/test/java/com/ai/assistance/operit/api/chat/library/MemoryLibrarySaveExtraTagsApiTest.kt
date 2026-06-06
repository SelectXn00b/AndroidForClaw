package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-011 (2026-06-06): `MemoryLibrary.saveMemoryAsync` 必须暴露 `extraTags: List<String>`
 * 参数（默认 emptyList()），并在 `saveMemory` 的主问题 / 实体两条创建分支里 `forEach extraTags`
 * 调 `memoryRepository.addTagToMemory(memory, it)`。
 *
 * 这是为 gateway 路径（飞书 / 微信等）打 `#gateway:<platform>` tag 的 API 基础，APP UI 路径
 * 不传 extraTags，走默认 emptyList，行为不变。
 *
 * **测试策略**：`MemoryLibrary` 依赖 ObjectBox + Android Context，JVM mock ROI 太低，参考
 * `MemoryLibraryPersistentInstructionGuardTest` 模式走源码字符串扫描。运行时正确性由手测 +
 * §3 E2E 兜底。
 *
 * 对应 TC-AGENT-247-a/b/c（见 docs/hermes-test-cases.md）。
 */
class MemoryLibrarySaveExtraTagsApiTest {

    private val source: String by lazy { stripLineComments(File(memoryLibraryPath()).readText()) }

    @Test
    fun `TC-AGENT-247-a saveMemoryAsync signature contains extraTags parameter with default emptyList`() {
        // 匹配 `extraTags: List<String> = emptyList()`，允许空白多寡 / 类型参数前后空白。
        val sigRegex = Regex(
            """extraTags\s*:\s*List\s*<\s*String\s*>\s*=\s*emptyList\s*\(\s*\)"""
        )
        assertTrue(
            "MemoryLibrary.saveMemoryAsync 必须含 `extraTags: List<String> = emptyList()` 参数 —— " +
                "gateway 路径需要它打 #gateway:<platform> tag，APP UI 路径靠默认空 list 保留原行为。\n" +
                "未匹配 regex: $sigRegex",
            sigRegex.containsMatchIn(source)
        )
        // 同时确保该参数落在 saveMemoryAsync 函数声明窗口内（防止有人把它加到别的函数）
        val asyncIdx = source.indexOf("fun saveMemoryAsync")
        assertTrue("找不到 `fun saveMemoryAsync` 函数签名", asyncIdx >= 0)
        val asyncWindow = source.substring(asyncIdx, (asyncIdx + 1500).coerceAtMost(source.length))
        assertTrue(
            "`extraTags: List<String> = emptyList()` 必须出现在 saveMemoryAsync 签名块内（前 1500 字符窗口）。\n" +
                "实际窗口:\n$asyncWindow",
            sigRegex.containsMatchIn(asyncWindow)
        )
    }

    @Test
    fun `TC-AGENT-247-b saveMemory main problem branch injects extraTags`() {
        // 主问题创建分支：在 `mainProblem.tags.forEach { ... addTagToMemory ... }` 同一块内
        // 必须也有 `extraTags.forEach { ... addTagToMemory ... }`。
        val mainProblemBlock = extractWindowAround(source, "mainProblem.tags.forEach", before = 0, after = 30)
            ?: error("找不到 mainProblem.tags.forEach 块——MemoryLibrary 结构可能被改动")
        assertTrue(
            "MemoryLibrary.saveMemory 主问题创建分支必须在 mainProblem.tags 之后 forEach extraTags 调 addTagToMemory —— " +
                "否则 gateway 总结产生的主问题节点不会带 #gateway tag。\n实际窗口:\n$mainProblemBlock",
            mainProblemBlock.contains("extraTags.forEach") && mainProblemBlock.contains("addTagToMemory")
        )
    }

    @Test
    fun `TC-AGENT-247-c saveMemory entity branch injects extraTags`() {
        // 实体创建分支：在 `entity.tags.forEach { ... addTagToMemory ... }` 同一块内必须也有
        // `extraTags.forEach { ... addTagToMemory ... }`。
        val entityBlock = extractWindowAround(source, "entity.tags.forEach", before = 0, after = 30)
            ?: error("找不到 entity.tags.forEach 块——MemoryLibrary 结构可能被改动")
        assertTrue(
            "MemoryLibrary.saveMemory 实体创建分支必须在 entity.tags 之后 forEach extraTags 调 addTagToMemory —— " +
                "否则 gateway 总结产生的实体节点不会带 #gateway tag。\n实际窗口:\n$entityBlock",
            entityBlock.contains("extraTags.forEach") && entityBlock.contains("addTagToMemory")
        )
    }

    // ----- helpers -----

    /** 在 source 里找到 keyword 所在行，向前/向后各取 N 行返回（含 keyword 行）。 */
    private fun extractWindowAround(src: String, keyword: String, before: Int, after: Int): String? {
        val lines = src.lines()
        val idx = lines.indexOfFirst { it.contains(keyword) }
        if (idx < 0) return null
        val start = (idx - before).coerceAtLeast(0)
        val end = (idx + after + 1).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
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

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun memoryLibraryPath(): String =
        File(appSrcMainRoot(), "api/chat/library/MemoryLibrary.kt").path
}
