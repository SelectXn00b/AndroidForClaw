package com.ai.assistance.operit.ui.features.memory.viewmodel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-041-c (2026-06-17): `MemoryViewModel` 必须暴露冷归档 state，并在 `selectNode` 识别到 root 时
 * 后台拉冷归档：
 *  - StateFlow `coldArchiveEntries: StateFlow<List<MemoryArchiver.ArchiveEntry>>`
 *  - `selectNode(node)` 内：识别 root（`bucketForRootMemory(node) != null`）-> `Dispatchers.IO`
 *    协程调 `loadColdArchive(bucket)` 写到 `_coldArchiveEntries`；非 root 写 emptyList。
 *  - `clearSelection()` 同步清掉 `_coldArchiveEntries`（重置 emptyList）。
 *
 * 测试策略：源码字符串扫描守 wiring（仿 R-AGENT-038 / R-AGENT-039 / R-AGENT-040 / R-AGENT-041-a）。
 * VM 涉及 ObjectBox + viewModelScope + Dispatchers.IO + StateFlow，纯 JVM mock ROI 极低。
 *
 * 对应 TC-AGENT-041-c-g（见 docs/hermes-test-cases.md）。
 */
class MemoryViewModelColdArchiveWiringTest {

    private val source: String by lazy { stripComments(File(viewModelPath()).readText()) }

    private val selectNodeBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "selectNode") }.getOrDefault("")
    }

    private val clearSelectionBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "clearSelection") }.getOrDefault("")
    }

    /**
     * TC-AGENT-041-c-g: VM 暴露 `coldArchiveEntries` state + `selectNode` 命中 root 时拉归档 +
     * `clearSelection` 重置。
     */
    @Test
    fun `TC-AGENT-041-c-g viewmodel exposes cold archive state and loads on root selection`() {
        // (1) coldArchiveEntries StateFlow 字段
        assertTrue(
            "MemoryViewModel.kt 必须含 `coldArchiveEntries` 字段 —— 冷归档行 state。\n实际未命中。",
            source.contains("coldArchiveEntries")
        )
        assertTrue(
            "MemoryViewModel.kt 必须含 `StateFlow<` 字面 —— coldArchiveEntries 应是 StateFlow。",
            Regex("""\bStateFlow\s*<""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryViewModel.kt 必须含 `ArchiveEntry` 引用 —— state 元素类型来自 MemoryArchiver。",
            source.contains("ArchiveEntry")
        )

        // (2) selectNode 内调 bucketForRootMemory + loadColdArchive
        assertTrue(
            "找不到 selectNode 函数体 —— 先确认方法存在。",
            selectNodeBlock.isNotBlank()
        )
        assertTrue(
            "selectNode 必须调 `bucketForRootMemory(` —— 判断节点是否为 root + 反查 bucket。\n实际:\n$selectNodeBlock",
            Regex("""\bbucketForRootMemory\s*\(""").containsMatchIn(selectNodeBlock)
        )
        assertTrue(
            "selectNode 必须调 `loadColdArchive(` —— 命中 root 时拉对应 bucket 的冷归档。\n实际:\n$selectNodeBlock",
            Regex("""\bloadColdArchive\s*\(""").containsMatchIn(selectNodeBlock)
        )

        // (3) Dispatchers.IO 字面
        assertTrue(
            "selectNode（或其调用范围内）必须含 `Dispatchers.IO` 字面 —— " +
                "冷归档读文件必须走 IO 调度，不能阻塞主线程。\n实际:\n$selectNodeBlock",
            selectNodeBlock.contains("Dispatchers.IO")
        )

        // (4) clearSelection 重置 _coldArchiveEntries 为 emptyList
        assertTrue(
            "找不到 clearSelection 函数体 —— 先确认方法存在。",
            clearSelectionBlock.isNotBlank()
        )
        val hasResetInClear =
            Regex("""_coldArchiveEntries[\s\S]{0,80}emptyList""")
                .containsMatchIn(clearSelectionBlock) ||
                Regex("""coldArchiveEntries[\s\S]{0,80}emptyList""")
                    .containsMatchIn(clearSelectionBlock)
        assertTrue(
            "clearSelection 必须把 `_coldArchiveEntries` 重置为 `emptyList()` —— " +
                "防止上一个 root 节点的冷归档残留到下一个非 root 节点的详情页。\n实际:\n$clearSelectionBlock",
            hasResetInClear
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

    private fun viewModelPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/memory/viewmodel/MemoryViewModel.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/memory/viewmodel/MemoryViewModel.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryViewModel.kt — cwd=${File(".").absolutePath}")
    }
}
