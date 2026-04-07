package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/render.ts
 *
 * Parses raw markdown text into MarkdownBlock IR.
 */
object MarkdownRender {

    fun parseMarkdown(raw: String): List<MarkdownBlock> {
        TODO("Parse markdown string into block IR")
    }

    fun renderBlocksToPlainText(blocks: List<MarkdownBlock>): String {
        return blocks.joinToString("\n\n") { it.text }
    }

    fun extractCodeBlocks(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
        return blocks.filter { it.type == MarkdownBlockType.CODE_BLOCK }
    }
}
