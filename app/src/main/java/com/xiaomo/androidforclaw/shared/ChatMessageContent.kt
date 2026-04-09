package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/chat-message-content.ts
 *
 * Content block type handling for chat messages.
 * Handles both simple String content and structured content-block lists.
 */

/** Normalize content to a string, extracting text blocks from content-block arrays. */
fun normalizeContentToString(content: Any?): String = when (content) {
    is String -> content
    is List<*> -> content.mapNotNull { block ->
        when (block) {
            is String -> block
            is Map<*, *> -> when (block["type"]) {
                "text" -> block["text"] as? String
                else -> null
            }
            else -> null
        }
    }.joinToString("")
    else -> ""
}

/** Check if content contains any text. */
fun hasTextContent(content: Any?): Boolean = normalizeContentToString(content).isNotBlank()

/** Check if content contains a tool_use block. */
fun hasToolUseContent(content: Any?): Boolean {
    if (content !is List<*>) return false
    return content.any { block ->
        block is Map<*, *> && block["type"] == "tool_use"
    }
}

/** Check if content contains a tool_result block. */
fun hasToolResultContent(content: Any?): Boolean {
    if (content !is List<*>) return false
    return content.any { block ->
        block is Map<*, *> && block["type"] == "tool_result"
    }
}

/** Extract all tool_use blocks from content. */
fun extractToolUseBlocks(content: Any?): List<Map<*, *>> {
    if (content !is List<*>) return emptyList()
    return content.filterIsInstance<Map<*, *>>()
        .filter { it["type"] == "tool_use" }
}

/** Count the number of text blocks in content. */
fun countTextBlocks(content: Any?): Int {
    if (content is String) return if (content.isNotEmpty()) 1 else 0
    if (content !is List<*>) return 0
    return content.count { block ->
        block is Map<*, *> && block["type"] == "text" && (block["text"] as? String)?.isNotEmpty() == true
    }
}

/** Extract only image blocks from content. */
fun extractImageBlocks(content: Any?): List<Map<*, *>> {
    if (content !is List<*>) return emptyList()
    return content.filterIsInstance<Map<*, *>>()
        .filter { it["type"] == "image" }
}

/** Get the first text content from a content value. */
fun firstTextContent(content: Any?): String? = when (content) {
    is String -> content.ifEmpty { null }
    is List<*> -> content.filterIsInstance<Map<*, *>>()
        .firstOrNull { it["type"] == "text" }
        ?.let { it["text"] as? String }
        ?.ifEmpty { null }
    else -> null
}
