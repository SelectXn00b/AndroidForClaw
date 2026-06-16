package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-039 (2026-06-16)：`MemoryQueryToolExecutor.executeSessionSearch` 行为守护。
 *
 * **测试策略**：`MemoryQueryToolExecutor` 重度依赖 Android Context / ObjectBox MemoryRepository /
 * memorySearchSettingsPreferences，纯 JVM mock ROI 极低；与 R-AGENT-014 / R-AGENT-038 同策略走
 * 源码字符串扫描守住 `executeSessionSearch` 函数体的关键守卫（截断 / clamp / 空 query / 0 命中 /
 * try-catch）。运行时正确性由手测 + §3 E2E `test_tool_call_e2e.sh` 兜底。
 *
 * 对应 TC-AGENT-039-f / g（见 docs/hermes-test-cases.md）。
 */
class MemoryQueryToolExecutorSessionSearchTest {

    private val source: String by lazy { stripComments(File(executorPath()).readText()) }
    private val executeBlock: String by lazy { extractFunctionBlock(source, "executeSessionSearch") }

    /**
     * TC-AGENT-039-f: `executeSessionSearch` 必须含 8000 字符截断逻辑（含 `8000` 字面 + `…[truncated]`
     * 字面）+ `limit` clamp 逻辑（`coerceIn(...)` 或 `coerceAtMost(50)` 等价表达）。
     */
    @Test
    fun `TC-AGENT-039-f session_search truncates output and clamps limit`() {
        assertTrue(
            "找不到 executeSessionSearch 函数体 —— 先满足 TC-AGENT-039-b。",
            executeBlock.isNotBlank()
        )

        // 8000 字符截断
        assertTrue(
            "executeSessionSearch 必须含 `8000` 字面值 —— 输出截断阈值。\n实际函数体:\n$executeBlock",
            executeBlock.contains("8000")
        )
        assertTrue(
            "executeSessionSearch 必须含 `…[truncated]` 字面 —— 截断后必须有可见尾标。\n实际:\n$executeBlock",
            executeBlock.contains("…[truncated]") || executeBlock.contains("\u2026[truncated]")
        )

        // limit clamp（接受 coerceIn 或 coerceAtMost(50) / coerceAtLeast 等组合）
        val hasClamp =
            Regex("""\.coerceIn\s*\(""").containsMatchIn(executeBlock) ||
                (Regex("""\.coerceAtMost\s*\(\s*50""").containsMatchIn(executeBlock) &&
                    Regex("""\.coerceAtLeast\s*\(\s*1""").containsMatchIn(executeBlock))
        assertTrue(
            "executeSessionSearch 必须含 limit clamp 逻辑（`coerceIn(1, 50)` 或 `coerceAtMost(50) + coerceAtLeast(1)` 等价表达）—— " +
                "防止 agent 传超大 limit 拖死 ObjectBox 全表搜索。\n实际函数体:\n$executeBlock",
            hasClamp
        )
    }

    /**
     * TC-AGENT-039-g: `executeSessionSearch` 必须含三种边界守卫：
     *  (1) 空 query 走 success=false 路径（含 `isBlank` / `isEmpty` + `success = false` 字面）
     *  (2) 0 命中走 success=true 路径含 `"No matching memories found"` 字面
     *  (3) `try { ... } catch` 包住 `searchMemories` 调用 + `AppLogger.w` / `AppLogger.e` 记录
     *      + `success = false` 不穿透异常
     */
    @Test
    fun `TC-AGENT-039-g session_search guards empty query empty result and io exception`() {
        assertTrue(
            "找不到 executeSessionSearch 函数体 —— 先满足 TC-AGENT-039-b。",
            executeBlock.isNotBlank()
        )

        // (1) 空 query 守卫
        val hasEmptyQueryGuard =
            (Regex("""\bisBlank\s*\(""").containsMatchIn(executeBlock) ||
                Regex("""\bisEmpty\s*\(""").containsMatchIn(executeBlock)) &&
                Regex("""success\s*=\s*false""").containsMatchIn(executeBlock)
        assertTrue(
            "executeSessionSearch 必须含空 query 守卫（`query.isBlank()` / `query.isEmpty()` 走 `success = false`）—— " +
                "否则 agent 传空 query 会跑全表搜索 / 下游异常。\n实际函数体:\n$executeBlock",
            hasEmptyQueryGuard
        )

        // (2) 0 命中走 success 路径含 "No matching memories found"
        assertTrue(
            "executeSessionSearch 必须含 `\"No matching memories found\"` 字面 —— 0 命中走 success=true 路径。\n实际:\n$executeBlock",
            executeBlock.contains("No matching memories found")
        )

        // (3) try-catch 包 searchMemories
        val hasTryCatchAroundSearch = Regex(
            """try\s*\{[\s\S]*?searchMemories[\s\S]*?\}\s*catch\s*\(""",
            RegexOption.DOT_MATCHES_ALL
        ).containsMatchIn(executeBlock)
        assertTrue(
            "executeSessionSearch 必须用 `try { ... searchMemories(...) ... } catch` 包住 ObjectBox 搜索调用 —— " +
                "防止 IO / DB 异常穿透 agent loop。\n实际函数体:\n$executeBlock",
            hasTryCatchAroundSearch
        )
        // catch 体内必须 AppLogger.w / e 记录
        val hasLog =
            Regex("""AppLogger\.(w|e)\s*\(""").containsMatchIn(executeBlock)
        assertTrue(
            "executeSessionSearch 的 catch 路径必须 `AppLogger.w(...)` 或 `AppLogger.e(...)` 记录 —— " +
                "异常吞掉但要留诊断痕迹。\n实际函数体:\n$executeBlock",
            hasLog
        )
    }

    // ----- helpers (与 MemoryQueryToolExecutorTagsWiringTest 同款) -----

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

    private fun executorPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/MemoryQueryToolExecutor.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryQueryToolExecutor.kt — cwd=${File(".").absolutePath}")
    }
}
