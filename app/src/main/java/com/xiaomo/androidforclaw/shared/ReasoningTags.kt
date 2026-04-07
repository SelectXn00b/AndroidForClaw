package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text/reasoning-tags.ts
 *
 * Strip <thinking>, <thought>, <antthinking>, <final> tags from text,
 * respecting code regions.
 */

enum class ReasoningTagMode { STRICT, PRESERVE }
enum class ReasoningTagTrim { NONE, START, BOTH }

private val QUICK_TAG_RE = Regex("""<\s*/?\s*(?:(?:antml:)?(?:think(?:ing)?|thought)|antthinking|final)\b""", RegexOption.IGNORE_CASE)
private val FINAL_TAG_RE = Regex("""<\s*/?\s*final\b[^<>]*>""", RegexOption.IGNORE_CASE)
private val THINKING_TAG_RE = Regex("""<\s*(/?)s*(?:(?:antml:)?(?:think(?:ing)?|thought)|antthinking)\b[^<>]*>""", RegexOption.IGNORE_CASE)

private fun applyTrim(value: String, mode: ReasoningTagTrim): String = when (mode) {
    ReasoningTagTrim.NONE -> value
    ReasoningTagTrim.START -> value.trimStart()
    ReasoningTagTrim.BOTH -> value.trim()
}

fun stripReasoningTagsFromText(
    text: String,
    mode: ReasoningTagMode = ReasoningTagMode.STRICT,
    trim: ReasoningTagTrim = ReasoningTagTrim.BOTH
): String {
    if (text.isEmpty()) return text
    if (!QUICK_TAG_RE.containsMatchIn(text)) return text

    // Strip <final> tags first (outside code regions)
    var cleaned = text
    if (FINAL_TAG_RE.containsMatchIn(cleaned)) {
        val preCodeRegions = findCodeRegions(cleaned)
        data class TagMatch(val start: Int, val length: Int, val inCode: Boolean)
        val finalMatches = FINAL_TAG_RE.findAll(cleaned).map { m ->
            TagMatch(m.range.first, m.value.length, isInsideCode(m.range.first, preCodeRegions))
        }.toList()

        for (m in finalMatches.reversed()) {
            if (!m.inCode) {
                cleaned = cleaned.substring(0, m.start) + cleaned.substring(m.start + m.length)
            }
        }
    }

    // Strip <thinking> blocks
    val codeRegions = findCodeRegions(cleaned)
    val result = StringBuilder()
    var lastIndex = 0
    var inThinking = false

    for (match in THINKING_TAG_RE.findAll(cleaned)) {
        val idx = match.range.first
        val isClose = match.groupValues[1] == "/"

        if (isInsideCode(idx, codeRegions)) continue

        if (!inThinking) {
            result.append(cleaned, lastIndex, idx)
            if (!isClose) {
                inThinking = true
            }
        } else if (isClose) {
            inThinking = false
        }

        lastIndex = idx + match.value.length
    }

    if (!inThinking || mode == ReasoningTagMode.PRESERVE) {
        result.append(cleaned.substring(lastIndex))
    }

    return applyTrim(result.toString(), trim)
}
