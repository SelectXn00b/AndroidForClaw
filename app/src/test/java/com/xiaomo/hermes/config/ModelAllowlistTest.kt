package com.xiaomo.hermes.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ModelAllowlist — glob matching and allow/block logic
 */
class ModelAllowlistTest {

    // ===== Glob matching =====

    @Test
    fun `exact match works`() {
        assertTrue(ModelAllowlist.matchesGlob("gpt-4o", "gpt-4o"))
    }

    @Test
    fun `wildcard at end matches`() {
        assertTrue(ModelAllowlist.matchesGlob("gpt-4o", "gpt-*"))
        assertTrue(ModelAllowlist.matchesGlob("gpt-4o-mini", "gpt-*"))
    }

    @Test
    fun `wildcard at start matches`() {
        assertTrue(ModelAllowlist.matchesGlob("claude-3.5-sonnet", "*-sonnet"))
    }

    @Test
    fun `wildcard in middle matches`() {
        assertTrue(ModelAllowlist.matchesGlob("gemini-3-pro-preview", "gemini-*-preview"))
    }

    @Test
    fun `case insensitive matching`() {
        assertTrue(ModelAllowlist.matchesGlob("GPT-4o", "gpt-*"))
    }

    @Test
    fun `non-matching pattern returns false`() {
        assertFalse(ModelAllowlist.matchesGlob("claude-3.5-sonnet", "gpt-*"))
    }

    // ===== Allow/block logic =====

    @Test
    fun `null config allows all`() {
        assertTrue(ModelAllowlist.isModelAllowed("gpt-4o", null))
    }

    @Test
    fun `empty config allows all`() {
        assertTrue(ModelAllowlist.isModelAllowed("gpt-4o", ModelAllowlistConfig()))
    }

    @Test
    fun `allow list permits matching models`() {
        val config = ModelAllowlistConfig(allow = listOf("gpt-*", "claude-*"))
        assertTrue(ModelAllowlist.isModelAllowed("gpt-4o", config))
        assertTrue(ModelAllowlist.isModelAllowed("claude-3.5-sonnet", config))
        assertFalse(ModelAllowlist.isModelAllowed("gemini-pro", config))
    }

    @Test
    fun `block list rejects matching models`() {
        val config = ModelAllowlistConfig(block = listOf("gpt-3.5-*"))
        assertTrue(ModelAllowlist.isModelAllowed("gpt-4o", config))
        assertFalse(ModelAllowlist.isModelAllowed("gpt-3.5-turbo", config))
    }

    @Test
    fun `block takes precedence over allow`() {
        val config = ModelAllowlistConfig(
            allow = listOf("gpt-*"),
            block = listOf("gpt-3.5-*")
        )
        assertTrue(ModelAllowlist.isModelAllowed("gpt-4o", config))
        assertFalse(ModelAllowlist.isModelAllowed("gpt-3.5-turbo", config))
    }
}
