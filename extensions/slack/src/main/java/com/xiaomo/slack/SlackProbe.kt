package com.xiaomo.slack

import android.util.Log
import kotlinx.coroutines.withTimeout

class SlackProbe(private val config: SlackConfig) {
    companion object {
        private const val TAG = "SlackProbe"
    }

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long? = null,
        val botId: String? = null,
        val botUsername: String? = null,
        val teamName: String? = null,
        val error: String? = null
    )

    suspend fun probe(timeoutMs: Long = 5000): ProbeResult {
        if (config.botToken.isBlank()) {
            return ProbeResult(ok = false, error = "Bot token is blank")
        }
        return try {
            val client = SlackClient(config.botToken)
            val start = System.currentTimeMillis()
            val result = withTimeout(timeoutMs) { client.authTest() }
            val latency = System.currentTimeMillis() - start
            result.fold(
                onSuccess = { json ->
                    ProbeResult(
                        ok = true,
                        latencyMs = latency,
                        botId = json.get("user_id")?.asString,
                        botUsername = json.get("user")?.asString,
                        teamName = json.get("team")?.asString
                    )
                },
                onFailure = { e ->
                    ProbeResult(ok = false, error = e.message)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Probe failed: ${e.message}")
            ProbeResult(ok = false, error = e.message)
        }
    }

    suspend fun healthCheck(): Boolean = probe(timeoutMs = 3000).ok
}
