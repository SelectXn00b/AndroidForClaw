package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-GW-DIAG-002 source-scan wiring tests for [GatewayFileLogger] +
 * [HermesGatewayController].
 *
 * Each `@Test` corresponds to a TC-GW-DIAG-002-x row in
 * `docs/hermes-test-cases.md`.
 *
 * **Source-scan rationale**: `GatewayFileLogger` writes to
 * `Environment.getExternalStoragePublicDirectory(...)` which is Android-only;
 * a pure-JVM behavioral test would need Robolectric. The trim algorithm is
 * simple enough (`lastIndexOf` + `substring`) that literal-level assertions
 * on the source file are sufficient to prevent regression. Same pattern used
 * by [AgentStreamingSidecarShapeWiringTest] and the cron analogues.
 */
class GatewayFileLoggerRunRetentionWiringTest {

    private val loggerSource: String by lazy {
        stripKotlinComments(File(loggerPath()).readText())
    }

    private val controllerSource: String by lazy {
        stripKotlinComments(File(controllerPath()).readText())
    }

    // ---------------------------------------------------------------------
    // TC-GW-DIAG-002-a: logger exposes startRun / endRun API + trim helper
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-DIAG-002-a logger exposes startRun endRun API`() {
        assertTrue(
            "TC-GW-DIAG-002-a: `GatewayFileLogger.kt` must declare `fun startRun(` so callers " +
                "can mark the beginning of an agent run.",
            loggerSource.contains("fun startRun(")
        )
        assertTrue(
            "TC-GW-DIAG-002-a: `GatewayFileLogger.kt` must declare `fun endRun(` so callers " +
                "can mark the end of an agent run.",
            loggerSource.contains("fun endRun(")
        )
        // Run-boundary marker literals — `▶▶▶ RUN START` / `◀◀◀ RUN END` —
        // are written into gateway.log so the trim helper can locate prior
        // run boundaries by lastIndexOf.
        assertTrue(
            "TC-GW-DIAG-002-a: must define `▶▶▶ RUN START` literal (used both as the on-disk " +
                "banner and the lastIndexOf needle).",
            loggerSource.contains("▶▶▶ RUN START")
        )
        assertTrue(
            "TC-GW-DIAG-002-a: must define `◀◀◀ RUN END` literal for the end-of-run banner.",
            loggerSource.contains("◀◀◀ RUN END")
        )
        assertTrue(
            "TC-GW-DIAG-002-a: must define `private fun trimToLastTwoRuns(` — the helper that " +
                "drops everything before the last RUN_START marker at startRun time.",
            loggerSource.contains("private fun trimToLastTwoRuns(")
        )
        assertTrue(
            "TC-GW-DIAG-002-a: trim strategy must reference `lastIndexOf(RUN_START_MARKER)` " +
                "(scan-from-end for the most recent prior run boundary). Without lastIndexOf the " +
                "retention semantics would be wrong (e.g. first-match-from-start would always " +
                "keep ALL runs after the very first START).",
            loggerSource.contains("lastIndexOf(RUN_START_MARKER)")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-DIAG-002-b: controller wires startRun + 3 endRun call sites
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-DIAG-002-b controller wires startRun endRun at boundaries`() {
        assertTrue(
            "TC-GW-DIAG-002-b: `HermesGatewayController.kt` must call " +
                "`GatewayFileLogger.startRun(historyChatId)` at the entry of `runHermesAgent` " +
                "so the trim happens before this run's content is written.",
            controllerSource.contains("GatewayFileLogger.startRun(historyChatId)")
        )
        assertTrue(
            "TC-GW-DIAG-002-b: must call `GatewayFileLogger.endRun(historyChatId)` on the " +
                "normal-completion return path.",
            controllerSource.contains("GatewayFileLogger.endRun(historyChatId)")
        )
        // The interrupted path tags the label so operators can tell which
        // termination cause produced the banner.
        assertTrue(
            "TC-GW-DIAG-002-b: must call `GatewayFileLogger.endRun(\"\$historyChatId (interrupted)\")` " +
                "on the interrupt-detected return path (idx ~615 in controller).",
            controllerSource.contains("GatewayFileLogger.endRun(\"\$historyChatId (interrupted)\")")
        )
        assertTrue(
            "TC-GW-DIAG-002-b: must call `GatewayFileLogger.endRun(\"\$historyChatId (early-out /new)\")` " +
                "on the `/new` short-circuit return path (idx ~322 in controller).",
            controllerSource.contains("GatewayFileLogger.endRun(\"\$historyChatId (early-out /new)\")")
        )
        // Cardinality guards — exactly 1 startRun + 3 endRun. Drift here
        // would silently break retention (e.g. an extra startRun would trim
        // mid-run and lose history; a missing endRun would mean the
        // affected return path produces an unclosed run banner that the
        // next startRun would happily keep as "previous run").
        val startRunCount = countOccurrences(controllerSource, "GatewayFileLogger.startRun(")
        assertEquals(
            "TC-GW-DIAG-002-b: `GatewayFileLogger.startRun(` must appear exactly once in the " +
                "controller (at the runHermesAgent entry). Found $startRunCount.",
            1,
            startRunCount
        )
        val endRunCount = countOccurrences(controllerSource, "GatewayFileLogger.endRun(")
        assertEquals(
            "TC-GW-DIAG-002-b: `GatewayFileLogger.endRun(` must appear exactly 3 times in the " +
                "controller (normal / interrupted / early-out paths). Found $endRunCount.",
            3,
            endRunCount
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-DIAG-002-c: trimToLastTwoRuns retains only the most recent
    //                  prior run (source-scan: verify algorithm shape)
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-DIAG-002-c trimToLastTwoRuns retains only the most recent prior run`() {
        // The algorithm:
        //   val lastStart = content.lastIndexOf(RUN_START_MARKER)
        //   if (lastStart <= 0) return
        //   file.writeText(content.substring(lastStart))
        // is short enough that source-literal checks are an acceptable
        // proxy for a behavioral test. Robolectric setup just for
        // Environment.getExternalStoragePublicDirectory would dwarf the
        // value.
        assertTrue(
            "TC-GW-DIAG-002-c: trim must scan for the LAST RUN_START_MARKER " +
                "(via lastIndexOf) so the most recent prior run is preserved and everything " +
                "older is dropped.",
            loggerSource.contains("lastIndexOf(RUN_START_MARKER)")
        )
        assertTrue(
            "TC-GW-DIAG-002-c: trim must early-return when `lastStart <= 0` — no prior run, " +
                "or already at top — to avoid no-op writes.",
            loggerSource.contains("if (lastStart <= 0) return")
        )
        assertTrue(
            "TC-GW-DIAG-002-c: trim must write back `content.substring(lastStart)` (cut " +
                "everything BEFORE the last START so that START becomes the new file head). " +
                "Without this the trim would have no effect on disk.",
            loggerSource.contains("file.writeText(content.substring(lastStart))")
        )
        // Regression guard: byte-midpoint truncation (the old design from
        // `trimIfNeeded`) must NOT be reused here — the run-based retention
        // would be defeated if the trim cut at content.length / 2 instead
        // of at a run boundary.
        val trimMethodStart = loggerSource.indexOf("private fun trimToLastTwoRuns(")
        assertTrue(
            "TC-GW-DIAG-002-c: trimToLastTwoRuns method must exist in the source.",
            trimMethodStart >= 0
        )
        // Scope: from method start to next blank line outside the function
        // is hard to do in regex; use a generous window check instead. The
        // method body is ~10 lines so 800 chars window is enough.
        val trimWindowEnd = (trimMethodStart + 800).coerceAtMost(loggerSource.length)
        val trimWindow = loggerSource.substring(trimMethodStart, trimWindowEnd)
        assertTrue(
            "TC-GW-DIAG-002-c: trimToLastTwoRuns must NOT use `content.length / 2` (byte-midpoint " +
                "truncation belongs to the legacy size-cap `trimIfNeeded`, not the run-based " +
                "retention).",
            !trimWindow.contains("content.length / 2")
        )
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++
            idx = found + needle.length
        }
        return count
    }

    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments
     * while preserving newlines so failure messages stay meaningful and
     * string literals inside docstrings don't pollute literal-content
     * checks. Mirrors the helper in AgentStreamingSidecarShapeWiringTest.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun loggerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/GatewayFileLogger.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/GatewayFileLogger.kt")
        return alt.path
    }

    private fun controllerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        return alt.path
    }
}
