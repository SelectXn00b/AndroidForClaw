package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * --- TC-AGENT-003-rcfix-* : 飞书自动总结后空回复 bug 真因修复 ---
 * 见 docs/hermes-test-cases.md 域 AGENT — Helpers 段（R-AGENT-003）
 *
 * Root cause: compileHistoryForProvider 合并 turn 时丢弃 reasoningContent 字段，
 * 导致 MiMo 在带 tool_calls 的 assistant 历史回放时收不到 reasoning_content，
 * 返回 400 code:3 → 飞书显示 (empty response)。
 */
class StructuredToolCallBridgeTest {

    /** TC-AGENT-003-rcfix-a: 单个 ASSISTANT turn 携带 reasoningContent，合并后字段必须保留 */
    @Test fun compileHistoryForProvider_preservesReasoningContentOnSingleAssistantTurn() {
        val input = listOf(
            PromptTurn(
                kind = PromptTurnKind.ASSISTANT,
                content = "<tool name=\"web_search\"><param name=\"q\">x</param></tool>",
                reasoningContent = "user wants to search"
            )
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        assertEquals(1, out.size)
        assertEquals(PromptTurnKind.ASSISTANT, out[0].kind)
        assertEquals("user wants to search", out[0].reasoningContent)
    }

    /** TC-AGENT-003-rcfix-b: 多个相邻 ASSISTANT turn 合并时取第一个非空 reasoningContent */
    @Test fun compileHistoryForProvider_preservesFirstNonEmptyReasoningOnMergedAssistantTurns() {
        val input = listOf(
            PromptTurn(PromptTurnKind.ASSISTANT, "a", reasoningContent = "first reasoning"),
            PromptTurn(PromptTurnKind.ASSISTANT, "b", reasoningContent = "second reasoning")
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        assertEquals(1, out.size)
        assertEquals("first reasoning", out[0].reasoningContent)
    }

    /** TC-AGENT-003-rcfix-c: ASSISTANT + TOOL_CALL 合并到同一 ASSISTANT block，reasoning 必须保留 */
    @Test fun compileHistoryForProvider_keepsReasoningWhenAssistantThenToolCallMerged() {
        val input = listOf(
            PromptTurn(PromptTurnKind.ASSISTANT, "thinking text", reasoningContent = "rc-from-assistant"),
            PromptTurn(PromptTurnKind.TOOL_CALL, "<tool name=\"x\"></tool>", reasoningContent = null)
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        // 两个 turn 合并成一个 ASSISTANT block
        assertEquals(1, out.size)
        assertEquals(PromptTurnKind.ASSISTANT, out[0].kind)
        assertEquals("rc-from-assistant", out[0].reasoningContent)
    }

    /** TC-AGENT-003-rcfix-d: USER block 不应该带 reasoningContent（USER 永远不带） */
    @Test fun compileHistoryForProvider_userBlockHasNoReasoning() {
        val input = listOf(
            PromptTurn(PromptTurnKind.USER, "hello world")
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        assertEquals(1, out.size)
        assertEquals(PromptTurnKind.USER, out[0].kind)
        assertNull(out[0].reasoningContent)
    }

    /** TC-AGENT-003-rcfix-e: TOOL_RESULT block 不应该带 reasoningContent */
    @Test fun compileHistoryForProvider_toolResultBlockHasNoReasoning() {
        val input = listOf(
            PromptTurn(PromptTurnKind.TOOL_RESULT, "<tool_result>ok</tool_result>")
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        assertEquals(1, out.size)
        assertEquals(PromptTurnKind.TOOL_RESULT, out[0].kind)
        assertNull(out[0].reasoningContent)
    }

    /** TC-AGENT-003-rcfix-f: SUMMARY 合并到 USER_INPUT block 时不能错误地带上 reasoning */
    @Test fun compileHistoryForProvider_summaryMergedIntoUserBlockDoesNotLeakReasoning() {
        val input = listOf(
            PromptTurn(PromptTurnKind.USER, "user msg"),
            PromptTurn(PromptTurnKind.SUMMARY, "summary text")
        )
        val out = StructuredToolCallBridge.compileHistoryForProvider(input, useToolCall = true)
        // USER + SUMMARY 合并成一个 USER block
        assertEquals(1, out.size)
        assertEquals(PromptTurnKind.USER, out[0].kind)
        assertNull(out[0].reasoningContent)
        assertTrue(out[0].content.contains("user msg"))
        assertTrue(out[0].content.contains("summary text"))
    }

    /** TC-AGENT-003-rcfix-g: 空 history 仍然是空 list */
    @Test fun compileHistoryForProvider_emptyHistoryReturnsEmpty() {
        val out = StructuredToolCallBridge.compileHistoryForProvider(emptyList(), useToolCall = true)
        assertTrue(out.isEmpty())
    }
}
