package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-011 (2026-06-06)：`HermesGatewayController.runHermesAgent` 在调
 * `MemoryLibrary.saveMemoryAsync` 时，**必须**显式传 `extraTags = listOf("#gateway:$platform")`，
 * 其中 `platform` 从 `sessionKey` 派生（`sessionKey.substringBefore(':')` 或等价），不能硬编码
 * `"unknown"` / `""` / `"gateway"`。
 *
 * 这样飞书 / 微信等不同平台的 gateway 总结产生的记忆节点都自带 `#gateway:<platform>` tag，
 * 用户在 MemoryScreen 里可以用 `startsWith("#gateway:")` 过滤掉机器人路径的记忆，或
 * `== "#gateway:feishu"` 只看飞书路径。
 *
 * **测试策略**：`runHermesAgent` 是 suspend + 重度依赖 Android Context / multiServiceManager /
 * ChatHistoryManager，JVM mock ROI 太低，沿用 R-AGENT-010 的 TC-AGENT-246-* 模式走源码字符串扫描。
 * 运行时正确性由手测 + §3 E2E 兜底。
 *
 * 对应 TC-AGENT-247-d/e（见 docs/hermes-test-cases.md）。
 */
class HermesGatewayControllerGatewayTagWiringTest {

    private val source: String by lazy { stripLineComments(File(controllerPath()).readText()) }
    private val runBlock: String by lazy { extractRunHermesAgentBlock(source) }

    @Test
    fun `TC-AGENT-247-d runHermesAgent passes gateway tag to saveMemoryAsync`() {
        // runHermesAgent 必须调一次 saveMemoryAsync 且传 extraTags 参数，参数值含 "#gateway:" 前缀。
        // 命名参数 `extraTags = ...` 或单纯 `extraTags = listOf("#gateway:...` 都算。
        val saveIdx = runBlock.indexOf("saveMemoryAsync")
        assertTrue("找不到 saveMemoryAsync 调用点 —— R-AGENT-010 接线断了？", saveIdx >= 0)
        // saveMemoryAsync 之后 600 字符内必须出现 extraTags = listOf 且含 #gateway: 前缀
        val callWindow = runBlock.substring(saveIdx, (saveIdx + 600).coerceAtMost(runBlock.length))
        assertTrue(
            "runHermesAgent 调 saveMemoryAsync 时必须传 `extraTags = listOf(...)` —— 否则 gateway 路径的记忆无法和 APP UI 路径区分。\n" +
                "实际 saveMemoryAsync 之后 600 字符窗口:\n$callWindow",
            Regex("""extraTags\s*=\s*listOf\s*\(""").containsMatchIn(callWindow)
        )
        assertTrue(
            "extraTags 中必须含 \"#gateway:\" 前缀（gateway 路径专用 tag 命名）。\n实际窗口:\n$callWindow",
            callWindow.contains("\"#gateway:")
        )
    }

    @Test
    fun `TC-AGENT-247-e gateway tag platform derives from sessionKey`() {
        // 不能硬编码平台名。runBlock 内必须出现从 sessionKey 派生 platform 的痕迹：
        //   `sessionKey.substringBefore(':')` 或 `sessionKey.split(':')`，
        // 且 saveMemoryAsync 调用窗口里 extraTags 的字符串里含 `$platform`（Kotlin 字符串模板）
        // 或直接 `${...}` 嵌入 substringBefore/split 派生值。
        val derives = Regex("""sessionKey\s*\.\s*(substringBefore|split)\s*\(""")
            .containsMatchIn(runBlock)
        assertTrue(
            "runHermesAgent 必须从 sessionKey 派生 platform（substringBefore/split），不得硬编码 —— " +
                "否则飞书、微信等多平台 gateway 全部打成同一个 tag，区分不了。",
            derives
        )

        // 找 saveMemoryAsync 调用点，确认 extraTags 用了字符串模板（含 `$` 引用）或直接拼派生值，
        // 不是 listOf("#gateway:unknown") 这种死值。
        val saveIdx = runBlock.indexOf("saveMemoryAsync")
        assertTrue("找不到 saveMemoryAsync 调用点", saveIdx >= 0)
        val callWindow = runBlock.substring(saveIdx, (saveIdx + 600).coerceAtMost(runBlock.length))

        // 在 extraTags = listOf(...) 的字符串字面里必须含 `$`（变量插值）或 `${`（表达式插值）
        // ——意味着 platform 来自变量而非死值。
        val extraTagsMatch = Regex("""extraTags\s*=\s*listOf\s*\(\s*"([^"]*)"""")
            .find(callWindow)
        assertTrue(
            "extraTags 中的 tag 字符串无法匹配。实际窗口:\n$callWindow",
            extraTagsMatch != null
        )
        val tagLiteral = extraTagsMatch!!.groupValues[1]
        assertTrue(
            "extraTags 的 tag 字符串必须用 Kotlin 字符串模板嵌入 platform（含 `\$` 或 `\${...}`）—— " +
                "实际死值: \"$tagLiteral\"，禁止硬编码平台名。",
            tagLiteral.contains("$")
        )
        // 反检：禁止 #gateway:unknown / #gateway: 空 这类占位
        assertTrue(
            "extraTags 不得硬编码 #gateway:unknown / #gateway:\" 之类占位 —— 实际: \"$tagLiteral\"",
            !tagLiteral.contains("#gateway:unknown") &&
                !Regex("""^#gateway:\s*$""").matches(tagLiteral)
        )
    }

    // ----- helpers (clone from HermesGatewayControllerMemoryAutosaveWiringTest) -----

    private fun extractRunHermesAgentBlock(src: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst { it.contains("fun runHermesAgent") }
        check(startIdx >= 0) { "找不到 runHermesAgent 函数签名" }
        val endIdx = lines.subList(startIdx + 1, lines.size)
            .indexOfFirst { it.trimStart().startsWith("private fun ") || it.trimStart().startsWith("fun ") }
            .let { if (it < 0) lines.size - startIdx - 1 else it } + startIdx + 1
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

    private fun controllerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"),
            File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate HermesGatewayController.kt — cwd=${File(".").absolutePath}")
    }
}
