package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/ir.ts
 *
 * Intermediate representation for parsed markdown content.
 */

enum class MarkdownStyle { BOLD, ITALIC, STRIKETHROUGH, CODE, LINK, HEADING }

data class StyleSpan(
    val style: MarkdownStyle,
    val start: Int,
    val end: Int,
    val level: Int? = null
)

data class LinkSpan(
    val url: String,
    val label: String?,
    val start: Int,
    val end: Int
)

data class MarkdownBlock(
    val type: MarkdownBlockType,
    val text: String,
    val spans: List<StyleSpan> = emptyList(),
    val links: List<LinkSpan> = emptyList(),
    val language: String? = null,
    val level: Int? = null
)

enum class MarkdownBlockType {
    PARAGRAPH, HEADING, CODE_BLOCK, BLOCKQUOTE, LIST_ITEM, HORIZONTAL_RULE, IMAGE
}
