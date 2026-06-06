package com.xiaomo.hermes.hermes.gateway

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Append-only store for replies that the agent generated but the gateway failed to deliver
 * (after one retry). Backed by a JSONL file (one JSON object per line).
 *
 * **Threading**: Designed for single-writer (GatewayRunner is per-process singleton).
 * Concurrent appends from multiple platforms are serialized inside the GatewayRunner's
 * dispatch loop, so we don't add internal locking.
 *
 * **R-GW-003 bugfix (2026-06-06)**: when Feishu / other gateway sends fail silently
 * (network glitch, expired token, SDK WS death), the agent's careful reply was lost
 * forever. Now we persist it here and `UndeliveredReplyNotifier` pops a local Android
 * notification so the user can copy-paste it manually.
 */
class UndeliveredReplyStore(private val file: File) {

    /** Append one failed-delivery entry. Best-effort: swallows IO errors with a log. */
    fun append(platform: String, chatId: String, text: String, error: String) {
        try {
            file.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("platform", platform)
                put("chatId", chatId)
                put("text", text)
                put("error", error)
                put("timestampMs", System.currentTimeMillis())
            }
            // JSONL: one object per line, append mode
            file.appendText(json.toString() + "\n")
        } catch (e: Throwable) {
            Log.w(TAG, "append failed: ${e.message}")
        }
    }

    /** Read all entries in append order. Returns empty list on missing file or parse errors. */
    fun read(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        val o = JSONObject(line)
                        Entry(
                            platform = o.optString("platform", ""),
                            chatId = o.optString("chatId", ""),
                            text = o.optString("text", ""),
                            error = o.optString("error", ""),
                            timestampMs = o.optLong("timestampMs", 0L),
                        )
                    } catch (e: Throwable) {
                        Log.w(TAG, "skipping malformed entry: ${e.message}")
                        null
                    }
                }
        } catch (e: Throwable) {
            Log.w(TAG, "read failed: ${e.message}")
            emptyList()
        }
    }

    /** Truncate the store. Called after user has dealt with all pending entries. */
    fun clear() {
        try {
            if (file.exists()) file.writeText("")
        } catch (e: Throwable) {
            Log.w(TAG, "clear failed: ${e.message}")
        }
    }

    data class Entry(
        val platform: String,
        val chatId: String,
        val text: String,
        val error: String,
        val timestampMs: Long,
    )

    companion object {
        private const val TAG = "UndeliveredStore"
    }
}
