package com.xiaomo.hermes.hermes.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-043: `Scheduler.kt` must declare a `cronImmediateRunner` injection
 * slot that mirrors the `cronOutboundDispatcher` pattern from R-AGENT-033.
 * The slot lets the `app` module hand a fire-on-demand runner to this
 * `hermes-android` module without violating the single-direction module
 * dependency.
 *
 * Source-scan because exercising the runtime injection would require
 * spinning up a real `CronAgentRunner` + Application context.
 */
class SchedulerImmediateRunnerTest {

    private val source: String by lazy { File(schedulerPath()).readText() }

    // -------- TC-AGENT-043-a --------
    /**
     * TC-AGENT-043-a: `cronImmediateRunner` field declared at module level
     * with the correct closure signature, `@Volatile`, and default null.
     */
    @Test
    fun `TC-AGENT-043-a cronImmediateRunner field declared`() {
        // Must be marked @Volatile (publishes assignment across threads since
        // app module sets it on cold start while CronjobTools may read on
        // any agent dispatch).
        assertTrue(
            "TC-AGENT-043-a: missing @Volatile on cronImmediateRunner",
            Regex(
                """@Volatile\s*\n\s*var\s+cronImmediateRunner""",
                RegexOption.MULTILINE
            ).containsMatchIn(source)
        )

        // Must declare the suspend lambda type Map<String, Any?> -> Unit.
        assertTrue(
            "TC-AGENT-043-a: cronImmediateRunner signature must be `(suspend (job: Map<String, Any?>) -> Unit)?`",
            Regex(
                """var\s+cronImmediateRunner\s*:\s*\(suspend\s*\(\s*job\s*:\s*Map\s*<\s*String\s*,\s*Any\?\s*>\s*\)\s*->\s*Unit\s*\)\?"""
            ).containsMatchIn(source)
        )

        // Must default to null (so the immediate path is opt-in via app
        // module's onCreate injection — not silently active in unit tests).
        // The declared type contains nested parens — `(suspend (...) -> Unit)?`
        // — so we anchor on `?\s*=\s*null` after the closing of the lambda type
        // rather than trying to balance parens with a flat regex.
        assertTrue(
            "TC-AGENT-043-a: cronImmediateRunner must default to null",
            Regex(
                """var\s+cronImmediateRunner\s*:[\s\S]*?\)\s*->\s*Unit\s*\)\?\s*=\s*null"""
            ).containsMatchIn(source)
        )
    }

    private fun schedulerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/cron/Scheduler.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/cron/Scheduler.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Scheduler.kt — cwd=${File(".").absolutePath}")
    }
}
