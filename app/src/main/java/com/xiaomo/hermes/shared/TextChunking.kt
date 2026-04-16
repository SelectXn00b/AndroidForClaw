package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text-chunking.ts
 *
 * Split text into chunks respecting a character limit and custom break points.
 */

fun chunkTextByBreakResolver(
    text: String,
    limit: Int,
    resolveBreakIndex: (window: String) -> Int
): List<String> {
    if (text.isEmpty()) return emptyList()
    if (limit <= 0 || text.length <= limit) return listOf(text)

    val chunks = mutableListOf<String>()
    var remaining = text

    while (remaining.length > limit) {
        val window = remaining.substring(0, limit)
        val candidateBreak = resolveBreakIndex(window)
        val breakIdx = if (candidateBreak in 1..limit) candidateBreak else limit

        val rawChunk = remaining.substring(0, breakIdx)
        val chunk = rawChunk.trimEnd()
        if (chunk.isNotEmpty()) {
            chunks.add(chunk)
        }

        // If we broke on a whitespace separator, skip it
        val brokeOnSeparator = breakIdx < remaining.length && remaining[breakIdx].isWhitespace()
        val nextStart = minOf(remaining.length, breakIdx + if (brokeOnSeparator) 1 else 0)
        remaining = remaining.substring(nextStart).trimStart()
    }

    if (remaining.isNotEmpty()) {
        chunks.add(remaining)
    }
    return chunks
}
