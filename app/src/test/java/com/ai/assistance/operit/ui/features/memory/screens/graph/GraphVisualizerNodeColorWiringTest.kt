package com.ai.assistance.operit.ui.features.memory.screens.graph

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * R-AGENT-012 bugfix (2026-06-07): `GraphVisualizer.drawNode` 必须读 `node.color` 渲染节点 fill，
 * 不能硬编码 `nodePalette.fillColor`。先前实现把 `MemoryRepository.pickNodeColor()` 算好的
 * gateway 蓝绿 (`0xFF26A69A`) / persistent_instruction 金色 (`0xFFFFB300`) 全部丢弃，
 * 导致 R-AGENT-005/R-AGENT-012 颜色策略在 UI 层完全不可见。
 *
 * 修复策略：
 *   - `drawNode` 已经接收 `node: Node` 参数（line ~1133），不需要改签名
 *   - 在函数体里用 `node.color`：默认色 (`Color.LightGray`) 时 fallback 到 `nodePalette.fillColor`
 *     以保持暗色主题下默认节点的对比度
 *
 * 测试策略：Compose Canvas 渲染走 DrawScope 重依赖 Compose runtime，JVM mock ROI 太低，
 * 沿用 R-AGENT-010/011/012 的源码扫描模式守住 wiring 契约。运行时颜色由手测兜底
 * （飞书 gateway 跑一轮 → 进 MemoryScreen → 看节点颜色）。
 *
 * 对应 TC-AGENT-249-a。
 */
class GraphVisualizerNodeColorWiringTest {

    private val source: String by lazy { stripLineComments(File(graphVisualizerPath()).readText()) }

    @Test
    fun `TC-AGENT-249-a drawNode reads node color for fill`() {
        // drawNode 函数必须存在（保护性断言：防止重命名后测试无声沉默）
        assertTrue(
            "GraphVisualizer.kt 必须含 `private fun DrawScope.drawNode(` —— 节点渲染入口。",
            Regex("""fun\s+DrawScope\s*\.\s*drawNode\s*\(""").containsMatchIn(source)
        )

        // drawNode 必须接 node: Node 参数（之前已存在；这里守住不被无脑重构掉）
        assertTrue(
            "drawNode 必须接收 `node: Node` 参数 —— Repository 算好的 node.color 必须能透传进来。",
            Regex("""fun\s+DrawScope\s*\.\s*drawNode\s*\([^)]*\bnode\s*:\s*Node\b""")
                .containsMatchIn(source)
        )

        // 抓 drawNode 函数体（从签名行到下一个顶层 private fun / fun）
        val body = extractDrawNodeBody(source)

        // 函数体里必须出现 `node.color` 引用 —— 这是 bugfix 的核心：读 Repository 算好的色
        assertTrue(
            "drawNode 函数体必须引用 `node.color` —— 否则 Repository.pickNodeColor() " +
                "算出的 gateway 蓝绿 / persistent_instruction 金色被丢弃。\n" +
                "实际 drawNode 函数体（截断 800 字符）:\n${body.take(800)}",
            body.contains("node.color")
        )

        // 函数体里必须**不存在** `color = nodePalette.fillColor` 这种硬编码 fill 调用。
        // 允许 `nodePalette.fillColor` 仅作为 fallback 出现（被三元/if 包住）——但不能是
        // drawRoundRect 的直接 `color =` 参数硬编码。
        // 简化策略：扫描 `color\s*=\s*nodePalette\.fillColor` 直接命名参数赋值，不允许。
        val hardcodedFill = Regex("""color\s*=\s*nodePalette\s*\.\s*fillColor""")
            .containsMatchIn(body)
        assertFalse(
            "drawNode 函数体严禁出现 `color = nodePalette.fillColor` 直接硬编码 —— " +
                "必须先算 `val nodeFillColor = if (node.color != Color.LightGray) node.color " +
                "else nodePalette.fillColor`，再 `color = nodeFillColor` 传给 drawRoundRect。\n" +
                "实际 drawNode 函数体（截断 800 字符）:\n${body.take(800)}",
            hardcodedFill
        )
    }

    // ----- helpers -----

    private fun extractDrawNodeBody(src: String): String {
        // 从 `fun DrawScope.drawNode(` 那一行起，到下一个 `private fun ` / `fun ` 之前
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun DrawScope.drawNode(") || Regex("""fun\s+DrawScope\s*\.\s*drawNode\s*\(""").containsMatchIn(it)
        }
        check(startIdx >= 0) { "找不到 drawNode 函数签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val endRel = rest.indexOfFirst {
            val t = it.trimStart()
            t.startsWith("private fun ") || t.startsWith("fun ") || t.startsWith("@Composable")
        }
        val endIdx = if (endRel < 0) lines.size else startIdx + 1 + endRel
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

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun graphVisualizerPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/screens/graph/GraphVisualizer.kt").path
}
