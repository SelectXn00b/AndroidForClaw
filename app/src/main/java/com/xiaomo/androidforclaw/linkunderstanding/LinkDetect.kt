package com.xiaomo.androidforclaw.linkunderstanding

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/detect.ts
 */

private val URL_REGEX = Regex(
    "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
    RegexOption.IGNORE_CASE
)

data class DetectedLink(
    val url: String,
    val startIndex: Int,
    val endIndex: Int
)

fun extractLinksFromMessage(text: String): List<DetectedLink> {
    return URL_REGEX.findAll(text).map { match ->
        DetectedLink(
            url = match.value,
            startIndex = match.range.first,
            endIndex = match.range.last + 1
        )
    }.toList()
}

fun hasLinks(text: String): Boolean = URL_REGEX.containsMatchIn(text)

fun extractFirstLink(text: String): String? = URL_REGEX.find(text)?.value
