package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/chat-content.ts
 */

fun assistantVisibleText(content: Any?): String? = when (content) {
    is String -> content
    is List<*> -> content.filterIsInstance<Map<*, *>>()
        .filter { it["type"] == "text" }
        .mapNotNull { it["text"] as? String }
        .joinToString("")
        .ifEmpty { null }
    else -> null
}

fun extractTextFromContent(content: Any?): String = assistantVisibleText(content) ?: ""
