package com.xiaomo.hermes.agent.memory

import java.security.MessageDigest

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/manager.ts (chunkMarkdown)
 *
 * Markdown chunking utilities — aligned with OpenClaw chunkMarkdown()
 */
object ChunkUtils {

    // OpenClaw constants
    const val DEFAULT_CHUNK_TOKENS = 400
    const val DEFAULT_CHUNK_OVERLAP = 80

    data class Chunk(
        val startLine: Int,   // 1-based
        val endLine: Int,     // 1-based, inclusive
        val text: String,
        val hash: String
    )

    /**
     * Split markdown content into overlapping chunks.
     * Aligned with OpenClaw chunkMarkdown:
     * - maxChars = tokens * 4
     * - overlapChars = overlap * 4
     * - line-based splitting with char budget
     * - long lines split by maxChars
     */
    fun chunkMarkdown(
        content: String,
        tokens: Int = DEFAULT_CHUNK_TOKENS,
        overlap: Int = DEFAULT_CHUNK_OVERLAP
    ): List<Chunk> {
        val maxChars = tokens * 4
        val overlapChars = overlap * 4
        val lines = content.lines()
        if (lines.isEmpty()) return emptyList()

        val chunks = mutableListOf<Chunk>()
        var i = 0

        while (i < lines.size) {
            val chunkLines = mutableListOf<String>()
            var chunkStartLine = i + 1  // 1-based
            var charCount = 0

            // Accumulate lines up to maxChars
            while (i < lines.size && charCount + lines[i].length + (if (chunkLines.isEmpty()) 0 else 1) <= maxChars) {
                chunkLines.add(lines[i])
                charCount += lines[i].length + (if (chunkLines.size > 1) 1 else 0)
                i++
            }

            // Handle case where a single line exceeds maxChars
            if (chunkLines.isEmpty() && i < lines.size) {
                val longLine = lines[i]
                var pos = 0
                while (pos < longLine.length) {
                    val end = (pos + maxChars).coerceAtMost(longLine.length)
                    val segment = longLine.substring(pos, end)
                    chunks.add(Chunk(
                        startLine = i + 1,
                        endLine = i + 1,
                        text = segment,
                        hash = hashText(segment)
                    ))
                    pos = end
                }
                i++
                continue
            }

            if (chunkLines.isNotEmpty()) {
                val text = chunkLines.joinToString("\n")
                chunks.add(Chunk(
                    startLine = chunkStartLine,
                    endLine = chunkStartLine + chunkLines.size - 1,
                    text = text,
                    hash = hashText(text)
                ))
            }

            // Overlap: backtrack by overlapChars worth of lines
            if (i < lines.size) {
                var backChars = 0
                var backLines = 0
                for (j in (i - 1) downTo (i - chunkLines.size).coerceAtLeast(0)) {
                    backChars += lines[j].length + 1
                    if (backChars >= overlapChars) break
                    backLines++
                }
                if (backLines > 0) {
                    i -= backLines
                }
            }
        }

        return chunks
    }

    /**
     * Extract keywords from query for FTS5 search.
     * Removes stop words, short words (<3 chars), and punctuation.
     */
    fun extractKeywords(query: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "shall",
            "should", "may", "might", "must", "can", "could", "and", "but", "or",
            "nor", "not", "so", "yet", "both", "either", "neither", "each", "every",
            "all", "any", "few", "more", "most", "other", "some", "such", "no",
            "only", "own", "same", "than", "too", "very", "just", "because",
            "as", "until", "while", "of", "at", "by", "for", "with", "about",
            "against", "between", "through", "during", "before", "after", "above",
            "below", "to", "from", "up", "down", "in", "out", "on", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "what", "which", "who", "whom", "this", "that",
            "these", "those", "i", "me", "my", "myself", "we", "our", "ours",
            "you", "your", "yours", "he", "him", "his", "she", "her", "hers",
            "it", "its", "they", "them", "their", "theirs"
        )

        return query
            .replace(Regex("[^\\w\\s]"), " ")
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
    }

    /**
     * SHA-256 hash of text, hex-encoded.
     */
    fun hashText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
