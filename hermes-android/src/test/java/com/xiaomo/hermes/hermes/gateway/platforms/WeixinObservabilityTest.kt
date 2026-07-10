package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-OBS-001-f (R-OBS-001):
 *
 * `WeixinAdapter` must expose a `PlatformDiagSink` injection point and emit
 * diagnostic events at three critical points where the OEM-ROM-kill bug
 * shows itself:
 *
 *   - `connect()` — `connect ok account=...` / `connect FAIL reason=...`
 *     so we know whether the iLink handshake actually succeeded after a
 *     warmup-triggered service restart.
 *   - `send()` — `send IN chat=... hadToken=...` + `send OUT errcode=...
 *     errmsg=...` so we see exactly whether the send was attempted with a
 *     persisted context_token, and what errcode came back.
 *   - `_runPollLoop()` — `poll loop ended` on exit so we see whether the
 *     long poll died (the most common "service was killed in background"
 *     symptom).
 *
 * The sink is an `interface PlatformDiagSink` declared in this module
 * (hermes-android cannot reverse-depend on app/'s file loggers) and
 * injected by `HermesGatewayController` from the app side (see
 * TC-OBS-001-g).
 *
 * Source-scan only — Weixin transport is OkHttp + coroutines + iLink
 * SDK shape, can't run pure JVM without heavy stubbing.
 */
class WeixinObservabilityTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    @Test
    fun `TC-OBS-001-f PlatformDiagSink interface declared`() {
        assertTrue(
            "TC-OBS-001-f: `Weixin.kt` must declare or reference `PlatformDiagSink` " +
                "(the injectable interface bridging adapter diag calls into app-side " +
                "file loggers without reverse-depending on app/).",
            source.contains("PlatformDiagSink")
        )
        // Either declared here, or in a sibling file referenced here. Prefer
        // declaration in the same module path; allow either form.
        val declaredHere = Regex("""interface\s+PlatformDiagSink\b""").containsMatchIn(source)
        val siblingDeclared = File(siblingPlatformDiagSinkPath()).exists()
        assertTrue(
            "TC-OBS-001-f: `PlatformDiagSink` must be declared either in `Weixin.kt` " +
                "(`interface PlatformDiagSink {...}`) or as a sibling file " +
                "`PlatformDiagSink.kt` under the same `platforms/` package.",
            declaredHere || siblingDeclared
        )
    }

    @Test
    fun `TC-OBS-001-f WeixinAdapter holds a diag sink and uses it`() {
        val adapterBody = extractClassBody("WeixinAdapter")
        // Must have a sink field (nullable / optional injection).
        val hasSinkField = Regex("""(_diagSink|diagSink)\s*:\s*PlatformDiagSink\??""")
            .containsMatchIn(adapterBody)
        assertTrue(
            "TC-OBS-001-f: `WeixinAdapter` must declare a `PlatformDiagSink?`-typed field " +
                "(default null; injected by HermesGatewayController).\n" +
                "Actual adapter body head:\n${adapterBody.take(2000)}",
            hasSinkField
        )

        // Must invoke sink at least once in the class body (i/w/e/d).
        val sinkCall = Regex("""(_diagSink|diagSink)\s*\?\.\s*[iwed]\s*\(""")
        assertTrue(
            "TC-OBS-001-f: `WeixinAdapter` must call the sink (`_diagSink?.i(...)` / `.w(...)` " +
                "/ `.e(...)` / `.d(...)`) at least once.",
            sinkCall.containsMatchIn(adapterBody)
        )
    }

    @Test
    fun `TC-OBS-001-f connect emits ok and FAIL diag markers`() {
        val connectBody = extractFunctionBody("connect", isOverrideSuspend = true)
        val hasOk = connectBody.contains("connect ok")
        val hasFail = connectBody.contains("connect FAIL")
        assertTrue(
            "TC-OBS-001-f: `connect()` must emit either `connect ok` (success path) " +
                "or `connect FAIL` (failure path) — at least one.\n" +
                "Actual connect body head:\n${connectBody.take(2000)}",
            hasOk || hasFail
        )
        // The full contract wants both paths instrumented; harden:
        assertTrue(
            "TC-OBS-001-f: `connect()` must contain `connect ok` marker for the success branch.",
            hasOk
        )
        assertTrue(
            "TC-OBS-001-f: `connect()` must contain `connect FAIL` marker for the failure branch " +
                "(silent connection failures are the #1 OEM-ROM-kill symptom and must be visible).",
            hasFail
        )
    }

    @Test
    fun `TC-OBS-001-f send emits IN and OUT diag markers`() {
        val sendBody = extractFunctionBody("send", isOverrideSuspend = true)
        assertTrue(
            "TC-OBS-001-f: `send()` must contain `send IN` marker (entry — captures hadToken).\n" +
                "Actual send body head:\n${sendBody.take(2000)}",
            sendBody.contains("send IN")
        )
        assertTrue(
            "TC-OBS-001-f: `send()` must contain `send OUT` marker (exit — captures errcode/errmsg).",
            sendBody.contains("send OUT")
        )
    }

    @Test
    fun `TC-OBS-001-f poll loop emits ended marker`() {
        val pollBody = extractFunctionBody("_runPollLoop")
        assertTrue(
            "TC-OBS-001-f: `_runPollLoop()` must contain `poll loop ended` marker " +
                "(the #1 evidence for `service was killed in background`).\n" +
                "Actual _runPollLoop body head:\n${pollBody.take(2000)}",
            pollBody.contains("poll loop ended")
        )
    }

    // ----- helpers -----

    private fun extractClassBody(className: String): String {
        val regex = Regex("""class\s+$className\b[^{]*\{""")
        val match = regex.find(source) ?: error("class $className not found")
        return extractBraceBody(match.range.last)
    }

    private fun extractFunctionBody(funName: String, isOverrideSuspend: Boolean = false): String {
        val pattern = if (isOverrideSuspend) {
            """override\s+suspend\s+fun\s+$funName\s*\("""
        } else {
            """\bfun\s+$funName\s*\("""
        }
        val regex = Regex(pattern)
        val match = regex.find(source) ?: error(
            "function $funName not found (isOverrideSuspend=$isOverrideSuspend)"
        )
        val openBrace = source.indexOf('{', startIndex = match.range.last)
        if (openBrace < 0) error("function $funName has no body brace")
        return extractBraceBody(openBrace)
    }

    private fun extractBraceBody(openBracePos: Int): String {
        require(source[openBracePos] == '{') { "expected '{' at pos $openBracePos" }
        var depth = 0
        var i = openBracePos
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(openBracePos, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces starting at $openBracePos")
    }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android/src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun weixinPath(): String =
        File(appSrcMainRoot(), "hermes/gateway/platforms/Weixin.kt").path

    private fun siblingPlatformDiagSinkPath(): String =
        File(appSrcMainRoot(), "hermes/gateway/platforms/PlatformDiagSink.kt").path
}
