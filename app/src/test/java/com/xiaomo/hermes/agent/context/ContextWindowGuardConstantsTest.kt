package com.xiaomo.hermes.agent.context

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verify ContextWindowGuard / ContextManager / MessageCompactor defaults
 * are aligned with OpenClaw source values.
 *
 * OpenClaw source reference:
 * - auth-profiles-DRjqKE3G.js line 374: const DEFAULT_CONTEXT_TOKENS = 2e5  (200,000)
 * - auth-profiles-DRjqKE3G.js line 96107: const MAX_TOOL_RESULT_CONTEXT_SHARE = .3
 * - tool-result-truncation.ts: DEFAULT_MAX_LIVE_TOOL_RESULT_CHARS = 40_000 (was 400K, reduced 10x)
 * - auth-profiles-DRjqKE3G.js line 97030: const EMBEDDED_COMPACTION_TIMEOUT_MS = 3e5 (300,000)
 * - auth-profiles-DRjqKE3G.js line 103642: const TOOL_RESULT_MAX_CHARS = 8e3 (8,000)
 */
class ContextWindowGuardConstantsTest {

    @Test
    fun `DEFAULT_CONTEXT_WINDOW_TOKENS matches OpenClaw DEFAULT_CONTEXT_TOKENS of 200000`() {
        assertEquals(200_000, ContextWindowGuard.DEFAULT_CONTEXT_WINDOW_TOKENS)
    }

    @Test
    fun `CONTEXT_WINDOW_HARD_MIN_TOKENS is 16000`() {
        assertEquals(16_000, ContextWindowGuard.CONTEXT_WINDOW_HARD_MIN_TOKENS)
    }

    @Test
    fun `MAX_TOOL_RESULT_CONTEXT_SHARE matches OpenClaw value of 0_3`() {
        assertEquals(0.3, ToolResultContextGuard.MAX_TOOL_RESULT_CONTEXT_SHARE, 0.001)
    }

    @Test
    fun `HARD_MAX_TOOL_RESULT_CHARS matches OpenClaw value of 40000`() {
        assertEquals(40_000, ToolResultContextGuard.HARD_MAX_TOOL_RESULT_CHARS)
    }

    @Test
    fun `COMPACTION_TIMEOUT matches OpenClaw EMBEDDED_COMPACTION_TIMEOUT_MS of 300000`() {
        assertEquals(300_000L, MessageCompactor.DEFAULT_COMPACTION_TIMEOUT_MS)
    }

    @Test
    fun `MAX_TOOL_RESULT_CHARS matches OpenClaw TOOL_RESULT_MAX_CHARS of 8000`() {
        // Access private constant via reflection
        val field = ToolResultTruncator::class.java.getDeclaredField("MAX_TOOL_RESULT_CHARS")
            .apply { isAccessible = true }
        assertEquals(8_000, field.getInt(null))
    }
}
