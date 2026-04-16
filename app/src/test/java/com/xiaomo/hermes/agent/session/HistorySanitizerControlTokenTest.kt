package com.xiaomo.hermes.agent.session

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for control token stripping (aligned with OpenClaw 2026.3.11 #42173).
 *
 * OpenClaw behavior (stripModelSpecialTokens):
 * - Regex: /<[|｜][^|｜]*[|｜]>/g
 * - Replace each match with a single space
 * - Collapse runs of multiple spaces into one
 * - Trim leading/trailing whitespace
 *
 * Verifies that leaked model delimiters from GLM-5, DeepSeek, etc.
 * are stripped from assistant text before reaching end users.
 */
class HistorySanitizerControlTokenTest {

    @Test
    fun `strips half-width control tokens`() {
        val input = "Hello <|endoftext|> world"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Hello world", result)
    }

    @Test
    fun `strips full-width control tokens`() {
        val input = "Hello <｜end▁of▁sentence｜> world"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Hello world", result)
    }

    @Test
    fun `strips multiple control tokens`() {
        val input = "<|im_start|>assistant\nHi there<|im_end|>"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("assistant\nHi there", result)
    }

    @Test
    fun `preserves normal text`() {
        val input = "This is a normal response with no control tokens."
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals(input, result)
    }

    @Test
    fun `preserves normal angle brackets`() {
        val input = "Use <div> tags and x < y > z"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals(input, result)
    }

    @Test
    fun `fast path for text without angle brackets`() {
        val input = "No brackets here at all"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertSame(input, result) // Should be exact same reference (fast path)
    }

    @Test
    fun `strips DeepSeek style tokens`() {
        // Two tokens replaced by spaces, then collapsed
        val input = "Result: <｜tool▁call▁begin｜>search<｜tool▁call▁end｜>"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Result: search", result)
    }

    @Test
    fun `handles empty string`() {
        assertEquals("", HistorySanitizer.stripControlTokensFromText(""))
    }

    @Test
    fun `collapses multiple spaces after token removal`() {
        val input = "A  <|token|>  B"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("A B", result)
    }

    @Test
    fun `adjacent tokens produce single space`() {
        val input = "Start<|a|><|b|>End"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Start End", result)
    }

    @Test
    fun `leading token is trimmed`() {
        val input = "<|im_start|>Hello"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Hello", result)
    }

    @Test
    fun `trailing token is trimmed`() {
        val input = "Hello<|im_end|>"
        val result = HistorySanitizer.stripControlTokensFromText(input)
        assertEquals("Hello", result)
    }
}
