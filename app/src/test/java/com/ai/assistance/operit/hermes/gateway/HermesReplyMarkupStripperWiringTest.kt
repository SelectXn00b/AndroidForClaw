package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-SANITIZE-a (R-AGENT-031 / R-AGENT-035): the `<think>` /
 * `<tool>` / `<tool_result>` / `<status>` markup strip logic must live
 * in a top-level `object HermesReplyMarkupStripper`, and
 * `HermesGatewayController.stripMarkup` must delegate to it (avoid
 * double-source regex drift).
 *
 * Source-scan only — actual behavior verified in
 * `HermesReplyMarkupStripperBehaviorTest` (TC-CRON-SANITIZE-b/c) and
 * `CronAgentRunnerSanitizeWiringTest` (TC-CRON-SANITIZE-d).
 */
class HermesReplyMarkupStripperWiringTest {

    private val stripperSource: String by lazy { File(stripperPath()).readText() }
    private val controllerSource: String by lazy { File(controllerPath()).readText() }

    @Test
    fun `TC-CRON-SANITIZE-a HermesReplyMarkupStripper exists and Controller delegates`() {
        // (1) File must exist
        assertTrue(
            "TC-CRON-SANITIZE-a: file `HermesReplyMarkupStripper.kt` must exist at " +
                "app/src/main/java/com/ai/assistance/operit/hermes/gateway/",
            File(stripperPath()).exists()
        )

        // (2) Must be a top-level object
        assertTrue(
            "TC-CRON-SANITIZE-a: must declare `object HermesReplyMarkupStripper`.\n" +
                "Actual head:\n${stripperSource.take(400)}",
            Regex("""\bobject\s+HermesReplyMarkupStripper\b""").containsMatchIn(stripperSource)
        )

        // (3) Must expose `fun strip(...)`
        assertTrue(
            "TC-CRON-SANITIZE-a: must expose `fun strip(...)`.",
            Regex("""fun\s+strip\s*\(""").containsMatchIn(stripperSource)
        )

        // (4) Must reference ChatMarkupRegex (canonical regex source)
        assertTrue(
            "TC-CRON-SANITIZE-a: must reference `ChatMarkupRegex`.",
            stripperSource.contains("ChatMarkupRegex")
        )

        // (5) Must reference the four regex fields used by HermesGatewayController.stripMarkup
        for (literal in listOf("thinkTag", "toolTag", "toolResultTag", "statusTag")) {
            assertTrue(
                "TC-CRON-SANITIZE-a: must reference `ChatMarkupRegex.$literal`.",
                stripperSource.contains(literal)
            )
        }

        // (6) Must contain unclosed-think regex literal (the crucial fix)
        assertTrue(
            "TC-CRON-SANITIZE-a: must contain `UNCLOSED_THINK` regex (handles unclosed `<think>` residue).",
            stripperSource.contains("UNCLOSED_THINK")
        )

        // (7) Controller.stripMarkup must delegate to HermesReplyMarkupStripper (no duplicate regex source).
        // Supports both block-body (`fun stripMarkup(...): String { ... }`) and Kotlin
        // expression-body (`fun stripMarkup(...): String = HermesReplyMarkupStripper.strip(text)`).
        val stripMarkupBody = extractFunctionBody(controllerSource, "stripMarkup")
            ?: error("TC-CRON-SANITIZE-a: could not locate `stripMarkup` function body in HermesGatewayController.kt")
        assertTrue(
            "TC-CRON-SANITIZE-a: `HermesGatewayController.stripMarkup` must delegate to " +
                "`HermesReplyMarkupStripper` (avoid double source of truth).\nActual body:\n$stripMarkupBody",
            stripMarkupBody.contains("HermesReplyMarkupStripper")
        )
    }

    private fun stripperPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesReplyMarkupStripper.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesReplyMarkupStripper.kt")
        return alt.path
    }

    private fun controllerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        return alt.path
    }

    /**
     * Locate the body of `fun name(...): T = <expr>` OR `fun name(...): T { ... }`.
     *
     * - Expression-body: returns everything from `=` up to the end of the statement
     *   (next top-level newline that isn't a continuation).
     * - Block-body: brace-walk from `{` to matching `}`.
     */
    private fun extractFunctionBody(source: String, name: String): String? {
        // Match the signature up to either `{` (block) or `=` (expression).
        val sigRegex = Regex("""fun\s+$name\s*\([^)]*\)\s*(?::\s*[^\n={]+)?\s*([{=])""")
        val match = sigRegex.find(source) ?: return null
        val opener = match.groupValues[1]
        val bodyStart = match.range.last + 1
        return if (opener == "{") {
            var depth = 1
            var i = bodyStart
            while (i < source.length && depth > 0) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            source.substring(bodyStart, i - 1)
        } else {
            // Expression-body: read until end of line (Kotlin expression bodies are
            // single-expression by definition; if it spans lines via `.replace(...)`
            // chains the parser allows it, but for our delegate we only need the
            // immediate RHS).  Take the rest of the file conservatively until the
            // next blank line or `private fun` / `fun ` / `}` at column 0.
            val rest = source.substring(bodyStart)
            val endRegex = Regex("""\n\s*\n|\n(?=(private\s+)?fun\s+|\})""")
            val endMatch = endRegex.find(rest)
            if (endMatch != null) rest.substring(0, endMatch.range.first) else rest
        }
    }
}
