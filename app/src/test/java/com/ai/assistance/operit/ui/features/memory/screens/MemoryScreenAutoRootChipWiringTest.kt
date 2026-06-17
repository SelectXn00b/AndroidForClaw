package com.ai.assistance.operit.ui.features.memory.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-041-b (2026-06-17): `MemoryScreen` 必须把 auto-root chip 过滤行接通：
 *  (1) 私有 `AutoRootFilterChipRow` composable 声明
 *  (2) MemoryScreen 主 layout 内对 `AutoRootFilterChipRow(` 的调用，与 `GatewayFilterChipRow(`
 *      平行渲染（两个 chip row **同时存在**，不互斥）
 *  (3) 5 条 string 资源引用：`memory_filter_auto_root_all` / `_hide` / `_summary` /
 *      `_extracted` / `_summary_id`
 *  (4) chip row 早返回判定（graph 没有任何 `#auto_root` 节点时整 row 不显示）
 *
 * 测试策略：源码字符串扫描守 wiring（仿 R-AGENT-041-c MemoryDialogsColdArchiveWiringTest 同款）。
 * Compose UI 涉及 FilterChip / collectAsState / Material3 composables，纯 JVM mock ROI 极低。
 *
 * 对应 TC-AGENT-041-b-g。
 */
class MemoryScreenAutoRootChipWiringTest {

    private val source: String by lazy { stripComments(File(screenPath()).readText()) }

    /**
     * TC-AGENT-041-b-g: MemoryScreen 必须含 AutoRootFilterChipRow 接线。
     */
    @Test
    fun `TC-AGENT-041-b-g screen renders auto_root chip row alongside gateway chip row`() {
        // (1) AutoRootFilterChipRow composable 声明（private fun 字面）
        assertTrue(
            "MemoryScreen.kt 必须含 `AutoRootFilterChipRow` composable 声明 —— " +
                "auto-root chip 行用独立 composable 封装，与 GatewayFilterChipRow 平行。",
            Regex("""\bfun\s+AutoRootFilterChipRow\s*\(""").containsMatchIn(source)
        )

        // (2) MemoryScreen 主 layout 调用点（与 GatewayFilterChipRow 平行调用）
        assertTrue(
            "MemoryScreen.kt 必须含 `AutoRootFilterChipRow(` 调用点 —— " +
                "在主 layout 里实际渲染。",
            Regex("""\bAutoRootFilterChipRow\s*\(""").containsMatchIn(source)
        )
        // 必须**两个** chip row 共存，"切到 auto-root chip row 把 gateway chip row 替换掉"是错误改动
        assertTrue(
            "MemoryScreen.kt 必须保留 `GatewayFilterChipRow(` 调用 —— " +
                "auto-root chip 行不替换 gateway chip 行，二者并行。",
            Regex("""\bGatewayFilterChipRow\s*\(""").containsMatchIn(source)
        )

        // (3) 5 条 string 资源引用
        val expectedStrings = listOf(
            "memory_filter_auto_root_all",
            "memory_filter_auto_root_hide",
            "memory_filter_auto_root_summary",
            "memory_filter_auto_root_extracted",
            "memory_filter_auto_root_summary_id"
        )
        for (resName in expectedStrings) {
            assertTrue(
                "MemoryScreen.kt 必须引用 string 资源 `R.string.$resName` —— " +
                    "auto-root chip 必须 resource-driven，不可硬编码。",
                source.contains(resName)
            )
        }

        // (4) 早返回 isEmpty 判定（graph 无任何 #auto_root 节点时整 row 不显示）
        // 早返回可能是 `if (xxx.isEmpty()) return` 或 `if (xxx.isEmpty()) return@Composable` 或
        // `if (...isNotEmpty()) { ... }` 守门。任一形式即可。
        val hasEmptyGuard =
            Regex("""\bisEmpty\s*\(\s*\)""").containsMatchIn(source) ||
                Regex("""\bisNotEmpty\s*\(\s*\)""").containsMatchIn(source)
        assertTrue(
            "MemoryScreen.kt 必须含 `isEmpty()` 或 `isNotEmpty()` 早返回守门 —— " +
                "graph 没有任何 `#auto_root` 节点时整 chip row 不显示，避免视觉噪声。",
            hasEmptyGuard
        )

        // (5) `#auto_root` 字面在 MemoryScreen.kt 出现（chip row 计算 availableAutoRootBuckets 时
        // 必须扫这个 family tag；ViewModel 处理过的话可改 ViewModel 测试，但 MemoryScreen 至少需
        // 引用 chip 选项的 bucket tag 字面 —— 检查任意一个 root tag 字面）
        val hasAnyRootTagLiteral =
            source.contains("#auto_summary_root") ||
                source.contains("#auto_extracted_root") ||
                source.contains("#auto_summary_id_root") ||
                source.contains("#auto_root")
        assertTrue(
            "MemoryScreen.kt 必须含至少一个 root tag 字面 " +
                "(`#auto_root` 或某个 bucket-specific root tag) —— " +
                "chip row 选项需要绑定到具体的 bucket tag。",
            hasAnyRootTagLiteral
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

    private fun screenPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/memory/screens/MemoryScreen.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/memory/screens/MemoryScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryScreen.kt — cwd=${File(".").absolutePath}")
    }
}
