package com.xiaomo.androidforclaw.autoreply

fun shouldSendHeartbeat(lastHeartbeatMs: Long, intervalMs: Long = 60_000): Boolean {
    return System.currentTimeMillis() - lastHeartbeatMs >= intervalMs
}

fun filterHeartbeatPayload(payload: ReplyPayload): ReplyPayload? {
    if (payload.text == SILENT_REPLY_TOKEN) return null
    return payload
}
