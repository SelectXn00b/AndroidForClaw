package com.ai.assistance.operit.ui.features.memory.screens.graph

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-012 bugfix follow-up 2026-06-07 (TC-AGENT-250-a):
 * TC-AGENT-249 让 `drawNode` 读 `node.color` 渲染节点 fill 之后，引入了一个视觉
 * regression：节点 fill 现在用固定 hex（金 0xFFFFB300 / 蓝绿 0xFF26A69A /
 * 绿 0xFF81C784 / 蓝 0xFF64B5F6 / 紫 0xFF9575CD），但 `getNodeLayoutMetrics`
 * 仍然写死 `color = nodePalette.textColor`（按主题算的浅/深灰）。暗色主题下
 * 浅文字 (#E5E7EB) 落到金/绿/蓝 fill 上 WCAG 对比度只有 1.3~1.8:1，文字
 * 几乎不可读。
 *
 * 修复约束：
 *   1. `getNodeLayoutMetrics` 必须接收 `nodeFillColor: Color` 参数
 *   2. 函数体里必须出现 `nodeFillColor.luminance()` 调用（按 fill 亮度选文字色）
 *   3. cache key 必须含 fillColor.hashCode() —— 否则同一节点不同 fill
 *      复用文字 layout result，反色失效
 *   4. `drawNode` 必须把算好的 `nodeFillColor` 透传进 `getNodeLayoutMetrics`
 *   5. `resolveNodeScreenRect` 调 `getNodeLayoutMetrics` 时也得传 fillColor
 *      参数（命中检测只用布局尺寸，传 fallback 色即可）—— 这条由编译期强制，
 *      无需单测显式守
 *
 * 测试策略：Compose Canvas 渲染走 DrawScope，JVM mock ROI 太低，沿用 R-AGENT-249
 * 模式做源码扫描。运行时对比度由手测（暗色主题装 APK 看图谱节点）兜底。
 */
class GraphVisualizerTextContrastWiringTest {

    private val source: String by lazy { stripLineComments(File(graphVisualizerPath()).readText()) }

    @Test
    fun `TC-AGENT-250-a getNodeLayoutMetrics picks text color by fill luminance`() {
        // 1) getNodeLayoutMetrics 签名必须含 nodeFillColor: Color 参数
        assertTrue(
            "getNodeLayoutMetrics 必须接收 `nodeFillColor: Color` 参数 —— " +
                "TC-AGENT-249 把 fill 从主题灰改成固定 hex 后，文字色必须按 fill " +
                "亮度反推，不能再无脑用主题 textColor。",
            Regex("""fun\s+getNodeLayoutMetrics\s*\([\s\S]*?nodeFillColor\s*:\s*Color""")
                .containsMatchIn(source)
        )

        // 抓 getNodeLayoutMetrics 函数体
        val body = extractGetNodeLayoutMetricsBody(source)

        // 2) 函数体必须出现 nodeFillColor.luminance() 调用 —— 按 fill 亮度选文字色
        assertTrue(
            "getNodeLayoutMetrics 函数体必须调 `nodeFillColor.luminance()` —— " +
                "决定文字色用浅色 (#E5E7EB) 还是深色 (#1F2937) 才能保证 WCAG AA " +
                "对比度。\n实际函数体（截断 1000 字符）:\n${body.take(1000)}",
            Regex("""nodeFillColor\s*\.\s*luminance\s*\(\s*\)""").containsMatchIn(body)
        )

        // 3) cache key 必须含 fillColor.hashCode() / nodeFillColor.hashCode() —— 否则
        //    同节点不同 fill 复用 layout result，缓存污染让反色失效
        val cacheKeyHasFill = Regex(
            """nodeFillColor\s*\.\s*hashCode\s*\(\s*\)|fillColor\s*\.\s*hashCode\s*\(\s*\)|nodeFillColor\s*\.\s*value"""
        ).containsMatchIn(body)
        assertTrue(
            "getNodeLayoutMetrics 的 cache key 必须含 nodeFillColor 的 hashCode/value —— " +
                "TC-AGENT-249 后同一节点 id 可能因 tag 变化拿到不同 fill 色，若 cache key " +
                "不区分 fill，第一次缓存的 textColor 会被复用，反色失效。\n" +
                "实际函数体（截断 1000 字符）:\n${body.take(1000)}",
            cacheKeyHasFill
        )

        // 4) drawNode 必须把 nodeFillColor 透传进 getNodeLayoutMetrics
        val drawNodeBody = extractDrawNodeBody(source)
        assertTrue(
            "drawNode 必须把算好的 `nodeFillColor` 透传进 getNodeLayoutMetrics —— " +
                "否则 getNodeLayoutMetrics 拿不到 fill 信息，反色逻辑没数据。\n" +
                "实际 drawNode 函数体（截断 1200 字符）:\n${drawNodeBody.take(1200)}",
            Regex("""getNodeLayoutMetrics\s*\([\s\S]*?nodeFillColor\s*=""")
                .containsMatchIn(drawNodeBody) ||
                Regex("""getNodeLayoutMetrics\s*\([\s\S]*?nodeFillColor\b[^=]""")
                    .containsMatchIn(drawNodeBody)
        )
    }

    // ----- helpers -----

    private fun extractGetNodeLayoutMetricsBody(src: String): String =
        extractFunBody(src, "getNodeLayoutMetrics")

    private fun extractDrawNodeBody(src: String): String =
        extractFunBody(src, "drawNode")

    private fun extractFunBody(src: String, funName: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun $funName(") ||
                Regex("""fun\s+(DrawScope\s*\.\s*)?$funName\s*\(""").containsMatchIn(it)
        }
        check(startIdx >= 0) { "找不到 $funName 函数签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val endRel = rest.indexOfFirst {
            val t = it.trimStart()
            (t.startsWith("private fun ") || t.startsWith("fun ") || t.startsWith("@Composable")) &&
                !t.contains("$funName(")
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
