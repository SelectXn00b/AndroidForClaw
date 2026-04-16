package com.xiaomo.hermes.agent.context

import org.junit.Assert.*
import org.junit.Test

/**
 * Verify isReasoningTagProvider logic is aligned with OpenClaw 2026.3.11.
 *
 * OpenClaw source (compact-1mmJ_KWL.js):
 *   function isReasoningTagProvider(provider) {
 *     if (!provider) return false;
 *     const normalized = provider.trim().toLowerCase();
 *     if (normalized === "google" || normalized === "google-gemini-cli" || normalized === "google-generative-ai") return true;
 *     if (normalized.includes("minimax")) return true;
 *     return false;
 *   }
 *
 * These providers lack native reasoning API fields and need <think>/<final> tags
 * injected via the system prompt.
 */
class ContextBuilderReasoningTagTest {

    /**
     * Mirrors OpenClaw isReasoningTagProvider — extracted for testability.
     * Must match the logic in ContextBuilder.buildReasoningFormatSection().
     */
    private fun isReasoningTagProvider(provider: String?): Boolean {
        if (provider.isNullOrBlank()) return false
        val normalized = provider.trim().lowercase()
        if (normalized in listOf("google", "google-gemini-cli", "google-generative-ai")) return true
        if (normalized.contains("minimax")) return true
        return false
    }

    @Test
    fun `google requires reasoning tags`() {
        assertTrue(isReasoningTagProvider("google"))
    }

    @Test
    fun `google-gemini-cli requires reasoning tags`() {
        assertTrue(isReasoningTagProvider("google-gemini-cli"))
    }

    @Test
    fun `google-generative-ai requires reasoning tags`() {
        assertTrue(isReasoningTagProvider("google-generative-ai"))
    }

    @Test
    fun `minimax requires reasoning tags`() {
        assertTrue(isReasoningTagProvider("minimax"))
        assertTrue(isReasoningTagProvider("minimax-pro"))
        assertTrue(isReasoningTagProvider("abcminimax"))
    }

    @Test
    fun `case insensitive`() {
        assertTrue(isReasoningTagProvider("Google"))
        assertTrue(isReasoningTagProvider("GOOGLE"))
        assertTrue(isReasoningTagProvider("MiniMax"))
    }

    @Test
    fun `anthropic does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("anthropic"))
    }

    @Test
    fun `openai does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("openai"))
    }

    @Test
    fun `openrouter does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("openrouter"))
    }

    @Test
    fun `deepseek does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("deepseek"))
    }

    @Test
    fun `fireworks does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("fireworks"))
    }

    @Test
    fun `together does NOT require reasoning tags`() {
        assertFalse(isReasoningTagProvider("together"))
    }

    @Test
    fun `null and blank return false`() {
        assertFalse(isReasoningTagProvider(null))
        assertFalse(isReasoningTagProvider(""))
        assertFalse(isReasoningTagProvider("  "))
    }
}
