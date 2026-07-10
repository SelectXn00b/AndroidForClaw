package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-c (R-OBS-001): `CronAgentRunner.run` + `deliver` must emit
 * structured trace lines to `CronFileLogger`, so the user can `cat
 * /sdcard/Download/Hermes/cron_logs/cron.log` to see every cron tick's
 * lifecycle without `logcat` / root.
 *
 * Source-scan only — `CronAgentRunner` is suspend + Context-bound; pure
 * JVM units would need extensive mocking. The literal-contract test is
 * the same pattern used by `CronGatewayWarmupTest` and is cheap to keep
 * green across refactors.
 */
class CronAgentRunnerObservabilityTest {

    private val source: String by lazy { File(runnerPath()).readText() }

    @Test
    fun `TC-OBS-001-c run and deliver write structured trace to CronFileLogger`() {
        // 1. file references CronFileLogger
        assertTrue(
            "TC-OBS-001-c: `CronAgentRunner.kt` must reference `CronFileLogger` " +
                "(the cron dispatch chain's diagnostic sink).",
            source.contains("CronFileLogger")
        )

        // 2. run() body must contain start / done markers
        val runBody = extractFunctionBody("run")
        assertTrue(
            "TC-OBS-001-c: `run()` must contain literal `agent run start` (entry marker).\n" +
                "Actual run() body head:\n${runBody.take(2000)}",
            runBody.contains("agent run start")
        )
        assertTrue(
            "TC-OBS-001-c: `run()` must contain literal `agent run done` (exit marker).",
            runBody.contains("agent run done")
        )

        // 3. deliver() body must contain mode + result markers
        val deliverBody = extractFunctionBody("deliver")
        assertTrue(
            "TC-OBS-001-c: `deliver()` must contain literal `deliver mode=` " +
                "(branch decision marker).\nActual deliver() body head:\n${deliverBody.take(2000)}",
            deliverBody.contains("deliver mode=")
        )
        assertTrue(
            "TC-OBS-001-c: `deliver()` must contain literal `deliver SUCCESS` (success path marker).",
            deliverBody.contains("deliver SUCCESS")
        )
        assertTrue(
            "TC-OBS-001-c: `deliver()` must contain literal `deliver FAIL` (failure path marker).",
            deliverBody.contains("deliver FAIL")
        )
    }

    // ---- helpers ----

    private fun extractFunctionBody(funName: String): String {
        // Match either `suspend fun foo(` or `private suspend fun foo(` etc.
        val regex = Regex("""\bfun\s+$funName\s*\(""")
        val match = regex.find(source) ?: error("function $funName not found")
        val openBrace = source.indexOf('{', startIndex = match.range.last)
        if (openBrace < 0) error("function $funName has no body brace")
        var depth = 0
        var i = openBrace
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBrace, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces in $funName")
    }

    private fun runnerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        return File("app/src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt").path
    }
}
