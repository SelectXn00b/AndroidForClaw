package com.ai.assistance.operit.data.collects

import com.ai.assistance.operit.data.model.ApiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pure-JVM unit test for [ApiProviderConfigs] OpenCode Zen registry entry.
 *
 * Tied to: R-AGENT-002.
 *
 * Verifies that the OPENCODE_ZEN provider was correctly registered
 * (defaultModelName / defaultApiEndpoint / requiresApiKey=false).
 */
class ApiProviderConfigsOpenCodeZenTest {

    /** OPENCODE_ZEN endpoint matches the OpenCode Zen public chat-completions URL. */
    @Test
    fun `defaultApiEndpoint isOpenCodeZenChatCompletionsUrl`() {
        assertEquals(
            "https://opencode.ai/zen/v1/chat/completions",
            ApiProviderConfigs.getDefaultApiEndpoint(ApiProviderType.OPENCODE_ZEN)
        )
    }

    /** OPENCODE_ZEN default model is the BASELINE free model (nemotron-3-ultra-free). */
    @Test
    fun `defaultModelName isBaselineFreeModel`() {
        assertEquals(
            "nemotron-3-ultra-free",
            ApiProviderConfigs.getDefaultModelName(ApiProviderType.OPENCODE_ZEN)
        )
    }

    /**
     * OPENCODE_ZEN does NOT require a user-provided API key — the public
     * literal "public" is injected by ApiPreferences.DEFAULT_API_KEY at
     * config-creation time.
     */
    @Test
    fun `requiresApiKey isFalseForOpenCodeZen`() {
        assertFalse(
            "OPENCODE_ZEN must not require user API key",
            ApiProviderConfigs.requiresApiKey(
                ApiProviderType.OPENCODE_ZEN,
                "https://opencode.ai/zen/v1/chat/completions"
            )
        )
    }
}
