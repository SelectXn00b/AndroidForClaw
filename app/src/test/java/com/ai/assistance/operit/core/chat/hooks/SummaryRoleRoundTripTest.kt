package com.ai.assistance.operit.core.chat.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-001 — SUMMARY role wire-format contract.
 *
 * TC-AGENT-245-a/b/c/d: when a [PromptTurn] of kind [PromptTurnKind.SUMMARY]
 * is serialized to OpenAI / MIMO chat-completion messages, its `role` MUST be
 * `"user"` (not `"system"`). Otherwise it forms two consecutive `role=system`
 * messages with the chat system prompt and MIMO rejects with
 *   400 "Errors in message queue response".
 *
 * Tested via the single source of truth [PromptTurnKind.toOpenAiRole]
 * (TC-245-a) plus a source-code anti-regression scan over the three call
 * sites in `EnhancedAIService.toOpenAiMessages`, the gateway branch of
 * `HermesAdapter.sendMessage`, and `HermesAdapter.buildOpenAiMessages`
 * (TC-245-b/c). TC-245-d covers the full round-trip behaviour by walking
 * a representative chat history through the mapping table.
 */
class SummaryRoleRoundTripTest {

    // ===== TC-AGENT-245-a: helper is the single source of truth =====

    @Test
    fun `TC-245-a SUMMARY maps to user role at OpenAI wire boundary`() {
        assertEquals("user", PromptTurnKind.SUMMARY.toOpenAiRole())
    }

    @Test
    fun `TC-245-a SYSTEM still maps to system role`() {
        assertEquals("system", PromptTurnKind.SYSTEM.toOpenAiRole())
    }

    @Test
    fun `TC-245-a USER still maps to user role`() {
        assertEquals("user", PromptTurnKind.USER.toOpenAiRole())
    }

    @Test
    fun `TC-245-a ASSISTANT and TOOL_CALL both map to assistant role`() {
        assertEquals("assistant", PromptTurnKind.ASSISTANT.toOpenAiRole())
        assertEquals("assistant", PromptTurnKind.TOOL_CALL.toOpenAiRole())
    }

    @Test
    fun `TC-245-a TOOL_RESULT maps to tool role`() {
        assertEquals("tool", PromptTurnKind.TOOL_RESULT.toOpenAiRole())
    }

    // ===== TC-AGENT-245-b: HermesAdapter gateway + non-gateway must call helper =====

    @Test
    fun `TC-245-b HermesAdapter does not hard-code SUMMARY to system`() {
        val source = File(adapterSourcePath()).readText()
        assertFalse(
            "HermesAdapter.kt still contains 'PromptTurnKind.SUMMARY -> \"system\"' — regression of TC-AGENT-245-b/c",
            source.contains("PromptTurnKind.SUMMARY -> \"system\"")
        )
    }

    // ===== TC-AGENT-245-c: EnhancedAIService must not hard-code SUMMARY to system =====

    @Test
    fun `TC-245-c EnhancedAIService does not hard-code SUMMARY to system`() {
        val source = File(enhancedAIServiceSourcePath()).readText()
        assertFalse(
            "EnhancedAIService.kt still contains 'PromptTurnKind.SUMMARY -> \"system\"' — regression of TC-AGENT-245-a",
            source.contains("PromptTurnKind.SUMMARY -> \"system\"")
        )
    }

    // ===== TC-AGENT-245-d: full round-trip — no two adjacent system messages =====

    @Test
    fun `TC-245-d typical conversation with SUMMARY produces no consecutive system messages`() {
        // Representative history: original system prompt + a few turns + a
        // mid-conversation auto-summary turn + a follow-up user message.
        val history = listOf(
            PromptTurn(PromptTurnKind.SYSTEM, "You are helpful."),
            PromptTurn(PromptTurnKind.USER, "First question"),
            PromptTurn(PromptTurnKind.ASSISTANT, "First answer"),
            PromptTurn(PromptTurnKind.SUMMARY, "Summary of earlier turns"),
            PromptTurn(PromptTurnKind.USER, "Follow-up")
        )

        val wireRoles = history.map { it.kind.toOpenAiRole() }

        // No two consecutive `system` messages (this is the MIMO failure mode).
        for (i in 1 until wireRoles.size) {
            assertFalse(
                "Consecutive role=system at index ${i - 1} and $i: $wireRoles",
                wireRoles[i] == "system" && wireRoles[i - 1] == "system"
            )
        }
        // Sanity: SUMMARY came out as user (not system).
        assertEquals("user", wireRoles[3])
        // Sanity: the only `system` is the original system prompt.
        assertEquals(1, wireRoles.count { it == "system" })
    }

    @Test
    fun `TC-245-d helper covers every PromptTurnKind exhaustively`() {
        // Guard: if a new kind is added to the enum, this test forces us to
        // decide its wire role consciously rather than fall back to a default.
        PromptTurnKind.values().forEach { kind ->
            val role = kind.toOpenAiRole()
            assertTrue(
                "Unknown wire role '$role' for kind=$kind — must be one of system/user/assistant/tool",
                role in setOf("system", "user", "assistant", "tool")
            )
        }
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        // Tests run with working dir = module root (HermesApp/app).
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        // Fallback for running from project root.
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun adapterSourcePath(): String =
        File(appSrcMainRoot(), "hermes/HermesAdapter.kt").path

    private fun enhancedAIServiceSourcePath(): String =
        File(appSrcMainRoot(), "api/chat/EnhancedAIService.kt").path
}
