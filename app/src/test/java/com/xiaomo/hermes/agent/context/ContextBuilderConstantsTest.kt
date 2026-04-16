package com.xiaomo.hermes.agent.context

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verify constants are aligned with OpenClaw source values.
 *
 * OpenClaw source references:
 * - tokens-T07bZqlD.js: SILENT_REPLY_TOKEN = "NO_REPLY", HEARTBEAT_TOKEN = "HEARTBEAT_OK"
 * - auth-profiles-MKCH-k1W.js: MIN_BOOTSTRAP_FILE_BUDGET_CHARS = 64, BOOTSTRAP_TAIL_RATIO = 0.2
 * - skills-*.js: DEFAULT_MAX_SKILLS_IN_PROMPT = 150, DEFAULT_MAX_SKILLS_PROMPT_CHARS = 30000
 * - redact-snapshot-*.js: bootstrapMaxChars default = 20000, bootstrapTotalMaxChars default = 150000
 *
 * After split: bootstrap constants live in BootstrapFiles.kt (package-level internal),
 * SILENT_REPLY_TOKEN remains on ContextBuilder.Companion.
 */
class ContextBuilderConstantsTest {

    @Test
    fun `SILENT_REPLY_TOKEN matches OpenClaw`() {
        assertEquals("NO_REPLY", ContextBuilder.SILENT_REPLY_TOKEN)
    }

    @Test
    fun `MIN_BOOTSTRAP_FILE_BUDGET_CHARS matches OpenClaw value of 64`() {
        assertEquals(64, MIN_BOOTSTRAP_FILE_BUDGET_CHARS)
    }

    @Test
    fun `DEFAULT_BOOTSTRAP_MAX_CHARS matches OpenClaw default of 20000`() {
        assertEquals(20_000, DEFAULT_BOOTSTRAP_MAX_CHARS)
    }

    @Test
    fun `DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS matches OpenClaw default of 150000`() {
        assertEquals(150_000, DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS)
    }

    @Test
    fun `BOOTSTRAP_TAIL_RATIO matches OpenClaw value of 0_2`() {
        assertEquals(0.2, BOOTSTRAP_TAIL_RATIO, 0.001)
    }

    @Test
    fun `BOOTSTRAP_FILES order matches OpenClaw loadWorkspaceBootstrapFiles`() {
        // OpenClaw agent-scope-Cwa3GjIC.js: loadWorkspaceBootstrapFiles() loads:
        // AGENTS -> SOUL -> TOOLS -> IDENTITY -> USER -> HEARTBEAT -> BOOTSTRAP -> memory/*
        // MEMORY.md is resolved dynamically in OpenClaw but statically appended here.
        val expectedOrder = listOf(
            "AGENTS.md",
            "SOUL.md",
            "TOOLS.md",
            "IDENTITY.md",
            "USER.md",
            "HEARTBEAT.md",
            "BOOTSTRAP.md",
            "MEMORY.md"
        )
        assertEquals(expectedOrder, BOOTSTRAP_FILES)
    }
}
