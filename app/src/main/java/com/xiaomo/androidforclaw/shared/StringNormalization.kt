package com.xiaomo.androidforclaw.shared

fun normalizeWhitespace(text: String): String = text.replace(Regex("""[^\S\n]+"""), " ").trim()

fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n")

fun normalizeString(text: String): String = normalizeWhitespace(normalizeNewlines(text))
