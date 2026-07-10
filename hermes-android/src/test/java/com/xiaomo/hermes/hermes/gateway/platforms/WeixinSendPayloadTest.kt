package com.xiaomo.hermes.hermes.gateway.platforms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix-1 (TC-GW-114-a): the outbound `send(...)` payload
 * must include `from_user_id=""` in the `msg` JSON, mirroring Python
 * `weixin.py:432`. The iLink server tolerates the missing field but the
 * Python upstream sends it explicitly — staying byte-identical avoids
 * silent server-side surprises (e.g. a future server build that
 * mandates the field).
 *
 * Source-scan because driving `send(...)` end-to-end requires a live
 * iLink endpoint. The structural guarantee is sufficient.
 */
class WeixinSendPayloadTest {

    private val source: String by lazy { File(weixinPath()).readText() }

    @Test
    fun `TC-GW-114-a payload includes empty from_user_id`() {
        // Locate the JSONObject that builds the outbound `msg` envelope —
        // it's the only block containing both `to_user_id` and `client_id`
        // in the same JSONObject builder.
        val toUserIdx = source.indexOf("""put("to_user_id", chatId)""")
        assertTrue(
            "TC-GW-114-a: source must contain `put(\"to_user_id\", chatId)` (msg envelope marker)",
            toUserIdx >= 0,
        )
        // Capture a window around the put block (~600 chars upward + 400
        // downward) and assert the `from_user_id` field is set to "".
        val window = source.substring(
            (toUserIdx - 600).coerceAtLeast(0),
            (toUserIdx + 400).coerceAtMost(source.length),
        )
        assertTrue(
            "TC-GW-114-a: outbound msg envelope must `put(\"from_user_id\", \"\")` " +
                "(Python upstream `weixin.py:432`)",
            Regex("""put\s*\(\s*"from_user_id"\s*,\s*""\s*\)""").containsMatchIn(window),
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
