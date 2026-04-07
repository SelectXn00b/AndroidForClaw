package com.xiaomo.androidforclaw.shared

fun chunkTextByBreakResolver(text: String, limit: Int, resolveBreakIndex: (String) -> Int): List<String> {
    if (text.length <= limit) return listOf(text)
    val chunks = mutableListOf<String>()
    var remaining = text
    while (remaining.length > limit) {
        val breakIndex = resolveBreakIndex(remaining.substring(0, limit))
        val splitAt = if (breakIndex > 0) breakIndex else limit
        chunks.add(remaining.substring(0, splitAt))
        remaining = remaining.substring(splitAt).trimStart()
    }
    if (remaining.isNotEmpty()) chunks.add(remaining)
    return chunks
}
