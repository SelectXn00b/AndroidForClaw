package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031 bugfix (2026-06-18): CronTickWorker.enqueue must:
 *  - use ExistingPeriodicWorkPolicy.UPDATE (not KEEP), so a broken unique
 *    work record left over from a previous install/crash is replaced.
 *  - re-throw on failure (not swallow), so OperitApplication.onCreate can
 *    surface the problem instead of leaving cron silently un-registered.
 *
 * Source-scan because exercising the WorkManager enqueue contract requires
 * Robolectric + a real Application context.
 */
class CronTickWorkerEnqueueWiringTest {

    private val source: String by lazy { File(workerPath()).readText() }

    /** Brace-walked body of the `enqueue(...)` companion function. */
    private fun extractEnqueueBody(): String {
        val anchor = Regex("""\bfun\s+enqueue\s*\(""").find(source)?.range?.first
            ?: error("Cannot find fun enqueue( in CronTickWorker.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find enqueue() opening brace" }
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

    /**
     * TC-AGENT-031-o: enqueue must use UPDATE so a stale / broken unique
     * work record from a prior install gets replaced rather than preserved
     * by KEEP semantics.
     */
    @Test
    fun `TC-AGENT-031-o enqueue uses UPDATE policy`() {
        val body = extractEnqueueBody()
        assertTrue(
            "TC-AGENT-031-o: enqueue must use ExistingPeriodicWorkPolicy.UPDATE " +
                "(not KEEP) so broken state from a prior install is replaced",
            body.contains("ExistingPeriodicWorkPolicy.UPDATE")
        )
        assertFalse(
            "TC-AGENT-031-o: enqueue must NOT use KEEP " +
                "(KEEP can preserve a permanently-broken unique work record)",
            body.contains("ExistingPeriodicWorkPolicy.KEEP")
        )
    }

    /**
     * TC-AGENT-031-p: enqueue must re-throw on failure so the caller
     * (OperitApplication.onCreate) can detect that cron is dead. The
     * previous shape (`catch (e) { AppLogger.e(...) }` with no throw)
     * silently swallowed enqueue failures.
     */
    @Test
    fun `TC-AGENT-031-p enqueue re-throws on failure`() {
        val body = extractEnqueueBody()
        // The catch block must contain a `throw` statement (re-throw the
        // captured exception or wrap-and-throw). Plain log-only is the
        // exact bug this TC defends against.
        val catchBlock = Regex("""catch\s*\([^)]*\)\s*\{([\s\S]*?)\}""")
            .find(body)?.groupValues?.getOrNull(1)
            ?: error("TC-AGENT-031-p: enqueue must wrap the WorkManager call in try/catch")
        assertTrue(
            "TC-AGENT-031-p: enqueue catch block must re-throw " +
                "(currently it only logs and silently returns; cron failures invisible)",
            Regex("""\bthrow\b""").containsMatchIn(catchBlock)
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun workerPath(): String =
        File(appSrcMainRoot(), "core/cron/CronTickWorker.kt").path
}
