package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/chat-envelope.ts
 */

private val MESSAGE_ID_HINT_RE = Regex("""\[msg-id:[^\]]+\]""")

fun stripEnvelope(text: String): String = text.trim()

fun stripMessageIdHints(text: String): String = MESSAGE_ID_HINT_RE.replace(text, "").trim()
