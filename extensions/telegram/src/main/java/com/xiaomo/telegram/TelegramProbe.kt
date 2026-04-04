package com.xiaomo.telegram

import android.util.Log
import kotlinx.coroutines.withTimeout

class TelegramProbe(private val config: TelegramConfig) {
    companion object {
        private const val TAG = "TelegramProbe"
    }

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long? = null,
        val botId: String? = null,
        val botUsername: String? = null,
        val error: String? = null
    )

    suspend fun probe(timeoutMs: Long = 5000): ProbeResult {
        if (config.botToken.isBlank()) {
            return ProbeResult(ok = false, error = "Bot token is blank")
        }
        return try {
            val client = TelegramClient(config.botToken)
            val start = System.currentTimeMillis()
            val me = withTimeout(timeoutMs) { client.getMe() }
            val latency = System.currentTimeMillis() - start
            me.fold(
                onSuccess = { json ->
                    ProbeResult(
                        ok = true,
                        latencyMs = latency,
                        botId = json.get("id")?.asString,
                        botUsername = json.get("username")?.asString
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
