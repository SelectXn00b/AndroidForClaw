package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/chunking.ts
 *
 * Splits markdown into size-limited chunks that respect block boundaries.
 */
object MarkdownChunking {

    fun chunkMarkdown(text: String, maxChunkLength: Int = 4000): List<String> {
        if (text.length <= maxChunkLength) return listOf(text)
        val chunks = mutableListOf<String>()
        val paragraphs = text.split(Regex("\n{2,}"))
        val current = StringBuilder()
        for (para in paragraphs) {
            if (current.length + para.length + 2 > maxChunkLength && current.isNotEmpty()) {
                chunks.add(current.toString().trimEnd())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(para)
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trimEnd())
        return chunks
    }

    fun chunkMarkdownBlocks(blocks: List<MarkdownBlock>, maxChunkLength: Int = 4000): List<List<MarkdownBlock>> {
        if (blocks.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<MarkdownBlock>>()
        var current = mutableListOf<MarkdownBlock>()
        var currentLen = 0
        for (block in blocks) {
            val blockLen = block.text.length + 2 // +2 for paragraph separator
            if (currentLen + blockLen > maxChunkLength && current.isNotEmpty()) {
                chunks.add(current)
                current = mutableListOf()
                currentLen = 0
            }
            current.add(block)
            currentLen += blockLen
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }
}
