package com.xiaomo.androidforclaw.routing

/**
 * OpenClaw module: routing
 * Source: OpenClaw/src/routing/session-key.ts
 */

const val DEFAULT_AGENT_ID = "main"
const val DEFAULT_MAIN_KEY = "main"
const val DEFAULT_ACCOUNT_ID = "default"

enum class SessionKeyShape { MISSING, AGENT, LEGACY_OR_ALIAS, MALFORMED_AGENT }

data class ParsedAgentSessionKey(val agentId: String, val rest: String)

private val AGENT_KEY_RE = Regex("""^([a-z0-9_-]+)/(.+)$""")

fun parseAgentSessionKey(sessionKey: String?): ParsedAgentSessionKey? {
    if (sessionKey.isNullOrBlank()) return null
    val match = AGENT_KEY_RE.matchEntire(sessionKey) ?: return null
    return ParsedAgentSessionKey(match.groupValues[1], match.groupValues[2])
}

fun normalizeAgentId(agentId: String?): String =
    agentId?.trim()?.lowercase()?.ifEmpty { null } ?: DEFAULT_AGENT_ID

fun sanitizeAgentId(raw: String): String =
    raw.trim().lowercase().replace(Regex("""[^a-z0-9_-]"""), "-")

fun buildAgentMainSessionKey(agentId: String, mainKey: String? = null): String =
    "$agentId/${mainKey ?: DEFAULT_MAIN_KEY}"

fun buildAgentPeerSessionKey(
    agentId: String,
    chatType: String,
    peerId: String,
    accountId: String? = null
): String {
    val account = accountId ?: DEFAULT_ACCOUNT_ID
    return "$agentId/$chatType/$account/$peerId"
}

fun resolveAgentIdFromSessionKey(sessionKey: String?): String {
    val parsed = parseAgentSessionKey(sessionKey)
    return parsed?.agentId ?: DEFAULT_AGENT_ID
}

fun classifySessionKeyShape(sessionKey: String?): SessionKeyShape = when {
    sessionKey.isNullOrBlank() -> SessionKeyShape.MISSING
    AGENT_KEY_RE.matches(sessionKey) -> SessionKeyShape.AGENT
    else -> SessionKeyShape.LEGACY_OR_ALIAS
}
