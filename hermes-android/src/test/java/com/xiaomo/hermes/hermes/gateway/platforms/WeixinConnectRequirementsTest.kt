package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix-1 (TC-GW-113-a): `connect()` must call
 * `checkWeixinRequirements()` and bail with a fatal-error log when it
 * returns false, mirroring Python `weixin.py:1183-1187`.
 *
 * Why this matters even though the Android `checkWeixinRequirements()`
 * implementation hard-codes `true`: structural alignment with the
 * upstream lets a future runtime gate (e.g. a vendor build that drops
 * `javax.crypto`) short-circuit the connect path the same way Python
 * does, instead of failing deeper inside the long-poll loop.
 *
 * Source-scan because mocking the global `checkWeixinRequirements()`
 * requires extension-function shimming or a non-trivial test harness.
 */
class WeixinConnectRequirementsTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    /** Brace-walked body of `override suspend fun connect()`. */
    private fun extractConnectBody(): String {
        val anchor = Regex("""override\s+suspend\s+fun\s+connect\s*\(""").find(source)?.range?.first
            ?: error("Cannot find override suspend fun connect() in Weixin.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find connect() opening brace" }
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

    @Test
    fun `TC-GW-113-a connect rejects when requirements fail`() {
        val body = extractConnectBody()

        // The `checkWeixinRequirements()` call must appear inside the
        // connect body — and importantly, before the existing
        // `_accountId.isEmpty()` guard, so a missing-deps environment
        // bails with the more specific error.
        val checkIdx = Regex("""checkWeixinRequirements\s*\(\s*\)""").find(body)?.range?.first ?: -1
        assertTrue(
            "TC-GW-113-a: connect() body must call checkWeixinRequirements()",
            checkIdx >= 0,
        )

        // The branch must `return false` when the call evaluates to false —
        // we accept either `if (!checkWeixinRequirements()) { ... return false }`
        // or `if (checkWeixinRequirements().not()) ...`.
        assertTrue(
            "TC-GW-113-a: connect() must `return false` in the requirements-failed branch",
            Regex("""!checkWeixinRequirements\s*\(\s*\)|checkWeixinRequirements\s*\(\s*\)\s*\.not\s*\(\s*\)""")
                .containsMatchIn(body),
        )

        // The window from the check site to ~400 chars after must contain
        // a `return false` so the structural intent is unambiguous.
        val window = body.substring(
            checkIdx,
            (checkIdx + 400).coerceAtMost(body.length),
        )
        assertTrue(
            "TC-GW-113-a: requirements-failed branch must `return false` within the same conditional block",
            Regex("""return\s+false""").containsMatchIn(window),
        )
    }

    private fun weixinPath(): String {
        val candidates = listOf(
            File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
            File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate Weixin.kt — cwd=${File(".").absolutePath}")
    }
}
