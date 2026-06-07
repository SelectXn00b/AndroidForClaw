package com.ai.assistance.operit.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-013 bugfix (2026-06-08)：`FunctionalPrompts.SUMMARY_PROMPT` (CN) 与
 * `SUMMARY_PROMPT_EN` 必须采用**短重点风格**——禁止旧版本里那些反向引导扩写的指令
 * （"不少于 3 段" / "5+ 条" / "宁可内容多一点" / "绝不能以一句话敷衍" / "no fewer than
 * 3 paragraphs" / "Info point 5+" / "Prefer being detailed"），改用"精简 / 抓重点 /
 * concise / key facts" 等约束词。
 *
 * **背景**：用户 2026-06-08 反馈"摘要太长了，没有捉住对话重要内容"。根因之一是旧 prompt
 * 明文要求扩写，模型即使原对话只有 5 条也被强制要扩成 4 大段 + 5 条列表 ——> 篇幅膨胀，
 * 重点被堆字数稀释。
 *
 * **测试策略**：源码字符串扫描守 prompt 内容契约（FunctionalPrompts 是纯 const string，
 * 与 R-AGENT-009 / R-AGENT-014 同策略）。
 *
 * 对应 TC-AGENT-013-k（见 docs/hermes-test-cases.md）。
 */
class FunctionalPromptsSummaryConcisenessWiringTest {

    private val source: String by lazy { File(promptsPath()).readText() }

    /**
     * TC-AGENT-013-k: SUMMARY_PROMPT 与 SUMMARY_PROMPT_EN 必须避开旧版本的扩写指令，
     * 改用短重点风格。
     */
    @Test
    fun `TC-AGENT-013-k summary prompt enforces concise style`() {
        // 1. 禁止包含旧版本的扩写指令（中文）
        val bannedCnPhrases = listOf(
            "不少于3段", "不少于 3 段",
            "宁可内容多一点",
            "绝不能以一句话敷衍",
            "不要限制字数"
        )
        bannedCnPhrases.forEach { banned ->
            assertFalse(
                "FunctionalPrompts.kt 不得再含旧扩写指令『$banned』—— 它会反向引导模型堆字数、" +
                    "稀释重点。bugfix R-AGENT-013-k 要求改成短重点风格。",
                source.contains(banned)
            )
        }

        // 2. 禁止包含旧版本的扩写指令（英文）
        val bannedEnPhrases = listOf(
            "no fewer than 3 paragraphs",
            "Prefer being detailed",
            "do not limit length",
            "Info point 5+"
        )
        bannedEnPhrases.forEach { banned ->
            assertFalse(
                "FunctionalPrompts.kt 不得再含旧扩写指令『$banned』—— 它会反向引导模型堆字数、" +
                    "稀释重点。bugfix R-AGENT-013-k 要求改成短重点风格。",
                source.contains(banned)
            )
        }

        // 3. 必须含至少一个"精简/抓重点"约束词（中文）
        val requiredCnKeywords = listOf("精简", "重点", "简洁", "凝练")
        assertTrue(
            "SUMMARY_PROMPT (CN) 必须含『精简/重点/简洁/凝练』之一作为短重点风格的约束词 —— " +
                "光删扩写指令不够，必须正面引导模型抓重点。",
            requiredCnKeywords.any { source.contains(it) }
        )

        // 4. 必须含至少一个"concise/key facts"约束词（英文）
        val requiredEnKeywords = listOf("concise", "key facts", "key points", "succinct")
        assertTrue(
            "SUMMARY_PROMPT_EN 必须含『concise/key facts/key points/succinct』之一作为短重点风格的约束词。",
            requiredEnKeywords.any { source.contains(it, ignoreCase = true) }
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun promptsPath(): String =
        File(appSrcMainRoot(), "core/config/FunctionalPrompts.kt").path
}
