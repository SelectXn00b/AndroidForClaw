package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-b (R-OBS-001): `WeixinFileLogger` singleton must exist at
 * `app/src/main/java/com/ai/assistance/operit/hermes/gateway/WeixinFileLogger.kt`
 * and write to `/sdcard/Download/Hermes/cron_logs/weixin.log` (sibling of
 * `cron.log` — both under `cron_logs/` since they're observability for the
 * same chain, segregated by emitter).
 */
class WeixinFileLoggerWiringTest {

    private val source: String by lazy { File(loggerPath()).readText() }

    @Test
    fun `TC-OBS-001-b WeixinFileLogger singleton exists with correct path`() {
        assertTrue(
            "TC-OBS-001-b: file `WeixinFileLogger.kt` must exist.",
            File(loggerPath()).exists()
        )
        assertTrue(
            "TC-OBS-001-b: must declare `object WeixinFileLogger`.\nActual head:\n${source.take(400)}",
            Regex("""\bobject\s+WeixinFileLogger\b""").containsMatchIn(source)
        )
        assertTrue(
            "TC-OBS-001-b: must reference `OperitPaths`.",
            source.contains("OperitPaths")
        )
        assertTrue(
            "TC-OBS-001-b: must contain literal `cron_logs` (subdir, shared with cron.log).",
            source.contains("cron_logs")
        )
        assertTrue(
            "TC-OBS-001-b: must contain literal `weixin.log` (filename).",
            source.contains("weixin.log")
        )
        for (m in listOf("fun i(", "fun w(", "fun e(", "fun d(")) {
            assertTrue(
                "TC-OBS-001-b: must expose `$m...)` method.",
                source.contains(m)
            )
        }
        assertTrue(
            "TC-OBS-001-b: must expose `getLogFilePath()`.",
            Regex("""fun\s+getLogFilePath\s*\(""").containsMatchIn(source)
        )
    }

    private fun loggerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/WeixinFileLogger.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/WeixinFileLogger.kt")
        return alt.path
    }
}
