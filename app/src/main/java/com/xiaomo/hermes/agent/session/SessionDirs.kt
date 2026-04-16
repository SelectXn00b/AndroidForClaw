package com.xiaomo.hermes.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/session-dirs.ts
 *
 * Session directory and file path management.
 */

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val SESSIONS_DIR = "sessions"
internal const val SESSIONS_INDEX = "sessions.json"
internal const val AUTO_PRUNE_DAYS = 30

internal fun getSessionJSONLFile(sessionsDir: File, sessionId: String): File {
    return File(sessionsDir, "$sessionId.jsonl")
}

internal fun ensureSessionFileParentExists(sessionFile: File) {
    sessionFile.parentFile?.mkdirs()
}

internal fun currentTimestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .format(Date())
}
