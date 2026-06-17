package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-043: `cronjob(action="run" | "run_now" | "trigger")` must:
 *   - bump `next_run_at` to now via `triggerJob` (cron parity)
 *   - hand the resolved job map to the injected `cronImmediateRunner`
 *     using a fire-and-forget `_immediateTriggerScope.launch` so the
 *     agent's tool call returns immediately
 *   - return JSON containing `triggered_immediately` (boolean reflecting
 *     whether the runner was wired at the time of the call)
 *   - tolerate a null runner (fallback path) without throwing
 *
 * Source-scan: the file-backed Jobs CRUD goes through getHermesHome() →
 * Android Context.filesDir, which is not available without Robolectric.
 * The structural shape captured here is sufficient to defend the contract.
 */
class CronjobToolsImmediateTriggerTest {

    private val source: String by lazy { File(toolsPath()).readText() }

    /** Brace-walked body of the `cronjob(...)` top-level function. */
    private fun extractCronjobBody(): String {
        val anchor = Regex("""\bfun\s+cronjob\s*\(""").find(source)?.range?.first
            ?: error("Cannot find fun cronjob( in CronjobTools.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find cronjob() opening brace" }
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

    /** Slice out only the run / run_now / trigger when-branch body. */
    private fun extractRunBranchBody(): String {
        val body = extractCronjobBody()
        val anchor = Regex(""""run"\s*,\s*"run_now"\s*,\s*"trigger"\s*->\s*\{""").find(body)?.range?.last
            ?: error("Cannot find \"run\", \"run_now\", \"trigger\" branch in cronjob()")
        var i = anchor // points at the `{`
        val start = i
        var depth = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return body.substring(start, i + 1)
            }
            i++
        }
        return body.substring(start)
    }

    // -------- TC-AGENT-043-b --------
    /**
     * TC-AGENT-043-b: run/run_now/trigger branch must (i) reference the
     * injected `cronImmediateRunner`, (ii) launch on a module-level
     * `_immediateTriggerScope`, (iii) emit `triggered_immediately` in the
     * JSON response.
     */
    @Test
    fun `TC-AGENT-043-b run branch invokes runner via scope`() {
        val branch = extractRunBranchBody()

        // (i) Branch references the injection slot.
        assertTrue(
            "TC-AGENT-043-b: run branch must reference cronImmediateRunner",
            branch.contains("cronImmediateRunner")
        )

        // (ii) Hand-off uses _immediateTriggerScope.launch (non-blocking,
        //      isolated coroutine scope so runner exceptions don't bubble
        //      back into the agent's tool-call return path).
        assertTrue(
            "TC-AGENT-043-b: run branch must use _immediateTriggerScope.launch",
            Regex("""_immediateTriggerScope\s*\.\s*launch""").containsMatchIn(branch)
        )

        // (iii) Response JSON must surface whether immediate trigger fired,
        //       so callers (agent / future UI clients) can distinguish from
        //       the fallback "queued for next tick" path.
        assertTrue(
            "TC-AGENT-043-b: run branch must include `triggered_immediately` JSON key",
            branch.contains("triggered_immediately")
        )

        // The pre-existing triggerJob call (cron-parity bump of next_run_at)
        // must remain — the immediate path is additive, not a replacement.
        assertTrue(
            "TC-AGENT-043-b: run branch must still call triggerJob(jobId)",
            Regex("""triggerJob\s*\(\s*jobId\s*\)""").containsMatchIn(branch)
        )
    }

    // -------- TC-AGENT-043-b (module-level scope shape) --------
    /**
     * Defense: the module-level `_immediateTriggerScope` must use a
     * `SupervisorJob` (so a failed runner doesn't poison subsequent
     * triggers) and `Dispatchers.IO` (so we don't pin the main thread
     * with a long-running agent invocation).
     */
    @Test
    fun `TC-AGENT-043-b immediateTriggerScope is supervisor IO scope`() {
        // Look for a private val _immediateTriggerScope at module / file level
        // (CronjobTools.kt has top-level helpers + private vals, no enclosing class).
        val matched = Regex(
            """private\s+val\s+_immediateTriggerScope\s*=\s*CoroutineScope\s*\(\s*SupervisorJob\s*\(\s*\)\s*\+\s*Dispatchers\s*\.\s*IO\s*\)"""
        ).containsMatchIn(source)
        assertTrue(
            "TC-AGENT-043-b: missing module-level _immediateTriggerScope = " +
                "CoroutineScope(SupervisorJob() + Dispatchers.IO)",
            matched
        )
    }

    // -------- TC-AGENT-043-e (structural fallback shape) --------
    /**
     * TC-AGENT-043-e: when `cronImmediateRunner` is null, the branch must
     * NOT call `runner(updated)` — the source-level shape must read the
     * field into a local and only invoke the lambda when non-null.
     * Source-scan because driving the null-runner path needs Robolectric.
     */
    @Test
    fun `TC-AGENT-043-e null runner branch does not unconditionally invoke`() {
        val branch = extractRunBranchBody()

        // Pattern: `val runner = cronImmediateRunner` capture exists.
        assertTrue(
            "TC-AGENT-043-e: branch must snapshot cronImmediateRunner into a local",
            Regex("""val\s+runner\s*=\s*cronImmediateRunner""").containsMatchIn(branch)
        )

        // Pattern: invocation is gated by `if (runner != null)` (or equivalent
        // safe call). Plain `cronImmediateRunner!!.invoke(...)` would be a bug.
        val gated = Regex("""if\s*\(\s*runner\s*!=\s*null\s*\)""").containsMatchIn(branch) ||
            Regex("""runner\s*\?\.""").containsMatchIn(branch)
        assertTrue(
            "TC-AGENT-043-e: runner invocation must be null-guarded",
            gated
        )

        // Anti-pattern: never use `!!` to force-unwrap the runner — that's
        // exactly the failure mode this TC defends against.
        assertFalse(
            "TC-AGENT-043-e: branch must not force-unwrap cronImmediateRunner with !!",
            Regex("""cronImmediateRunner\s*!!""").containsMatchIn(branch)
        )
    }

    // -------- TC-AGENT-043-f (exception isolation shape) --------
    /**
     * TC-AGENT-043-f: runner exceptions must not bubble up to the agent
     * tool-call return path. Structurally, this is enforced by:
     *   - launching on `_immediateTriggerScope` (SupervisorJob isolation)
     *   - the runner is INSIDE the launch block, NOT at the call site
     * Both shapes are checked here (source-scan).
     */
    @Test
    fun `TC-AGENT-043-f runner invocation lives inside launch block`() {
        val branch = extractRunBranchBody()

        // The `runner(updated)` call must be physically nested inside a
        // `_immediateTriggerScope.launch { ... }` block. We approximate by
        // checking that the `launch {` opening brace appears before the
        // runner invocation, both within this branch.
        val launchIdx = Regex("""_immediateTriggerScope\s*\.\s*launch\s*\{""").find(branch)?.range?.last ?: -1
        val invokeIdx = Regex("""runner\s*\(\s*updated\s*\)""").find(branch)?.range?.first ?: -1
        assertTrue(
            "TC-AGENT-043-f: runner(updated) must appear AFTER `_immediateTriggerScope.launch {` " +
                "in the same branch (so a thrown exception is caught by the SupervisorJob)",
            launchIdx >= 0 && invokeIdx > launchIdx
        )

        // Anti-pattern guard: the runner must not be invoked at the synchronous
        // call site (which would let exceptions propagate to the tool caller).
        // We detect "runner(updated)" outside the launch block by re-checking
        // the substring before the launch position.
        val preLaunch = if (launchIdx >= 0) branch.substring(0, launchIdx) else branch
        assertFalse(
            "TC-AGENT-043-f: runner(updated) must not appear before the launch block",
            Regex("""runner\s*\(\s*updated\s*\)""").containsMatchIn(preLaunch)
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
