package com.xiaomo.androidforclaw.markdown

/**
 * OpenClaw module: markdown
 * Source: OpenClaw/src/markdown/
 *
 * Hub re-export for markdown sub-modules:
 *  - MarkdownIR (block types, span types)
 *  - MarkdownRender (raw → IR parser)
 *  - MarkdownChunking (split into size-limited chunks)
 */
object MarkdownRenderer {

    fun toPlainText(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), "[code]")
            .replace(Regex("`[^`]+`"), { it.value.trim('`') })
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            .replace(Regex("~~(.+?)~~"), "$1")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .trim()
    }

    fun stripCodeBlocks(markdown: String): String {
        return markdown.replace(Regex("```[\\s\\S]*?```"), "").trim()
    }
}
