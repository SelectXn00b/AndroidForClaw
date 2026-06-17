package com.ai.assistance.operit.core.application

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-043: `OperitApplication.onCreate` must inject the
 * `Scheduler.cronImmediateRunner` lambda so the agent's
 * `cronjob(action="run")` and the sidebar Cron Jobs screen can fire jobs
 * in-process, bypassing WorkManager's 15-minute periodic tick.
 *
 * Source-scan because driving Application.onCreate would require booting
 * the entire app under Robolectric — overkill for an injection-shape
 * defense. The structural facts checked here:
 *   - the assignment `cronImmediateRunner = ` exists
 *   - the lambda body invokes `CronAgentRunner.run(applicationContext, ...)`
 *   - the assignment lives AFTER `CronTickWorker.enqueue(this)` (so the
 *     periodic worker is registered before the immediate path becomes
 *     wirable; defends against a startup race where a cron tool fires
 *     before the worker is enqueued)
 */
class OperitApplicationCronInjectionTest {

    private val source: String by lazy { File(applicationPath()).readText() }

    // -------- TC-AGENT-043-c --------
    @Test
    fun `TC-AGENT-043-c immediate runner injected after worker enqueue`() {
        // (1) Injection assignment exists.
        val injectIdx = Regex(
            """cronImmediateRunner\s*=\s*\{"""
        ).find(source)?.range?.first ?: -1
        assertTrue(
            "TC-AGENT-043-c: missing `cronImmediateRunner = { ... }` assignment in OperitApplication",
            injectIdx >= 0
        )

        // (2) Lambda body delegates to CronAgentRunner.run with applicationContext.
        // Locate the lambda body (a few hundred chars after the `{` is enough).
        val lambdaSlice = source.substring(injectIdx, (injectIdx + 600).coerceAtMost(source.length))
        assertTrue(
            "TC-AGENT-043-c: cronImmediateRunner lambda must call CronAgentRunner.run(applicationContext, ...)",
            Regex(
                """CronAgentRunner\s*\.\s*run\s*\(\s*applicationContext"""
            ).containsMatchIn(lambdaSlice)
        )

        // (3) Assignment lives AFTER `CronTickWorker.enqueue(this)`.
        // Ordering matters: the worker must be enqueued first so the periodic
        // tick is alive even if no agent ever invokes the immediate path.
        val workerIdx = Regex("""CronTickWorker\s*\.\s*enqueue\s*\(\s*this\s*\)""").find(source)?.range?.first ?: -1
        assertTrue(
            "TC-AGENT-043-c: CronTickWorker.enqueue(this) must appear in onCreate (R-AGENT-031 baseline)",
            workerIdx >= 0
        )
        assertTrue(
            "TC-AGENT-043-c: cronImmediateRunner injection must come AFTER CronTickWorker.enqueue(this) " +
                "(workerIdx=$workerIdx, injectIdx=$injectIdx)",
            injectIdx > workerIdx
        )
    }

    private fun applicationPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate OperitApplication.kt — cwd=${File(".").absolutePath}")
    }
}
