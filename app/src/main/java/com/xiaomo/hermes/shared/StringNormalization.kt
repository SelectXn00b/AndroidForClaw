package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/string-normalization.ts
 */

fun normalizeWhitespace(text: String): String = text.replace(Regex("""[^\S\n]+"""), " ").trim()

fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n")

fun normalizeString(text: String): String = normalizeWhitespace(normalizeNewlines(text))

/** Normalize a list of values to trimmed non-blank strings. */
fun normalizeStringEntries(list: List<Any?>?): List<String> =
    (list ?: emptyList()).map { it.toString().trim() }.filter { it.isNotEmpty() }

/** Normalize a list of values to trimmed lowercase non-blank strings. */
fun normalizeStringEntriesLower(list: List<Any?>?): List<String> =
    normalizeStringEntries(list).map { it.lowercase() }

/** Normalize a string to a hyphen-separated slug. */
fun normalizeHyphenSlug(raw: String?): String {
    val trimmed = raw?.trim()?.lowercase() ?: return ""
    if (trimmed.isEmpty()) return ""
    val dashed = trimmed.replace(Regex("""\s+"""), "-")
    val cleaned = dashed.replace(Regex("""[^a-z0-9#@._+\-]+"""), "-")
    return cleaned.replace(Regex("""-{2,}"""), "-").replace(Regex("""^[-.]+|[-.]+$"""), "")
}

/** Normalize a string to a slug, stripping leading @/# prefixes. */
fun normalizeAtHashSlug(raw: String?): String {
    val trimmed = raw?.trim()?.lowercase() ?: return ""
    if (trimmed.isEmpty()) return ""
    val withoutPrefix = trimmed.replace(Regex("""^[@#]+"""), "")
    val dashed = withoutPrefix.replace(Regex("""[\s_]+"""), "-")
    val cleaned = dashed.replace(Regex("""[^a-z0-9-]+"""), "-")
    return cleaned.replace(Regex("""-{2,}"""), "-").replace(Regex("""^-+|-+$"""), "")
}
