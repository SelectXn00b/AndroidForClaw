package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-a (R-OBS-001): `CronFileLogger` singleton must exist as
 * `app/src/main/java/com/ai/assistance/operit/hermes/gateway/CronFileLogger.kt`,
 * mirror `GatewayFileLogger` pattern, and write to
 * `/sdcard/Download/Hermes/cron_logs/cron.log`.
 *
 * Source-scan only — file logger touches Android `File` + `FileWriter` with
 * `OperitPaths.operitRootDir()` which is Robolectric-required; literal
 * contract is enough to lock the wiring.
 */
class CronFileLoggerWiringTest {

    private val source: String by lazy { File(loggerPath()).readText() }

    @Test
    fun `TC-OBS-001-a CronFileLogger singleton exists with correct path`() {
        assertTrue(
            "TC-OBS-001-a: file `CronFileLogger.kt` must exist at " +
                "app/src/main/java/com/ai/assistance/operit/hermes/gateway/",
            File(loggerPath()).exists()
        )
        assertTrue(
            "TC-OBS-001-a: must declare `object CronFileLogger` singleton " +
                "(same pattern as GatewayFileLogger).\nActual head:\n${source.take(400)}",
            Regex("""\bobject\s+CronFileLogger\b""").containsMatchIn(source)
        )
        assertTrue(
            "TC-OBS-001-a: must reference `OperitPaths` (path root resolution).",
            source.contains("OperitPaths")
        )
        assertTrue(
            "TC-OBS-001-a: must contain literal `cron_logs` (subdir).",
            source.contains("cron_logs")
        )
        assertTrue(
            "TC-OBS-001-a: must contain literal `cron.log` (filename).",
            source.contains("cron.log")
        )
        // Must expose 4-level API matching GatewayFileLogger
        for (m in listOf("fun i(", "fun w(", "fun e(", "fun d(")) {
            assertTrue(
                "TC-OBS-001-a: must expose `$m...)` method.",
                source.contains(m)
            )
        }
        assertTrue(
            "TC-OBS-001-a: must expose `getLogFilePath()` for UI display.",
            Regex("""fun\s+getLogFilePath\s*\(""").containsMatchIn(source)
        )
    }

    private fun loggerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/CronFileLogger.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/CronFileLogger.kt")
        return alt.path
    }
}
