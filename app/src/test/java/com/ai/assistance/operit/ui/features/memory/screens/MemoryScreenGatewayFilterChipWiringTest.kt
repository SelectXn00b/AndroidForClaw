package com.ai.assistance.operit.ui.features.memory.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-012 (2026-06-06): `MemoryScreen.kt` 必须在 `MemorySearchBar` 调用之后、
 * `GraphVisualizer` 调用之前插一行 `FilterChip` 渲染 gateway 过滤 chip 行，
 * 且对 `uiState.availableGatewayPlatforms.isEmpty()` 有条件隐藏（老用户/无 gateway 历史时不显示空 chip 行）。
 *
 * chip 点击 callback 必须调 `viewModel.onGatewayFilterChange(...)`，并引用
 * `uiState.availableGatewayPlatforms` + `uiState.gatewayFilter` 决定 chip 选中态。
 *
 * 测试策略：Composable 重度依赖 Compose runtime + Android Context，JVM mock ROI 太低，
 * 沿用 §0.3 源码扫描守住 wiring 契约。运行时 chip 行为由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-248-e/f。
 */
class MemoryScreenGatewayFilterChipWiringTest {

    private val source: String by lazy { stripLineComments(File(memoryScreenPath()).readText()) }

    @Test
    fun `TC-AGENT-248-e MemoryScreen wires gateway filter chip row between SearchBar and GraphVisualizer`() {
        // 必须存在 FilterChip 调用（chip 行渲染入口）
        assertTrue(
            "MemoryScreen.kt 必须 import 并调用 `FilterChip` —— gateway 过滤 chip 行的核心组件。",
            source.contains("FilterChip")
        )

        // 必须引用 uiState.availableGatewayPlatforms（chip 行数据源）
        assertTrue(
            "MemoryScreen.kt 必须引用 `uiState.availableGatewayPlatforms` —— " +
                "chip 行的 platform 来源（动态发现的飞书/微信等）。",
            source.contains("uiState.availableGatewayPlatforms") ||
                source.contains("availableGatewayPlatforms")
        )

        // 必须引用 uiState.gatewayFilter（chip 选中态来源）
        assertTrue(
            "MemoryScreen.kt 必须引用 `uiState.gatewayFilter` —— " +
                "chip 当前选中状态来源（决定哪个 chip 高亮）。",
            source.contains("uiState.gatewayFilter") ||
                source.contains("gatewayFilter")
        )

        // FilterChip 必须出现在 MemorySearchBar 之后、GraphVisualizer 之前。
        // 注意：FilterChip 可能被封装在私有 Composable（如 GatewayFilterChipRow）里，
        // 那种情况私有 Composable 的定义位置在 MemoryScreen 函数体之后（idx 大），
        // 不能用裸 `FilterChip(` indexOf。应该:
        //   1. 优先找 MemoryScreen 函数体里 chip 行的 Composable 调用入口（GatewayFilterChipRow 或裸 FilterChip）
        //   2. 该调用位置必须 > SearchBar 调用、< GraphVisualizer 调用
        val searchBarIdx = source.indexOf("MemorySearchBar(")
        val graphVizIdx = source.indexOf("GraphVisualizer(")
        assertTrue("找不到 MemorySearchBar( 调用", searchBarIdx >= 0)
        assertTrue("找不到 GraphVisualizer( 调用", graphVizIdx >= 0)
        // 在 SearchBar..GraphVisualizer 区间内必须出现 chip 行入口（GatewayFilterChipRow( 或 FilterChip(）
        val between = source.substring(searchBarIdx, graphVizIdx)
        assertTrue(
            "MemoryScreen 函数体里 MemorySearchBar 与 GraphVisualizer 之间必须存在 chip 行 Composable 调用 —— " +
                "可以是裸 `FilterChip(` 或封装的 `GatewayFilterChipRow(` 调用入口。\n" +
                "实际两者之间内容（截断 500 字符）:\n${between.take(500)}",
            between.contains("FilterChip(") || between.contains("GatewayFilterChipRow(")
        )
    }

    @Test
    fun `TC-AGENT-248-f MemoryScreen hides chip row when no gateway platforms`() {
        // chip 点击 callback 必须调 viewModel.onGatewayFilterChange(...)
        assertTrue(
            "MemoryScreen.kt 必须存在 `viewModel.onGatewayFilterChange(` 调用 —— " +
                "chip 点击回调入口，否则 chip 点了没用。",
            Regex("""viewModel\s*\.\s*onGatewayFilterChange\s*\(""").containsMatchIn(source)
        )

        // availableGatewayPlatforms.isEmpty() 条件隐藏 chip 行
        // 多种合法形式都允许：if (xxx.isNotEmpty()) / if (xxx.isEmpty()) ... else ... / xxx.takeIf
        val hasEmptyGuard = Regex(
            """availableGatewayPlatforms\s*\.\s*(isEmpty|isNotEmpty)\s*\(\s*\)"""
        ).containsMatchIn(source)
        assertTrue(
            "MemoryScreen.kt 必须对 `availableGatewayPlatforms.isEmpty()` 或 `.isNotEmpty()` 做条件渲染 —— " +
                "老用户/无 gateway 历史时不应看到空 chip 行（视觉残留）。",
            hasEmptyGuard
        )
    }

    // ----- helpers -----

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

    private fun memoryScreenPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/screens/MemoryScreen.kt").path
}
