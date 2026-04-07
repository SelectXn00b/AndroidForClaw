package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/render.ts
 *
 * Parses raw markdown text into MarkdownBlock IR.
 */
object MarkdownRender {

    private val FENCE_OPEN = Regex("""^```(\w*)""")
    private val HEADING = Regex("""^(#{1,6})\s+(.+)""")
    private val HR = Regex("""^[-*_]{3,}\s*$""")
    private val BLOCKQUOTE = Regex("""^>\s?(.*)""")
    private val LIST_ITEM = Regex("""^(\s*[-*+]|\s*\d+\.)\s+(.+)""")
    private val IMAGE = Regex("""^!\[([^\]]*)]\(([^)]+)\)""")

    fun parseMarkdown(raw: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = raw.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Fenced code block
            val fenceMatch = FENCE_OPEN.find(line)
            if (fenceMatch != null) {
                val lang = fenceMatch.groupValues[1].ifEmpty { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing ```
                blocks.add(MarkdownBlock(MarkdownBlockType.CODE_BLOCK, codeLines.joinToString("\n"), language = lang))
                continue
            }

            // Blank line — skip
            if (line.isBlank()) { i++; continue }

            // Horizontal rule
            if (HR.matches(line)) {
                blocks.add(MarkdownBlock(MarkdownBlockType.HORIZONTAL_RULE, "───"))
                i++; continue
            }

            // Heading
            val headingMatch = HEADING.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                blocks.add(MarkdownBlock(MarkdownBlockType.HEADING, text, level = level,
                    spans = listOf(StyleSpan(MarkdownStyle.HEADING, 0, text.length, level = level))))
                i++; continue
            }

            // Blockquote
            val bqMatch = BLOCKQUOTE.find(line)
            if (bqMatch != null) {
                val bqLines = mutableListOf(bqMatch.groupValues[1])
                i++
                while (i < lines.size) {
                    val next = BLOCKQUOTE.find(lines[i])
                    if (next != null) { bqLines.add(next.groupValues[1]); i++ }
                    else break
                }
                val text = bqLines.joinToString("\n")
                blocks.add(MarkdownBlock(MarkdownBlockType.BLOCKQUOTE, text))
                continue
            }

            // Image
            val imgMatch = IMAGE.find(line)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                blocks.add(MarkdownBlock(MarkdownBlockType.IMAGE, alt,
                    links = listOf(LinkSpan(url, alt, 0, alt.length))))
                i++; continue
            }

            // List item
            val listMatch = LIST_ITEM.find(line)
            if (listMatch != null) {
                val text = listMatch.groupValues[2]
                blocks.add(MarkdownBlock(MarkdownBlockType.LIST_ITEM, text,
                    spans = parseInlineSpans(text), links = parseInlineLinks(text)))
                i++; continue
            }

            // Paragraph — collect contiguous non-blank lines
            val paraLines = mutableListOf(line)
            i++
            while (i < lines.size && lines[i].isNotBlank()
                && !HEADING.containsMatchIn(lines[i])
                && !FENCE_OPEN.containsMatchIn(lines[i])
                && !HR.matches(lines[i])
                && !BLOCKQUOTE.containsMatchIn(lines[i])
                && !IMAGE.containsMatchIn(lines[i])
            ) {
                paraLines.add(lines[i]); i++
            }
            val text = paraLines.joinToString("\n")
            blocks.add(MarkdownBlock(MarkdownBlockType.PARAGRAPH, text,
                spans = parseInlineSpans(text), links = parseInlineLinks(text)))
        }
        return blocks
    }

    private val BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val ITALIC = Regex("""\*(.+?)\*|_(.+?)_""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val CODE_INLINE = Regex("""`([^`]+)`""")
    private val LINK_INLINE = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    internal fun parseInlineSpans(text: String): List<StyleSpan> {
        val spans = mutableListOf<StyleSpan>()
        for (m in BOLD.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.BOLD, m.range.first, m.range.last + 1))
        }
        for (m in ITALIC.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.ITALIC, m.range.first, m.range.last + 1))
        }
        for (m in STRIKE.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.STRIKETHROUGH, m.range.first, m.range.last + 1))
        }
        for (m in CODE_INLINE.findAll(text)) {
            spans.add(StyleSpan(MarkdownStyle.CODE, m.range.first, m.range.last + 1))
        }
        return spans
    }

    internal fun parseInlineLinks(text: String): List<LinkSpan> {
        return LINK_INLINE.findAll(text).map { m ->
            LinkSpan(url = m.groupValues[2], label = m.groupValues[1],
                start = m.range.first, end = m.range.last + 1)
        }.toList()
    }

    fun renderBlocksToPlainText(blocks: List<MarkdownBlock>): String {
        return blocks.joinToString("\n\n") { it.text }
    }

    fun extractCodeBlocks(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
        return blocks.filter { it.type == MarkdownBlockType.CODE_BLOCK }
    }
}
