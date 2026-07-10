package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-d (R-OBS-001): `CronExactAlarmReceiver.onReceive` must emit
 * trace lines to `CronFileLogger` at two points:
 *   - `alarm fired jobId=... action=...` on entry (confirms AlarmManager
 *     actually delivered; rules out "alarm got coalesced / never set up").
 *   - `alarm dispatched jobId=...` after `CronAgentRunner.run(...)` is
 *     launched (confirms we crossed into the dispatch scope; rules out
 *     "goAsync race / scope cancelled before launch").
 *
 * Source-scan only — BroadcastReceiver lifecycle is Android-bound.
 */
class CronExactAlarmReceiverObservabilityTest {

    private val source: String by lazy { File(receiverPath()).readText() }

    @Test
    fun `TC-OBS-001-d onReceive writes alarm trace to CronFileLogger`() {
        assertTrue(
            "TC-OBS-001-d: `CronExactAlarmReceiver.kt` must reference `CronFileLogger`.",
            source.contains("CronFileLogger")
        )

        val onReceive = extractFunctionBody("onReceive")
        assertTrue(
            "TC-OBS-001-d: `onReceive()` must contain literal `alarm fired` " +
                "(entry marker — proves AlarmManager actually delivered).\n" +
                "Actual onReceive body head:\n${onReceive.take(2000)}",
            onReceive.contains("alarm fired")
        )
        assertTrue(
            "TC-OBS-001-d: `onReceive()` must contain literal `alarm dispatched` " +
                "(post-launch marker — proves dispatchScope.launch ran).",
            onReceive.contains("alarm dispatched")
        )
    }

    private fun extractFunctionBody(funName: String): String {
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

    private fun receiverPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronExactAlarmReceiver.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        return File("app/src/main/java/com/ai/assistance/operit/core/cron/CronExactAlarmReceiver.kt").path
    }
}
