package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text/assistant-visible-text.ts
 *
 * Extract the user-visible text portion from an assistant's response content,
 * stripping reasoning tags, special tokens, and tool-use blocks.
 *
 * This is the core text extraction pipeline used for display, TTS, and notifications.
 */

data class AssistantVisibleTextOptions(
    val stripReasoning: Boolean = true,
    val reasoningMode: ReasoningTagMode = ReasoningTagMode.STRICT,
    val stripSpecialTokens: Boolean = true,
    val stripToolUse: Boolean = true,
    val collapseWhitespace: Boolean = false
)

/**
 * Extract visible text from assistant content.
 * Handles String, List<ContentBlock>, and structured content.
 */
fun extractAssistantVisibleText(
    content: Any?,
    options: AssistantVisibleTextOptions = AssistantVisibleTextOptions()
): String {
    val rawText = extractRawText(content)
    if (rawText.isEmpty()) return rawText

    var text = rawText

    // Strip reasoning tags (<thinking>, etc.)
    if (options.stripReasoning) {
        text = stripReasoningTagsFromText(text, options.reasoningMode)
    }

    // Strip model special tokens (<|im_start|>, etc.)
    if (options.stripSpecialTokens) {
        text = stripModelSpecialTokens(text)
    }

    // Strip tool_use blocks
    if (options.stripToolUse) {
        text = stripToolUseBlocks(text)
    }

    // Collapse excessive whitespace if requested
    if (options.collapseWhitespace) {
        text = collapseWhitespace(text)
    }

    return text.trim()
}

/** Extract raw text from various content formats. */
private fun extractRawText(content: Any?): String = when (content) {
    is String -> content
    is List<*> -> {
        content.mapNotNull { block ->
            when (block) {
                is String -> block
                is Map<*, *> -> {
                    when (block["type"]) {
                        "text" -> block["text"] as? String
                        else -> null
                    }
                }
                else -> null
            }
        }.joinToString("")
    }
    else -> ""
}

/** Strip tool_use content blocks from text. */
private fun stripToolUseBlocks(text: String): String {
    // Remove common tool-use patterns that may appear as text
    return text
        .replace(Regex("""<tool_use>[\s\S]*?</tool_use>"""), "")
        .replace(Regex("""<function_calls>[\s\S]*?</function_calls>"""), "")
}

/** Collapse multiple blank lines to at most two newlines, normalize spaces. */
private fun collapseWhitespace(text: String): String {
    return text
        .replace(Regex("""\n{3,}"""), "\n\n")
        .replace(Regex("""[^\S\n]+"""), " ")
}
