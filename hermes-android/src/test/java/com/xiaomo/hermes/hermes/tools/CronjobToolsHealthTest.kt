/**
 * R-AGENT-044: cron self-diagnostic tool — `cronjob(action="health")`.
 *
 * Source-scan tests for the implementation contract (the actual JVM-side
 * behavior path requires `getHermesHome()` → `getAppContext()`, which is
 * Robolectric-only; same constraint as JobsTest). The schema test is the
 * agent-discoverability gate.
 */
package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CronjobToolsHealthTest {

    private val source: String by lazy { File(toolsPath()).readText() }

    /** Brace-walked body of the top-level fun whose name matches [name]. */
    private fun extractFunBody(name: String): String {
        val anchor = Regex("""\bfun\s+${Regex.escape(name)}\s*\(""").find(source)?.range?.first
            ?: error("Cannot find fun $name( in CronjobTools.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find $name() opening brace" }
        val start = i
        var depth = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(start, i + 1)
            }
            i++
        }
        return source.substring(start)
    }

    /** Source-region for a single `when` branch starting at `"$action" ->`. */
    private fun extractWhenBranch(funBody: String, action: String): String {
        val anchor = Regex(""""${Regex.escape(action)}"\s*->\s*""").find(funBody)?.range?.last
            ?: error("Cannot find when branch for action='$action'")
        // Branch is one block expression `{...}` immediately after `->`.
        var i = funBody.indexOf('{', anchor)
        require(i >= 0) { "Branch '$action' must be a block expression" }
        val start = i
        var depth = 0
        while (i < funBody.length) {
            val c = funBody[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return funBody.substring(start, i + 1)
            }
            i++
        }
        return funBody.substring(start)
    }

    // -------- TC-AGENT-044-a (schema discoverability) --------
    /**
     * TC-AGENT-044-a: `CRONJOB_SCHEMA` must advertise the `health` action so the
     * agent learns about the diagnostic capability through the schema alone, and
     * the description must enumerate every payload field name so the agent can
     * interpret responses without out-of-band docs.
     */
    @Test
    fun `TC-AGENT-044-a schema advertises health action and payload fields`() {
        val schemaIdx = source.indexOf("CRONJOB_SCHEMA")
        assertTrue("TC-AGENT-044-a: CRONJOB_SCHEMA declaration not found", schemaIdx >= 0)
        val schemaSlice = source.substring(schemaIdx)

        // (1) `health` must appear inside the schema (action enum description).
        assertTrue(
            "TC-AGENT-044-a: schema must list 'health' as an action value",
            Regex("""\bhealth\b""").containsMatchIn(schemaSlice)
        )

        // (2) Payload keys agent must be able to interpret.
        val expectedKeys = listOf(
            "worker_registered",
            "worker_state",
            "last_tick_at",
            "next_scheduled_at",
            "pending_due_jobs",
            "recent_runs",
            "immediate_runner_wired",
            "enqueue_last_error",
        )
        for (key in expectedKeys) {
            assertTrue(
                "TC-AGENT-044-a: schema description must mention payload key `$key`",
                schemaSlice.contains(key)
            )
        }
    }

    // -------- TC-AGENT-044-b (probe wired path) --------
    /**
     * TC-AGENT-044-b: `cronjob` must have a `"health"` `when` branch that calls
     * the injected `cronHealthProbe` to read worker state. The branch must
     * surface the probe-supplied `worker_registered` / `worker_state` /
     * `next_scheduled_at` keys into the response.
     */
    @Test
    fun `TC-AGENT-044-b health branch invokes cronHealthProbe and merges worker fields`() {
        val funBody = extractFunBody("cronjob")
        val branch = extractWhenBranch(funBody, "health")

        // The branch must reference the probe injection slot.
        assertTrue(
            "TC-AGENT-044-b: health branch must reference cronHealthProbe (the injection slot)",
            branch.contains("cronHealthProbe")
        )
        // The branch must merge worker_registered into the response payload.
        assertTrue(
            "TC-AGENT-044-b: health branch must emit worker_registered key",
            branch.contains("worker_registered")
        )
        // The branch must merge worker_state.
        assertTrue(
            "TC-AGENT-044-b: health branch must emit worker_state key",
            branch.contains("worker_state")
        )
        // The branch must emit next_scheduled_at (probe surface).
        assertTrue(
            "TC-AGENT-044-b: health branch must emit next_scheduled_at",
            branch.contains("next_scheduled_at")
        )
        // The branch must emit success=true on the happy path.
        assertTrue(
            "TC-AGENT-044-b: health branch must emit success=true",
            Regex(""""success"\s*to\s*true""").containsMatchIn(branch)
        )
    }

    // -------- TC-AGENT-044-c (probe missing / enqueue error surfaced) --------
    /**
     * TC-AGENT-044-c: when the probe returns `worker_registered=false` and a
     * `last_enqueue_error`, the response must surface both. Source-scan: the
     * branch reads the probe's `last_enqueue_error` key and emits it as
     * `enqueue_last_error` in the response — agent uses this to answer "why
     * isn't my cron working".
     */
    @Test
    fun `TC-AGENT-044-c health branch surfaces enqueue_last_error when probe reports failure`() {
        val funBody = extractFunBody("cronjob")
        val branch = extractWhenBranch(funBody, "health")

        // The probe contract uses `last_enqueue_error` (snake_case) as the
        // probe-side key and `enqueue_last_error` as the response key. Both
        // must appear in the branch.
        assertTrue(
            "TC-AGENT-044-c: health branch must read probe field `last_enqueue_error`",
            branch.contains("last_enqueue_error")
        )
        assertTrue(
            "TC-AGENT-044-c: health branch must emit `enqueue_last_error` in the response",
            branch.contains("enqueue_last_error")
        )
        // The branch must handle a null probe (worker_registered=false fallback).
        // Look for the literal "MISSING" or an explicit null-check on the probe.
        assertTrue(
            "TC-AGENT-044-c: health branch must handle null probe (fallback worker_state \"MISSING\" " +
                "or explicit `cronHealthProbe == null` / `?.let` / `?.invoke()` guard)",
            branch.contains("MISSING") ||
                Regex("""cronHealthProbe\s*\?\s*\.""").containsMatchIn(branch) ||
                Regex("""cronHealthProbe\s*==\s*null""").containsMatchIn(branch)
        )
    }

    // -------- TC-AGENT-044-d (overdue jobs report shape) --------
    /**
     * TC-AGENT-044-d: the health branch must produce `pending_due_jobs` from
     * `listJobs()`, filtering out paused jobs and future-scheduled jobs.
     * Source-scan asserts the structural shape: the branch invokes `listJobs(`,
     * references `next_run_at`, filters on `state` / `enabled`, and writes
     * `pending_due_jobs` and `recent_runs` to the response.
     */
    @Test
    fun `TC-AGENT-044-d health branch builds pending_due_jobs from listJobs filtered + sorted`() {
        val funBody = extractFunBody("cronjob")
        val branch = extractWhenBranch(funBody, "health")

        assertTrue(
            "TC-AGENT-044-d: health branch must call listJobs(",
            branch.contains("listJobs(")
        )
        assertTrue(
            "TC-AGENT-044-d: health branch must inspect `next_run_at` (the lateness field)",
            branch.contains("next_run_at")
        )
        // Must filter out paused jobs (state=="paused" or enabled==false).
        assertTrue(
            "TC-AGENT-044-d: health branch must filter out paused/disabled jobs " +
                "(reference `state` / `enabled` / `paused`)",
            Regex(""""state"|"enabled"|"paused"""").containsMatchIn(branch)
        )
        // Must produce the pending_due_jobs key.
        assertTrue(
            "TC-AGENT-044-d: health branch must emit `pending_due_jobs` in the response",
            branch.contains("pending_due_jobs")
        )
        // Must produce recent_runs (last_run_at-based history).
        assertTrue(
            "TC-AGENT-044-d: health branch must emit `recent_runs` (job-history slice)",
            branch.contains("recent_runs")
        )
    }

    // -------- TC-AGENT-044-e (immediate runner wiring reflected) --------
    /**
     * TC-AGENT-044-e: `immediate_runner_wired` must reflect whether
     * `cronImmediateRunner` is non-null at health-check time. Source-scan:
     * the branch references `cronImmediateRunner` and emits the key.
     */
    @Test
    fun `TC-AGENT-044-e health branch reflects cronImmediateRunner state`() {
        val funBody = extractFunBody("cronjob")
        val branch = extractWhenBranch(funBody, "health")

        assertTrue(
            "TC-AGENT-044-e: health branch must read cronImmediateRunner",
            branch.contains("cronImmediateRunner")
        )
        assertTrue(
            "TC-AGENT-044-e: health branch must emit `immediate_runner_wired`",
            branch.contains("immediate_runner_wired")
        )
        // The wired flag is a boolean — `!= null` or `?.let` / `!= null` check.
        assertTrue(
            "TC-AGENT-044-e: immediate_runner_wired must be derived as a non-null check on cronImmediateRunner",
            Regex("""cronImmediateRunner\s*!=\s*null""").containsMatchIn(branch) ||
                Regex("""cronImmediateRunner\s*\?\s*\.""").containsMatchIn(branch)
        )
        // Must NOT be hard-coded true.
        assertFalse(
            "TC-AGENT-044-e: immediate_runner_wired must NOT be hard-coded true (must reflect runtime state)",
            Regex(""""immediate_runner_wired"\s*to\s*true""").containsMatchIn(branch)
        )
    }

    private fun toolsPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/tools/CronjobTools.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/tools/CronjobTools.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate CronjobTools.kt — cwd=${File(".").absolutePath}")
    }
}
