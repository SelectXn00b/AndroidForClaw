package com.xiaomo.hermes.chat

/**
 * OpenClaw module: chat
 * Source: OpenClaw/src/chat/tool-content.ts
 *
 * Normalizes and inspects tool-use content blocks in chat messages
 * (tool calls and tool results), independent of provider format.
 */

/** Generic tool content block — provider-agnostic representation. */
typealias ToolContentBlock = Map<String, Any?>

/** Known tool content block types. */
object ToolContentType {
    const val TOOL_USE = "tool_use"
    const val TOOL_RESULT = "tool_result"
    const val FUNCTION = "function"
    const val FUNCTION_RESULT = "function_result"
}

/** Normalize a raw content type string to a canonical tool content type. */
fun normalizeToolContentType(value: Any?): String? {
    val str = (value as? String) ?: return null
    return when (str) {
        "tool_use", "function_call", "function" -> ToolContentType.TOOL_USE
        "tool_result", "function_result" -> ToolContentType.TOOL_RESULT
        else -> null
    }
}

fun isToolCallContentType(value: Any?): Boolean =
    normalizeToolContentType(value) == ToolContentType.TOOL_USE

fun isToolResultContentType(value: Any?): Boolean =
    normalizeToolContentType(value) == ToolContentType.TOOL_RESULT

fun isToolCallBlock(block: ToolContentBlock): Boolean =
    isToolCallContentType(block["type"])

fun isToolResultBlock(block: ToolContentBlock): Boolean =
    isToolResultContentType(block["type"])

fun resolveToolBlockArgs(block: ToolContentBlock): Any? =
    block["input"] ?: block["arguments"] ?: block["function"]?.let {
        (it as? Map<*, *>)?.get("arguments")
    }

fun resolveToolUseId(block: ToolContentBlock): String? =
    (block["id"] ?: block["tool_use_id"] ?: block["call_id"]) as? String
