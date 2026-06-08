package com.ai.assistance.operit.services.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-013 bugfix (2026-06-08)：`forcePersistSummaryToMemory` 落库前必须裁掉
 * `AIMessageManager.summarizeMemory` 拼接进 `summary` 消息的两段尾部块：
 *
 *   - "对话回顾" / "Dialogue review"（原文截头去尾粘一遍）
 *   - "【工具包预热】" / "[Package Warmup]"（高频包 use_package 返回原文）
 *
 * 这两段对 chat 历史里的下一轮 agent 上下文有用（保留），但写进 `MemoryRepository`
 * 长期记忆会让 `#auto_summary` 节点 content 大部分变成原文复述、淹没真摘要 ——
 * 用户 2026-06-08 反馈："摘要太长了，没有捉住对话重要内容"。
 *
 * **测试策略**：与 R-AGENT-013 a..i 同策略 —— 源码字符串扫描守 wiring。
 * 运行时正确性由手测兜底。
 *
 * 对应 TC-AGENT-013-j（见 docs/hermes-test-cases.md）。
 */
class MessageCoordinationDelegateSummaryStripWiringTest {

    private val source: String by lazy { File(delegatePath()).readText() }

    /**
     * TC-AGENT-013-j: `forcePersistSummaryToMemory` 函数体必须含按"对话回顾" /
     * "Dialogue review" / "【工具包预热】" / "[Package Warmup]" 任一分隔符做
     * `substringBefore` 或 `indexOf`+`substring` 裁剪的代码。
     */
    @Test
    fun `TC-AGENT-013-j strips dialogue review and package warmup before persist`() {
        val block = extractFunctionBlock(source, "forcePersistSummaryToMemory")

        // 必须 reference 至少一个分隔符字面字符串
        val mentionsDelimiter =
            block.contains("对话回顾") ||
                block.contains("Dialogue review") ||
                block.contains("【工具包预热】") ||
                block.contains("[Package Warmup]")
        assertTrue(
            "forcePersistSummaryToMemory 必须 reference 拼接块分隔符（对话回顾 / Dialogue review / " +
                "【工具包预热】 / [Package Warmup]）—— 否则没法裁剪。\n实际函数体:\n$block",
            mentionsDelimiter
        )

        // 必须有裁剪动作（substringBefore 或 indexOf+substring 或 split + 取首）
        val hasStripCall =
            Regex("""\.substringBefore\s*\(""").containsMatchIn(block) ||
                (Regex("""\.indexOf\s*\(""").containsMatchIn(block) &&
                    Regex("""\.substring\s*\(""").containsMatchIn(block)) ||
                Regex("""\.split\s*\(""").containsMatchIn(block)
        assertTrue(
            "forcePersistSummaryToMemory 必须实际做裁剪（substringBefore / indexOf+substring / " +
                "split 任一）—— 仅 reference 分隔符不够。\n实际函数体:\n$block",
            hasStripCall
        )
    }

    /**
     * TC-AGENT-015-g (R-AGENT-015 死循环防御, 2026-06-08)：`forcePersistSummaryToMemory` 在调
     * `memoryRepository.saveMemory(...)` 之前必须对 `summaryText` 做一次 `<memory-context>` fence
     * 剥离（调 `sanitizeContext` 或等价正则替换 `<memory-context>...</memory-context>` 整段）。
     *
     * 防御性代码：理论上 `summarizeMemory` 拿的是 `List<ChatMessage>`（持久化层不带 fence），
     * 剥不到东西；但一旦未来路径变化让 fence 漏进 ChatMessage（例如直接保存被 R-AGENT-015 注入
     * 过的 OpenAI message 内容），`#auto_summary` 节点就会带 fence 落库，下轮 prefetch 又把它
     * 召回拼回 user message —— 形成"注入 → 摘要 → 落库 → 召回 → 再注入"雪球。在落库前剥一次
     * 是 cheap 兜底。
     */
    @Test
    fun `TC-AGENT-015-g forcePersistSummaryToMemory sanitizes memory context before save`() {
        val block = extractFunctionBlock(source, "forcePersistSummaryToMemory")

        // 找 saveMemory 调用位置（落库点）
        val saveIdx = Regex("""\bsaveMemory\s*\(""").find(block)?.range?.first ?: -1
        assertTrue(
            "找不到 saveMemory(...) 调用 —— 先满足 R-AGENT-013-a/b 的 wiring。",
            saveIdx >= 0
        )

        val before = block.substring(0, saveIdx)

        // 接受多种 sanitize 形式：
        //  a) 直接调 sanitizeContext(...)（hermes-android MemoryManager.kt:345 已现成）
        //  b) inline 正则替换 `<memory-context>...</memory-context>` 整段
        //  c) 引用 _INTERNAL_CONTEXT_RE / MEMORY_CONTEXT_RE / fence 黑名单常量
        val hasSanitize =
            Regex("""\bsanitizeContext\s*\(""").containsMatchIn(before) ||
                Regex("""<\s*memory-context\s*>""").containsMatchIn(before) ||
                Regex("""\b_?INTERNAL_CONTEXT_RE\b""").containsMatchIn(before) ||
                Regex("""\bMEMORY_CONTEXT_RE\b""").containsMatchIn(before) ||
                Regex("""\bMEMORY_CONTEXT_FENCE\b""").containsMatchIn(before)
        assertTrue(
            "forcePersistSummaryToMemory 必须在 saveMemory 调用之前剥 `<memory-context>` fence —— " +
                "调 sanitizeContext(summaryText) 或 inline 正则替换 `<memory-context>...</memory-context>` " +
                "整段（防 R-AGENT-015 注入路径反向污染长期记忆雪球）。\n实际 saveMemory 之前的代码:\n$before",
            hasSanitize
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

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageCoordinationDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
