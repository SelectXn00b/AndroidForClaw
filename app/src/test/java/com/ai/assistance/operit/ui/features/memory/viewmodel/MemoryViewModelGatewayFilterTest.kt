package com.ai.assistance.operit.ui.features.memory.viewmodel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-012 (2026-06-06): `MemoryViewModel` / `MemoryUiState` 必须暴露 gateway 过滤维度：
 *  - `MemoryUiState` 含 `availableGatewayPlatforms: List<String>` + `gatewayFilter: GatewayFilter`
 *  - `sealed class/interface GatewayFilter` 三个变体：`All` / `OnlyGateway(platforms: Set<String>)` / `ExcludeGateway`
 *  - `MemoryViewModel` 暴露 `onGatewayFilterChange(filter)` 入口
 *  - `refreshGraph` / 搜索路径源码中应出现 `"#gateway:"` 字面字符串（说明 filter 被应用）
 *
 * 测试策略：`MemoryViewModel` 重依赖 Android Context / ObjectBox / Repository，JVM mock ROI 太低，
 * 沿用 R-AGENT-010 / R-AGENT-011 的源码扫描模式守住 wiring 契约。
 * 运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-248-b/c/d。
 */
class MemoryViewModelGatewayFilterTest {

    private val source: String by lazy { stripLineComments(File(viewModelPath()).readText()) }

    @Test
    fun `TC-AGENT-248-b MemoryUiState contains gateway filter fields`() {
        val availableField = Regex(
            """availableGatewayPlatforms\s*:\s*List\s*<\s*String\s*>\s*=\s*emptyList\s*\(\s*\)"""
        )
        assertTrue(
            "MemoryUiState 必须含 `availableGatewayPlatforms: List<String> = emptyList()` 字段 —— " +
                "用于驱动 chip 行渲染 gateway 平台 chip。",
            availableField.containsMatchIn(source)
        )

        // gatewayFilter 字段类型为 GatewayFilter，默认 GatewayFilter.All
        val filterField = Regex(
            """gatewayFilter\s*:\s*GatewayFilter\s*=\s*GatewayFilter\s*\.\s*All"""
        )
        assertTrue(
            "MemoryUiState 必须含 `gatewayFilter: GatewayFilter = GatewayFilter.All` 字段 —— " +
                "三态过滤的当前选中值，默认 All 保持老用户行为不变。",
            filterField.containsMatchIn(source)
        )
    }

    @Test
    fun `TC-AGENT-248-c GatewayFilter sealed class has three variants`() {
        // sealed class / sealed interface GatewayFilter
        val sealedDecl = Regex("""sealed\s+(class|interface)\s+GatewayFilter""")
        assertTrue(
            "必须定义 `sealed class GatewayFilter` 或 `sealed interface GatewayFilter` —— 三态过滤的类型基础。",
            sealedDecl.containsMatchIn(source)
        )

        // 三个变体：All / OnlyGateway / ExcludeGateway
        // All 是 object 单例
        assertTrue(
            "GatewayFilter 必须含 `All` 变体（object 单例，默认无过滤）。",
            Regex("""object\s+All\s*:\s*GatewayFilter""").containsMatchIn(source)
        )
        // ExcludeGateway 也是 object 单例
        assertTrue(
            "GatewayFilter 必须含 `ExcludeGateway` 变体（object 单例，屏蔽所有 gateway 节点）。",
            Regex("""object\s+ExcludeGateway\s*:\s*GatewayFilter""").containsMatchIn(source)
        )
        // OnlyGateway(platforms: Set<String>) 是 data class
        assertTrue(
            "GatewayFilter 必须含 `OnlyGateway(platforms: Set<String>)` 变体（data class，多选 platform 集合）。",
            Regex("""data\s+class\s+OnlyGateway\s*\(\s*val\s+platforms\s*:\s*Set\s*<\s*String\s*>""")
                .containsMatchIn(source)
        )
    }

    @Test
    fun `TC-AGENT-248-d refreshGraph applies gatewayFilter`() {
        // onGatewayFilterChange public 方法存在
        assertTrue(
            "MemoryViewModel 必须暴露 `fun onGatewayFilterChange(filter: GatewayFilter)` —— " +
                "UI chip 点击的入口。",
            Regex("""fun\s+onGatewayFilterChange\s*\(\s*filter\s*:\s*GatewayFilter\s*\)""")
                .containsMatchIn(source)
        )

        // refreshGraph / 搜索路径源码中必须出现 "#gateway:" 字面字符串
        // —— 说明 filter 真的在按 tag.startsWith("#gateway:") 过滤，不是只存了 state 没用上
        assertTrue(
            "MemoryViewModel 源码必须出现 `\"#gateway:\"` 字面字符串 —— " +
                "说明 gatewayFilter 真的被应用到 memory.tags 过滤逻辑，不是空 state。",
            source.contains("\"#gateway:\"")
        )
    }

    // ----- helpers (与 R-AGENT-011 测试一致) -----

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

    private fun viewModelPath(): String =
        File(appSrcMainRoot(), "ui/features/memory/viewmodel/MemoryViewModel.kt").path
}
