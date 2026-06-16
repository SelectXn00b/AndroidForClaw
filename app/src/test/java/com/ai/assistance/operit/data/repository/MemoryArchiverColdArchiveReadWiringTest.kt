package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-041-c (2026-06-17): `MemoryArchiver` 必须暴露冷归档读 API：
 *  - 顶层 `fun parseArchiveJsonl(text: String): List<...>` 纯函数（行为见
 *    `MemoryArchiverColdArchiveParseTest`）
 *  - data class `MemoryArchiver.ArchiveEntry(ts: Long, chatId: String, content: String, source: String)`
 *  - instance method `MemoryArchiver.loadColdArchive(bucket: ArchiveBucket): List<ArchiveEntry>`
 *  - util `MemoryArchiver.bucketForRootMemory(memory: Memory): ArchiveBucket?`
 *
 * 测试策略：源码字符串扫描守 wiring（仿 R-AGENT-038 / R-AGENT-039 / R-AGENT-040 / R-AGENT-041-a）。
 * IO 层涉及 Android `Context.filesDir` + ObjectBox `Memory` 关联 tag 反查，纯 JVM mock ROI 极低。
 *
 * 对应 TC-AGENT-041-c-d/e（见 docs/hermes-test-cases.md）。
 */
class MemoryArchiverColdArchiveReadWiringTest {

    private val source: String by lazy { stripComments(File(repoPath()).readText()) }

    private val loadBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "loadColdArchive") }.getOrDefault("")
    }

    /**
     * TC-AGENT-041-c-d: 必须含 4 项 API surface：
     *  - `data class ArchiveEntry(` 含 ts / chatId / content / source 四字段
     *  - `fun parseArchiveJsonl(` 顶层签名（接受 String，返回 List<）
     *  - `fun loadColdArchive(` instance method 签名（参数 ArchiveBucket，返回 List<）
     *  - `fun bucketForRootMemory(` 签名（参数含 Memory，返回类型 ArchiveBucket?）
     */
    @Test
    fun `TC-AGENT-041-c-d archiver declares cold archive read api surface`() {
        // (1) data class ArchiveEntry 含四字段
        assertTrue(
            "MemoryArchiver.kt 必须含 `data class ArchiveEntry(` 字面 —— 冷归档行实体。",
            Regex("""\bdata\s+class\s+ArchiveEntry\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "ArchiveEntry 必须含 `ts: Long` 字段。",
            Regex("""\bts\s*:\s*Long\b""").containsMatchIn(source)
        )
        assertTrue(
            "ArchiveEntry 必须含 `chatId: String` 字段。",
            Regex("""\bchatId\s*:\s*String\b""").containsMatchIn(source)
        )
        assertTrue(
            "ArchiveEntry 必须含 `content: String` 字段。",
            Regex("""\bcontent\s*:\s*String\b""").containsMatchIn(source)
        )
        assertTrue(
            "ArchiveEntry 必须含 `source: String` 字段。",
            Regex("""\bsource\s*:\s*String\b""").containsMatchIn(source)
        )

        // (2) 顶层 parseArchiveJsonl 签名
        assertTrue(
            "必须含顶层 `fun parseArchiveJsonl(` 签名 —— 接受 String，返回 List<。",
            Regex("""\bfun\s+parseArchiveJsonl\s*\(\s*[^)]*String[^)]*\)\s*:\s*List<""")
                .containsMatchIn(source)
        )

        // (3) loadColdArchive instance method 签名
        assertTrue(
            "必须含 `fun loadColdArchive(` instance method —— 接受 ArchiveBucket，返回 List<。",
            Regex("""\bfun\s+loadColdArchive\s*\(\s*[^)]*ArchiveBucket[^)]*\)\s*:\s*List<""")
                .containsMatchIn(source)
        )

        // (4) bucketForRootMemory 签名（参数含 Memory，返回 ArchiveBucket?）
        assertTrue(
            "必须含 `fun bucketForRootMemory(` —— 接受 Memory，返回 ArchiveBucket?。",
            Regex("""\bfun\s+bucketForRootMemory\s*\(\s*[^)]*Memory[^)]*\)\s*:\s*ArchiveBucket\?""")
                .containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-041-c-e: `loadColdArchive` 函数体必须含：
     *  - `archiveDir(` 调用拿到 bucket 对应目录
     *  - `.jsonl` 字面（用文件名后缀过滤）
     *  - `sortedByDescending` 或 `sortedDescending` 等价（最近日期在前）
     *  - `readText(` 字面（读文件内容）
     *  - `parseArchiveJsonl(` 调用
     *  - `try {` + `catch (` 包裹（IO 失败不能拖垮 UI）
     */
    @Test
    fun `TC-AGENT-041-c-e loadColdArchive lists jsonl files sorts desc reads parses with try-catch`() {
        assertTrue(
            "找不到 loadColdArchive 函数体 —— 先满足 TC-AGENT-041-c-d。",
            loadBlock.isNotBlank()
        )

        assertTrue(
            "loadColdArchive 必须调 `archiveDir(` 拿到 bucket 对应目录。\n实际:\n$loadBlock",
            Regex("""\barchiveDir\s*\(""").containsMatchIn(loadBlock)
        )
        assertTrue(
            "loadColdArchive 必须含 `.jsonl` 字面 —— 文件名后缀过滤。\n实际:\n$loadBlock",
            loadBlock.contains(".jsonl")
        )

        val hasSortDesc =
            Regex("""\bsortedByDescending\s*\{""").containsMatchIn(loadBlock) ||
                Regex("""\bsortedDescending\s*\(\s*\)""").containsMatchIn(loadBlock)
        assertTrue(
            "loadColdArchive 必须含 `sortedByDescending` 或 `sortedDescending` —— " +
                "最近日期在前（用户先看到最新的冷归档）。\n实际:\n$loadBlock",
            hasSortDesc
        )

        assertTrue(
            "loadColdArchive 必须调 `readText(` —— 读 jsonl 文件内容。\n实际:\n$loadBlock",
            Regex("""\breadText\s*\(""").containsMatchIn(loadBlock)
        )
        assertTrue(
            "loadColdArchive 必须调 `parseArchiveJsonl(` —— 复用顶层纯函数解析。\n实际:\n$loadBlock",
            Regex("""\bparseArchiveJsonl\s*\(""").containsMatchIn(loadBlock)
        )

        assertTrue(
            "loadColdArchive 必须含 `try {` —— 单文件 IO 失败不能拖垮整个 UI。\n实际:\n$loadBlock",
            loadBlock.contains("try {")
        )
        assertTrue(
            "loadColdArchive 必须含 `catch (` —— IO 异常吞掉返回 emptyList，让 UI 平滑降级。\n实际:\n$loadBlock",
            Regex("""\bcatch\s*\(""").containsMatchIn(loadBlock)
        )
    }

    // ----- helpers (与 MemoryArchiverTest 同款) -----

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
            File("src/main/java/com/ai/assistance/operit/data/repository/MemoryArchiver.kt"),
            File("app/src/main/java/com/ai/assistance/operit/data/repository/MemoryArchiver.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryArchiver.kt — cwd=${File(".").absolutePath}")
    }
}
