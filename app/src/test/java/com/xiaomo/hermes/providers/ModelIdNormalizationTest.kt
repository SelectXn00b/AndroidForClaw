package com.xiaomo.hermes.providers

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ModelIdNormalization — aligned with OpenClaw model-id-normalization.ts
 */
class ModelIdNormalizationTest {

    // ===== Google model ID normalization =====

    @Test
    fun `gemini-3-pro normalizes to preview`() {
        assertEquals("gemini-3-pro-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3-pro"))
    }

    @Test
    fun `gemini-3-flash normalizes to preview`() {
        assertEquals("gemini-3-flash-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3-flash"))
    }

    @Test
    fun `gemini-3_1-pro normalizes to preview`() {
        assertEquals("gemini-3.1-pro-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3.1-pro"))
    }

    @Test
    fun `gemini-3_1-flash-lite normalizes to preview`() {
        assertEquals("gemini-3.1-flash-lite-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3.1-flash-lite"))
    }

    @Test
    fun `gemini-3_1-flash maps to gemini-3-flash-preview`() {
        assertEquals("gemini-3-flash-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3.1-flash"))
    }

    @Test
    fun `gemini-3_1-flash-preview also maps to gemini-3-flash-preview`() {
        assertEquals("gemini-3-flash-preview", ModelIdNormalization.normalizeGoogleModelId("gemini-3.1-flash-preview"))
    }

    @Test
    fun `unknown google model passes through`() {
        assertEquals("gemini-2.0-flash", ModelIdNormalization.normalizeGoogleModelId("gemini-2.0-flash"))
    }

    // ===== xAI model ID normalization =====

    @Test
    fun `grok experimental reasoning normalizes`() {
        assertEquals(
            "grok-4.20-reasoning",
            ModelIdNormalization.normalizeXaiModelId("grok-4.20-experimental-beta-0304-reasoning")
        )
    }

    @Test
    fun `grok experimental non-reasoning normalizes`() {
        assertEquals(
            "grok-4.20-non-reasoning",
            ModelIdNormalization.normalizeXaiModelId("grok-4.20-experimental-beta-0304-non-reasoning")
        )
    }

    @Test
    fun `unknown xai model passes through`() {
        assertEquals("grok-3", ModelIdNormalization.normalizeXaiModelId("grok-3"))
    }

    // ===== Provider-based normalization =====

    @Test
    fun `normalizeModelId dispatches to google for google provider`() {
        assertEquals("gemini-3-pro-preview", ModelIdNormalization.normalizeModelId("google", "gemini-3-pro"))
    }

    @Test
    fun `normalizeModelId dispatches to xai for xai provider`() {
        assertEquals(
            "grok-4.20-reasoning",
            ModelIdNormalization.normalizeModelId("xai", "grok-4.20-experimental-beta-0304-reasoning")
        )
    }

    @Test
    fun `normalizeModelId passes through for other providers`() {
        assertEquals("gpt-4o", ModelIdNormalization.normalizeModelId("openai", "gpt-4o"))
        assertEquals("claude-3.5-sonnet", ModelIdNormalization.normalizeModelId("anthropic", "claude-3.5-sonnet"))
    }
}
