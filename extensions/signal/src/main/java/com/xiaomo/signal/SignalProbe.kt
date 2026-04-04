package com.xiaomo.signal

import android.util.Log
import kotlinx.coroutines.withTimeout

class SignalProbe(private val config: SignalConfig) {
    companion object {
        private const val TAG = "SignalProbe"
    }

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long? = null,
        val version: String? = null,
        val error: String? = null
    )

    suspend fun probe(timeoutMs: Long = 5000): ProbeResult {
        if (config.phoneNumber.isBlank()) {
            return ProbeResult(ok = false, error = "Phone number is blank")
        }
        return try {
            val baseUrl = config.httpUrl.trimEnd('/') + ":" + config.httpPort
            val client = SignalClient(baseUrl, config.phoneNumber)
            val start = System.currentTimeMillis()
            val result = withTimeout(timeoutMs) { client.about() }
            val latency = System.currentTimeMillis() - start
            result.fold(
                onSuccess = { json ->
                    ProbeResult(
                        ok = true,
                        latencyMs = latency,
                        version = json.get("version")?.asString
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
