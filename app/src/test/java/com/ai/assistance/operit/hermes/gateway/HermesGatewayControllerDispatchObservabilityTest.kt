package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-e + TC-OBS-001-g (R-OBS-001):
 *
 * e) `HermesGatewayController.dispatchOutgoing` must emit `dispatchOutgoing IN`
 *    on entry and `dispatchOutgoing OUT success=` on exit to `CronFileLogger`.
 *    This is the single layer-3 chokepoint where cron output crosses from
 *    Kotlin agent loop into IM adapter — instrumenting it gives precise
 *    "did we even call adapter.send" evidence.
 *
 * g) Controller must wire `WeixinFileLogger` into the `WeixinAdapter`'s
 *    `PlatformDiagSink` so hermes-android can write diagnostic logs to
 *    `weixin.log` without reverse-depending on `app/` (the layering red
 *    line). Controller is the natural place — it's already the cross-
 *    module instantiation point for adapters.
 */
class HermesGatewayControllerDispatchObservabilityTest {

    private val source: String by lazy { File(controllerPath()).readText() }

    @Test
    fun `TC-OBS-001-e dispatchOutgoing writes IN and OUT trace to CronFileLogger`() {
        val dispatchBody = extractFunctionBody("dispatchOutgoing")
        assertTrue(
            "TC-OBS-001-e: `dispatchOutgoing()` must reference `CronFileLogger` " +
                "(observability sink separate from GatewayFileLogger so cron-chain logs " +
                "are isolated from AI-dialog logs).\nActual body head:\n${dispatchBody.take(2000)}",
            dispatchBody.contains("CronFileLogger")
        )
        assertTrue(
            "TC-OBS-001-e: `dispatchOutgoing()` must contain literal `dispatchOutgoing IN platform=` " +
                "(entry marker).",
            dispatchBody.contains("dispatchOutgoing IN platform=")
        )
        assertTrue(
            "TC-OBS-001-e: `dispatchOutgoing()` must contain literal `dispatchOutgoing OUT success=` " +
                "(exit marker, captures result.success boolean).",
            dispatchBody.contains("dispatchOutgoing OUT success=")
        )
    }

    @Test
    fun `TC-OBS-001-g controller wires WeixinFileLogger into adapter via PlatformDiagSink`() {
        assertTrue(
            "TC-OBS-001-g: `HermesGatewayController.kt` must reference `WeixinFileLogger` " +
                "(app-side sink for weixin.log).",
            source.contains("WeixinFileLogger")
        )
        assertTrue(
            "TC-OBS-001-g: `HermesGatewayController.kt` must reference `PlatformDiagSink` " +
                "(the hermes-android-side interface bridging adapter diag calls into app loggers).",
            source.contains("PlatformDiagSink")
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

    private fun controllerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        return File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt").path
    }
}
