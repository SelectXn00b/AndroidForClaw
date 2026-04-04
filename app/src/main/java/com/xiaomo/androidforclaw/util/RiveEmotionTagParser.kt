package com.xiaomo.androidforclaw.util

object RiveEmotionTagParser {
    private val TAG_RE = Regex("""\[rive:(\w+)]""", RegexOption.IGNORE_CASE)

    data class Result(val cleanText: String, val emotion: String?)

    fun parse(text: String): Result {
        val match = TAG_RE.find(text) ?: return Result(text, null)
        val emotion = match.groupValues[1].lowercase()
        val cleaned = TAG_RE.replace(text, "").trimEnd()
        return Result(cleaned, emotion)
    }
}
