/**
 * OpenClaw Source Reference:
 * - src/utils/directive-tags.ts (REPLY_TAG_RE)
 */
package com.xiaomo.hermes.util

/**
 * Strip [[reply_to_current]] and [[reply_to:<id>]] tags from LLM output.
 * Aligned with OpenClaw directive-tags.ts: REPLY_TAG_RE
 */
object ReplyTagFilter {
    // Aligned with OpenClaw: /\[\[\s*(?:reply_to_current|reply_to\s*:\s*([^\]\n]+))\s*\]\]/gi
    private val REPLY_TAG_RE = Regex(
        """\[\[\s*(?:reply_to_current|reply_to\s*:\s*([^\]\n]+))\s*]]""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strip reply tags and return cleaned text.
     */
    fun strip(text: String): String {
        if (!text.contains("[[")) return text
        return REPLY_TAG_RE.replace(text, " ").trim()
    }
}
