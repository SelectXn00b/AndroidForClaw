package com.ai.assistance.operit.services.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-038 phase 1：写入端 wiring 守护。
 *
 * `MessageCoordinationDelegate` 的 `forcePersistSummaryToMemory` + `extractAndPersistFacts` 必须
 * 切到 `MemoryArchiver.appendToRoot(...)`，**不再**直接 `repository.saveMemory + addTagToMemory`
 * 写 `#auto_summary` / `#auto_extracted` 这种"每条一节点"的 fragment。
 *
 * **测试策略**：源码扫描（`MessageCoordinationDelegate` 重度依赖 ObjectBox + Service Context，
 * 纯 JVM 单测无法直接驱动）。运行时正确性由 §3 E2E + 手测兜底。
 *
 * 对应 TC-AGENT-038-f（见 docs/hermes-test-cases.md）。
 *
 * 替代了已撤回的 `MessageCoordinationDelegateAutoSummaryMemoryWiringTest` (TC-013-a..i)
 * 与 `MessageCoordinationDelegateFactExtractionWiringTest` (TC-016-a..i)。
 */
class MessageCoordinationDelegateAutoNodeWiringTest {

    private val source: String by lazy { stripComments(File(repoPath()).readText()) }

    private val forcePersistBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "forcePersistSummaryToMemory") }.getOrDefault("")
    }

    private val extractFactsBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "extractAndPersistFacts") }.getOrDefault("")
    }

    /**
     * TC-AGENT-038-f part 1: `forcePersistSummaryToMemory` 必须用 `MemoryArchiver.appendToRoot(`
     * 写 SUMMARY bucket，且**不**得再出现旧 wiring（`addTagToMemory(.., "#auto_summary"`）。
     */
    @Test
    fun `TC-AGENT-038-f1 forcePersistSummaryToMemory routes through MemoryArchiver SUMMARY bucket`() {
        assertTrue("找不到 forcePersistSummaryToMemory 函数体。", forcePersistBlock.isNotBlank())

        // 必须出现 archiver 调用
        assertTrue(
            "forcePersistSummaryToMemory 必须调 `memoryArchiver.appendToRoot(` —— " +
                "切到 R-AGENT-038 archiver 路径。\n实际:\n$forcePersistBlock",
            Regex("""\bmemoryArchiver\.appendToRoot\s*\(""").containsMatchIn(forcePersistBlock)
        )

        // bucket 必须是 SUMMARY
        assertTrue(
            "forcePersistSummaryToMemory 必须把 bucket 指为 `MemoryArchiver.ArchiveBucket.SUMMARY`。",
            Regex("""MemoryArchiver\.ArchiveBucket\.SUMMARY""").containsMatchIn(forcePersistBlock)
        )

        // **不**得再出现 #auto_summary 字面 tag 写入（旧 wiring）
        assertFalse(
            "forcePersistSummaryToMemory 不得再调 `addTagToMemory(.., \"#auto_summary\")` —— " +
                "已切到 archiver root 节点；旧路径残留会导致每条 summary 仍新建独立 Memory。\n" +
                "实际:\n$forcePersistBlock",
            Regex("""addTagToMemory\s*\([^)]*"#auto_summary"""")
                .containsMatchIn(forcePersistBlock)
        )

        // forcePersistSummaryToMemory 不得再直接 saveMemory 创建 fragment 节点
        // （saveMemory 仍然合法地通过 MemoryArchiver.ensureRoot 间接调用，不在此函数体内）
        assertFalse(
            "forcePersistSummaryToMemory 函数体内不得再直接调 `repository.saveMemory(` —— " +
                "fragment 节点创建路径已废弃。\n实际:\n$forcePersistBlock",
            Regex("""\brepository\.saveMemory\s*\(""").containsMatchIn(forcePersistBlock)
        )
    }

    /**
     * TC-AGENT-038-f part 2: `extractAndPersistFacts` 必须用 `appendToRoot(EXTRACTED, ...)` 写
     * EXTRACTED bucket，且**不**得再出现 `addTagToMemory(.., "#auto_extracted"` 旧 wiring。
     * 函数签名应包含 `archiver: MemoryArchiver` 参数（而不是旧的 parentMemoryId/repository）。
     */
    @Test
    fun `TC-AGENT-038-f2 extractAndPersistFacts routes through MemoryArchiver EXTRACTED bucket`() {
        assertTrue("找不到 extractAndPersistFacts 函数体。", extractFactsBlock.isNotBlank())

        // 必须有 archiver: MemoryArchiver 形参
        assertTrue(
            "extractAndPersistFacts 必须新增 `archiver: MemoryArchiver` 形参 —— " +
                "之前的 parentMemoryId / repository 形参已废弃。\n实际签名行:\n${extractFactsBlock.lines().take(5).joinToString("\n")}",
            Regex("""archiver\s*:\s*MemoryArchiver""").containsMatchIn(extractFactsBlock)
        )

        // 必须调 archiver.appendToRoot
        assertTrue(
            "extractAndPersistFacts 必须调 `archiver.appendToRoot(` —— 切到 R-AGENT-038 路径。",
            Regex("""\barchiver\.appendToRoot\s*\(""").containsMatchIn(extractFactsBlock)
        )

        // bucket 必须是 EXTRACTED
        assertTrue(
            "extractAndPersistFacts 必须把 bucket 指为 `MemoryArchiver.ArchiveBucket.EXTRACTED`。",
            Regex("""MemoryArchiver\.ArchiveBucket\.EXTRACTED""").containsMatchIn(extractFactsBlock)
        )

        // **不**得再出现 #auto_extracted 字面 tag 写入
        assertFalse(
            "extractAndPersistFacts 不得再调 `addTagToMemory(.., \"#auto_extracted\")` —— " +
                "已切 archiver root；旧路径残留会让每条 fact 仍新建独立 Memory。\n" +
                "实际:\n$extractFactsBlock",
            Regex("""addTagToMemory\s*\([^)]*"#auto_extracted"""")
                .containsMatchIn(extractFactsBlock)
        )

        // 不得再写 #auto_summary_id 这种历史编号 tag（这条路径在 R-AGENT-027 已删，phase 1 不复活）
        assertFalse(
            "extractAndPersistFacts 不得复活 `#auto_summary_id:` tag —— R-AGENT-027 已删此路径。",
            extractFactsBlock.contains("#auto_summary_id:")
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

    private fun repoPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageCoordinationDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
