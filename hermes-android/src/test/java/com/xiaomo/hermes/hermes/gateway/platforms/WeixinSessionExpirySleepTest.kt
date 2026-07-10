package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix-1 (TC-GW-111-a): on session-expired errcode the
 * long-poll loop must pause for 10 minutes, mirroring Python
 * `weixin.py:1258` (`asyncio.sleep(600)`). The Kotlin port previously
 * paused for 5 minutes, halving the upstream cool-down window.
 *
 * Source-scan because driving the loop end-to-end requires a live iLink
 * server + AES-encrypted long-poll buffer + multi-second waits.
 */
class WeixinSessionExpirySleepTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    @Test
    fun `TC-GW-111-a session expired sleeps 10 min`() {
        // Locate the SESSION_EXPIRED branch — it sits inside the long-poll
        // loop, identified by the `isSessionExpired` flag check + a `delay(...)`
        // in the immediate body.
        val branchIdx = source.indexOf("isSessionExpired")
        assertTrue(
            "TC-GW-111-a: must contain `isSessionExpired` branch in long-poll loop",
            branchIdx >= 0,
        )
        // Capture a window around the branch (~600 chars) and assert the
        // delay is 10 minutes (600_000ms or 10 * 60_000L), not 5 minutes.
        val window = source.substring(
            branchIdx,
            (branchIdx + 600).coerceAtMost(source.length),
        )
        assertTrue(
            "TC-GW-111-a: session-expired branch must `delay(10 * 60_000L)` " +
                "(or 600_000L) — Python upstream `weixin.py:1258` sleeps 600s",
            Regex("""delay\s*\(\s*(?:10\s*\*\s*60[_]?000L|600[_]?000L)\s*\)""").containsMatchIn(window),
        )
        assertFalse(
            "TC-GW-111-a: session-expired branch must NOT `delay(5 * 60_000L)` " +
                "(legacy 5-minute pause is the bug being fixed)",
            Regex("""delay\s*\(\s*5\s*\*\s*60[_]?000L\s*\)""").containsMatchIn(window),
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
