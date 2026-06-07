package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-004 (2026-06-07)：EditMemoryDialog content 高度上限抬高 + `#auto_summary` 节点 hint chip。
 *
 * R-AGENT-013 让 APP 内每次自动摘要强行写入长期记忆并打 `#auto_summary` tag。这些节点 content 通常较长
 * （500~2000 字），200dp 高度严重限制编辑体验。同时用户在编辑界面无法识别"这条是自动摘要"。
 *
 * **测试策略**：Composable 重度依赖 Compose runtime + Android resources，走源码扫描守住 wiring；
 * 视觉效果由手测验证（与 R-AGENT-012 chip 行同策略）。
 *
 * 对应 TC-UI-004-a..g（见 docs/hermes-test-cases.md）。
 */
class EditMemoryDialogAutoSummaryHintWiringTest {

    private val source: String by lazy { stripLineComments(File(dialogPath()).readText()) }
    private val stringsSource: String by lazy { File(stringsPath()).readText() }

    @Test
    fun `TC-UI-004-a content field min height raised to 160dp`() {
        assertTrue(
            "content OutlinedTextField 必须 Modifier.heightIn(...) 含 min = 160.dp —— " +
                "短摘要也舒展，与既有最小高度上限对齐用户期望。",
            Regex("""heightIn\s*\([^)]*min\s*=\s*160\.dp""").containsMatchIn(source)
        )
    }

    @Test
    fun `TC-UI-004-b content field max height raised to 480dp`() {
        assertTrue(
            "content OutlinedTextField 必须 Modifier.heightIn(...) 含 max = 480.dp —— " +
                "长摘要可舒展到 ~18-20 行，配合外层 fillMaxHeight(0.9f) + verticalScroll 不破坏 dialog 布局。",
            Regex("""heightIn\s*\([^)]*max\s*=\s*480\.dp""").containsMatchIn(source)
        )
    }

    @Test
    fun `TC-UI-004-c references auto_summary tag literal`() {
        assertTrue(
            "EditMemoryDialog 源码必须包含 \"#auto_summary\" 字面字符串 —— 用于 chip 渲染分支判断。",
            source.contains("\"#auto_summary\"")
        )
    }

    @Test
    fun `TC-UI-004-d uses AssistChip or SuggestionChip for hint`() {
        assertTrue(
            "auto_summary hint 必须用 AssistChip 或 SuggestionChip 渲染 —— 与 Material3 chip 视觉风格统一。",
            Regex("""\bAssistChip\s*\(""").containsMatchIn(source) ||
                Regex("""\bSuggestionChip\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun `TC-UI-004-e chip rendered conditionally on auto_summary tag`() {
        // chip 必须只在 memory 含 #auto_summary tag 时渲染 —— 普通 / gateway / persistent_instruction 节点不渲染。
        // 找到 chip 渲染点，向上扫描必须存在 if(...含 "#auto_summary"... 含 tags...) 条件。
        val chipMatch = Regex("""(AssistChip|SuggestionChip)\s*\(""").find(source)
        assertTrue("找不到 AssistChip/SuggestionChip 调用点", chipMatch != null)
        val chipIdx = chipMatch!!.range.first
        val beforeWindow = source.substring(0, chipIdx)
            .lines()
            .takeLast(30)
            .joinToString("\n")
        assertTrue(
            "chip 渲染必须被 if（或等价条件分支）包裹，且条件引用 tags + \"#auto_summary\"。\n实际前 30 行:\n$beforeWindow",
            beforeWindow.contains("\"#auto_summary\"") &&
                Regex("""\bif\s*\(""").containsMatchIn(beforeWindow) &&
                beforeWindow.contains("tags")
        )
    }

    @Test
    fun `TC-UI-004-f chip label uses string resource`() {
        assertTrue(
            "chip 文案必须走 stringResource(R.string.memory_auto_summary_chip) —— i18n 不得硬编码。",
            source.contains("R.string.memory_auto_summary_chip") &&
                Regex("""stringResource\s*\(\s*R\.string\.memory_auto_summary_chip""").containsMatchIn(source)
        )
        assertTrue(
            "strings.xml 必须含 memory_auto_summary_chip 键。",
            stringsSource.contains("memory_auto_summary_chip")
        )
    }

    @Test
    fun `TC-UI-004-g document node remains disabled`() {
        // R-UI-004 不解锁文档节点 content 编辑 —— 与既有约束一致。
        assertTrue(
            "content OutlinedTextField 必须保留 enabled = memory?.isDocumentNode != true 约束 —— " +
                "R-UI-004 只抬高高度 + 加 chip，不解锁文档节点编辑。",
            Regex("""enabled\s*=\s*memory\?\.\s*isDocumentNode\s*!=\s*true""").containsMatchIn(source)
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

    private fun dialogPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/EditMemoryDialog.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/EditMemoryDialog.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate EditMemoryDialog.kt — cwd=${File(".").absolutePath}")
    }

    private fun stringsPath(): String {
        // 中文是默认 locale；优先扫 values-zh-rCN，其次 values。
        val candidates = listOf(
            File("src/main/res/values-zh-rCN/strings.xml"),
            File("app/src/main/res/values-zh-rCN/strings.xml"),
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate strings.xml — cwd=${File(".").absolutePath}")
    }
}
