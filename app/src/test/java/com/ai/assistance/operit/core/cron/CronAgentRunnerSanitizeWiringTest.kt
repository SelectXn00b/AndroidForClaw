package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-SANITIZE-d (R-AGENT-031 / R-AGENT-035): `CronAgentRunner.run`
 * must call `HermesReplyMarkupStripper.strip(output)` AFTER
 * `responseBuilder.toString()` but BEFORE `saveJobOutput(...)` /
 * `deliver(...)`, so that `<think>` / `<tool>` / `<tool_result>` /
 * `<status>` markup never reaches:
 *  - the cron output file (`saveJobOutput`)
 *  - the in-app chat history (`writeLocalChatNote`)
 *  - the IM dispatch (`gateway.dispatchOutgoing` for Weixin / Telegram / Feishu)
 *
 * Source-scan only — actual `EnhancedAIService.sendMessage` flow requires
 * Android Context + network; literal-level wiring is enough to prevent
 * regression.
 */
class CronAgentRunnerSanitizeWiringTest {

    /**
     * Raw source with `/* ... */` block comments and `// ...` line comments stripped.
     * Without this, `indexOf("saveJobOutput(")` would hit the KDoc comment references
     * at lines 41/52 (preamble explaining the bug history) instead of the actual
     * call site at ~line 194, producing a false negative.
     */
    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }

    @Test
    fun `TC-CRON-SANITIZE-d run strips markup before persistence and delivery`() {
        // (1) Must reference HermesReplyMarkupStripper at all
        assertTrue(
            "TC-CRON-SANITIZE-d: `CronAgentRunner.run` must call " +
                "`HermesReplyMarkupStripper.strip(...)` before persistence.",
            source.contains("HermesReplyMarkupStripper.strip(")
        )

        // (2) The strip call must occur BEFORE saveJobOutput call
        val stripIdx = source.indexOf("HermesReplyMarkupStripper.strip(")
        val saveIdx = source.indexOf("saveJobOutput(")
        assertTrue(
            "TC-CRON-SANITIZE-d: literal `HermesReplyMarkupStripper.strip(` must appear " +
                "BEFORE literal `saveJobOutput(` in source order " +
                "(strip=$stripIdx, save=$saveIdx). Otherwise the cron output file would " +
                "still get raw `<think>` markup.",
            stripIdx in 0 until saveIdx
        )

        // (3) The strip call must occur BEFORE deliver(...) too (covers writeLocalChatNote + dispatchOutgoing)
        val deliverIdx = findDeliverCallSite(source)
        assertTrue(
            "TC-CRON-SANITIZE-d: literal `HermesReplyMarkupStripper.strip(` must appear " +
                "BEFORE the `deliver(...)` call site " +
                "(strip=$stripIdx, deliver=$deliverIdx). Otherwise the in-app chat note " +
                "and IM dispatch would still get raw `<think>` markup.",
            stripIdx in 0 until deliverIdx
        )
    }

    /** Find the `deliver(...)` CALL site (not the function declaration). */
    private fun findDeliverCallSite(text: String): Int {
        // Regex matches `deliver(` not preceded by `fun ` (to skip the declaration).
        val callRegex = Regex("""(?<!fun\s)\bdeliver\s*\(""")
        val match = callRegex.find(text) ?: return -1
        return match.range.first
    }

    /**
     * Strip Kotlin `/* ... */` (incl. KDoc `/** ... */`) block comments and `// ...`
     * line comments, preserving newlines so that line numbers in failure messages
     * remain meaningful.  Naive — does not honor string literals — but sufficient
     * for this source-scan test because no string literal in CronAgentRunner.kt
     * contains `saveJobOutput(` or `HermesReplyMarkupStripper.strip(`.
     */
    private fun stripKotlinComments(text: String): String {
        // Block comments: replace inner chars with spaces, keep newlines.
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        // Line comments: replace from `//` to end of line with spaces.
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun runnerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        return alt.path
    }
}
