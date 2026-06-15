package com.ai.assistance.operit.services.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-026 (2026-06-15)：AI 价值判官 keepDecision=false 时**全段** skip。
 *
 * **背景**: SUMMARY_PROMPT 末尾要求模型输出 `【保留判断】=值得/不值得` (CN) /
 * `[Persistence Decision]=worth/not worth` (EN)。原行为：keepDecision=false 时只跳过父
 * `#auto_summary` 落库，**仍跑** `extractAndPersistFacts` 抽 bullet 当独立 fact。
 *
 * **2026-06-15 改**: 用户报"记忆库数量太多"。盘点发现该路径自相矛盾——AI 已判定整段
 * "不值得"保存，从中抽出来的 bullet 当独立 fact 与判定逻辑冲突；同时让该路径变成绕过
 * 父 #auto_summary dedup 的后门。改为 keepDecision=false 时**整段 skip**，连 fact 抽取
 * 也不跑（"父不存，子无依"——记忆精简策略一致性）。
 *
 * 对应 TC-AGENT-026-a..c（见 docs/hermes-test-cases.md）。
 */
class MessageCoordinationDelegateKeepDecisionWiringTest {

    private val source: String by lazy { File(delegatePath()).readText() }

    /**
     * 抓 keepDecision=false 分支体：从 `keepDecision == false` 行起，到匹配的 `}` 关闭为止。
     * 简化抓法——找 `if (keepDecision == false) {` 起、追踪括号深度到 0。
     */
    private fun extractKeepFalseBranch(): String {
        val anchor = source.indexOf("keepDecision == false")
        if (anchor < 0) return ""
        // 向前找最近的 `{`
        val openBrace = source.lastIndexOf('{', anchor)
        if (openBrace < 0) return ""
        // 实际我们更关心 if 块体。简化：从 `keepDecision == false` 后第一个 `{` 起追深度。
        val bodyStart = source.indexOf('{', anchor)
        if (bodyStart < 0) return ""
        var depth = 0
        var i = bodyStart
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(bodyStart, i + 1)
            }
            i++
        }
        return source.substring(bodyStart)
    }

    /**
     * TC-AGENT-026-a: keepDecision=false 分支必须**只 log + return**，不得调用 fact 抽取。
     */
    @Test
    fun `TC-AGENT-026-a keepDecision false branch skips fact extraction`() {
        val branch = extractKeepFalseBranch()
        assertTrue(
            "找不到 `keepDecision == false` 分支体 —— `forcePersistSummaryToMemory` 结构可能被改。",
            branch.isNotEmpty()
        )

        // 红线：分支体不得调 extractAndPersistFacts(
        assertFalse(
            "TC-AGENT-026-a 红线：keepDecision=false 分支体不得再调 `extractAndPersistFacts(` —— " +
                "AI 都判定整段不值得了，从中抽 bullet 当独立 fact 自相矛盾，且让该路径变成绕过父" +
                "#auto_summary dedup 的后门（2026-06-15 用户报记忆库节点过多优化）。\n实际分支体:\n$branch",
            Regex("""\bextractAndPersistFacts\s*\(""").containsMatchIn(branch)
        )

        // 同样不得叫 fact 抽取的别名
        assertFalse(
            "TC-AGENT-026-a 红线：keepDecision=false 分支体不得调 `extractFactsFromSummary(` 或 `persistExtractedFacts(`。\n实际分支体:\n$branch",
            Regex("""\b(extractFactsFromSummary|persistExtractedFacts)\s*\(""").containsMatchIn(branch)
        )

        // 必须 return（保证不掉到下面的父落档逻辑）
        assertTrue(
            "TC-AGENT-026-a: keepDecision=false 分支体必须含 `return` —— 否则会 fallthrough 到父落档。\n实际分支体:\n$branch",
            branch.contains("return")
        )
    }

    /**
     * TC-AGENT-026-b: keepDecision=false 分支日志必须含 chatId + len= 字面值。
     */
    @Test
    fun `TC-AGENT-026-b keepDecision false branch log carries chatId and len`() {
        val branch = extractKeepFalseBranch()
        assertTrue("找不到 keepDecision=false 分支体。", branch.isNotEmpty())

        assertTrue(
            "TC-AGENT-026-b: keepDecision=false 分支必须含 AppLogger / Log 调用（保留诊断能力）。\n实际分支体:\n$branch",
            Regex("""\b(AppLogger|Log)\.\w""").containsMatchIn(branch)
        )
        assertTrue(
            "TC-AGENT-026-b: keepDecision=false 日志必须 reference `chatId` —— 用户报记忆库异常时能 grep 出哪个 chat 的判定历史。\n实际分支体:\n$branch",
            branch.contains("chatId")
        )
        assertTrue(
            "TC-AGENT-026-b: keepDecision=false 日志必须 reference `len=` 或 `.length` —— 区分'空摘要被判 not worth' vs '正常长度被判 not worth'。\n实际分支体:\n$branch",
            branch.contains("len=") || branch.contains(".length")
        )
    }

    /**
     * TC-AGENT-026-c: parseAutoSummaryKeepDecision 函数必须存在；forcePersistSummaryToMemory 必须调用。
     */
    @Test
    fun `TC-AGENT-026-c parser function exists and is invoked`() {
        assertTrue(
            "TC-AGENT-026-c: 必须有 `parseAutoSummaryKeepDecision` 函数声明（R-AGENT-026 入口）。",
            Regex("""\bfun\s+parseAutoSummaryKeepDecision\s*\(""").containsMatchIn(source)
        )
        // forcePersistSummaryToMemory 函数体必须调用
        val parentBlock = extractParentBlock()
        assertTrue(
            "TC-AGENT-026-c: `forcePersistSummaryToMemory` 必须调用 `parseAutoSummaryKeepDecision(` —— 否则 R-AGENT-026 入口断了。",
            Regex("""\bparseAutoSummaryKeepDecision\s*\(""").containsMatchIn(parentBlock)
        )
    }

    private fun extractParentBlock(): String {
        // 锚定函数声明（不是 call site）：`fun forcePersistSummaryToMemory(`
        val anchorRegex = Regex("""\bfun\s+forcePersistSummaryToMemory\s*\(""")
        val anchor = anchorRegex.find(source)?.range?.first ?: return ""
        // 找该签名后的第一个 `{`（函数体起点）
        val openBrace = source.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(openBrace, i + 1)
            }
            i++
        }
        return source.substring(openBrace)
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
