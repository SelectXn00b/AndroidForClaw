package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-041-c (2026-06-17): `MemoryInfoDialog` 必须给 root 节点（携带 `#auto_root` tag）
 * 渲染冷归档区：用 `LazyColumn` 渲染 `coldArchiveEntries`，按 chatId 分组、组内按 ts 倒序。
 *
 * 测试策略：源码字符串扫描守 wiring（仿 R-AGENT-038 / R-AGENT-039 / R-AGENT-040 / R-AGENT-041-a）。
 * Compose UI 涉及 LazyColumn / state collection / Material3 composables，纯 JVM mock ROI 极低。
 *
 * 对应 TC-AGENT-041-c-f（见 docs/hermes-test-cases.md）。
 */
class MemoryDialogsColdArchiveWiringTest {

    private val source: String by lazy { stripComments(File(dialogsPath()).readText()) }

    /**
     * TC-AGENT-041-c-f: `MemoryInfoDialog` Composable 必须含：
     *  - `coldArchiveEntries` 参数（默认 `emptyList`）
     *  - `"#auto_root"` 字面（root 节点判定）
     *  - `LazyColumn(` 字面（列表性能）
     *  - `groupBy` 等价表达 + `chatId` 引用（按 chatId 分组）
     *  - `sortedByDescending` 等价表达 + `ts` 引用（组内 ts 倒序）
     */
    @Test
    fun `TC-AGENT-041-c-f info dialog renders cold archive section grouped by chatId on root nodes`() {
        // (1) coldArchiveEntries 参数（含默认值 emptyList）
        assertTrue(
            "MemoryDialogs.kt 必须含 `coldArchiveEntries` 参数 —— root 节点冷归档行列表。\n实际未命中。",
            Regex("""\bcoldArchiveEntries\s*:""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryDialogs.kt `coldArchiveEntries` 参数应有默认值 `emptyList()` —— " +
                "保持 MemoryInfoDialog 调用方向后兼容。",
            Regex("""coldArchiveEntries\s*:\s*[^=]*=\s*emptyList\s*\(""")
                .containsMatchIn(source)
        )

        // (2) #auto_root 字面（root 节点判定）
        assertTrue(
            "MemoryDialogs.kt 必须含 `\"#auto_root\"` 字面 —— root 节点判定（对齐 MemoryArchiver.ensureRoot 加的标识 tag）。",
            source.contains("\"#auto_root\"")
        )

        // (3) LazyColumn —— 列表性能
        assertTrue(
            "MemoryDialogs.kt 必须含 `LazyColumn(` —— root 节点冷归档可能有上百行，必须用 LazyColumn。",
            Regex("""\bLazyColumn\s*\(""").containsMatchIn(source)
        )

        // (4) groupBy 等价 + chatId 引用
        val hasGroupByChatId =
            Regex("""\bgroupBy\s*\{[^}]*\bchatId\b""").containsMatchIn(source) ||
                Regex("""\bgroupBy\s*\(\s*[^)]*::?chatId""").containsMatchIn(source)
        assertTrue(
            "MemoryDialogs.kt 必须含 `groupBy { ...chatId... }` —— 同一会话的冷归档行归到一组。\n实际未命中。",
            hasGroupByChatId
        )

        // (5) sortedByDescending 等价 + ts 引用
        val hasSortByTs =
            Regex("""\bsortedByDescending\s*\{[^}]*\bts\b""").containsMatchIn(source) ||
                Regex("""\bsortedByDescending\s*\(\s*[^)]*::?ts""").containsMatchIn(source)
        assertTrue(
            "MemoryDialogs.kt 必须含 `sortedByDescending { ...ts... }` —— 组内按 ts 倒序（最近的冷归档先看到）。\n实际未命中。",
            hasSortByTs
        )
    }

    // ----- helpers -----

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

    private fun dialogsPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/MemoryDialogs.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/dialogs/MemoryDialogs.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryDialogs.kt — cwd=${File(".").absolutePath}")
    }
}
