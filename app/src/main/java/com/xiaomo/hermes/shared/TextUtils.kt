package com.xiaomo.hermes.shared

import kotlin.math.floor
import kotlin.math.max

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text/join-segments.ts + text/strip-markdown.ts +
 *         text/auto-linked-file-ref.ts + string-sample.ts + assistant-identity-values.ts +
 *         model-param-b.ts + entry-metadata.ts
 *
 * Miscellaneous text utilities consolidated from small TS files.
 */

// --- join-segments.ts ---

fun concatOptionalTextSegments(left: String?, right: String?, separator: String = "\n\n"): String? {
    return when {
        left != null && right != null -> "$left$separator$right"
        right != null -> right
        left != null -> left
        else -> null
    }
}

fun joinPresentTextSegments(
    segments: List<String?>,
    separator: String = "\n\n",
    trim: Boolean = false
): String? {
    val values = segments.mapNotNull { s ->
        if (s == null) return@mapNotNull null
        val normalized = if (trim) s.trim() else s
        normalized.ifEmpty { null }
    }
    return if (values.isNotEmpty()) values.joinToString(separator) else null
}

// --- strip-markdown.ts ---

fun stripMarkdown(text: String): String {
    var result = text
    result = result.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    result = result.replace(Regex("""__(.+?)__"""), "$1")
    result = result.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "$1")
    result = result.replace(Regex("""~~(.+?)~~"""), "$1")
    result = result.replace(Regex("""^#{1,6}\s+(.+)$""", RegexOption.MULTILINE), "$1")
    result = result.replace(Regex("""^>\s?(.*)$""", RegexOption.MULTILINE), "$1")
    result = result.replace(Regex("""^[-*_]{3,}$""", RegexOption.MULTILINE), "")
    result = result.replace(Regex("""`([^`]+)`"""), "$1")
    result = result.replace(Regex("""\n{3,}"""), "\n\n")
    return result.trim()
}

// --- auto-linked-file-ref.ts ---

private val FILE_REF_EXTENSIONS = setOf("md", "go", "py", "pl", "sh", "am", "at", "be", "cc")

fun isAutoLinkedFileRef(href: String, label: String): Boolean {
    val stripped = href.replace(Regex("""^https?://""", RegexOption.IGNORE_CASE), "")
    if (stripped != label) return false
    val dotIndex = label.lastIndexOf('.')
    if (dotIndex < 1) return false
    val ext = label.substring(dotIndex + 1).lowercase()
    if (ext !in FILE_REF_EXTENSIONS) return false
    val segments = label.split("/")
    if (segments.size > 1) {
        for (i in 0 until segments.size - 1) {
            if ('.' in (segments[i] ?: "")) return false
        }
    }
    return true
}

// --- string-sample.ts ---

fun summarizeStringEntries(
    entries: List<String>?,
    limit: Int = 6,
    emptyText: String = ""
): String {
    if (entries.isNullOrEmpty()) return emptyText
    val safeLimit = max(1, floor(limit.toDouble()).toInt())
    val sample = entries.take(safeLimit)
    val suffix = if (entries.size > sample.size) " (+${entries.size - sample.size})" else ""
    return "${sample.joinToString(", ")}$suffix"
}

// --- assistant-identity-values.ts ---

fun coerceIdentityValue(value: String?, maxLength: Int): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.length <= maxLength) trimmed else trimmed.take(maxLength)
}

// --- model-param-b.ts ---

private val PARAM_B_RE = Regex("""(?:^|[^a-z0-9])[a-z]?(\d+(?:\.\d+)?)b(?:[^a-z0-9]|$)""")

fun inferParamBFromIdOrName(text: String): Double? {
    val raw = text.lowercase()
    var best: Double? = null
    for (match in PARAM_B_RE.findAll(raw)) {
        val numRaw = match.groupValues[1]
        if (numRaw.isEmpty()) continue
        val value = numRaw.toDoubleOrNull() ?: continue
        if (!value.isFinite() || value <= 0) continue
        if (best == null || value > best) best = value
    }
    return best
}

// --- entry-metadata.ts ---

data class ResolvedEmojiAndHomepage(
    val emoji: String? = null,
    val homepage: String? = null
)

fun resolveEmojiAndHomepage(
    metadataEmoji: String? = null,
    metadataHomepage: String? = null,
    frontmatterEmoji: String? = null,
    frontmatterHomepage: String? = null,
    frontmatterWebsite: String? = null,
    frontmatterUrl: String? = null
): ResolvedEmojiAndHomepage {
    val emoji = metadataEmoji ?: frontmatterEmoji
    val homepageRaw = metadataHomepage ?: frontmatterHomepage ?: frontmatterWebsite ?: frontmatterUrl
    val homepage = homepageRaw?.trim()?.ifEmpty { null }
    return ResolvedEmojiAndHomepage(emoji, homepage)
}
