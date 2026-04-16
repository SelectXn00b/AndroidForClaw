package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text/code-regions.ts
 *
 * Detect fenced and inline code regions in markdown text.
 */

data class CodeRegion(val start: Int, val end: Int)

/** Find all fenced code blocks and inline code spans in text. */
fun findCodeRegions(text: String): List<CodeRegion> {
    val regions = mutableListOf<CodeRegion>()

    // Fenced code blocks: ``` or ~~~
    val fencedRe = Regex("""(^|\n)(```|~~~)[^\n]*\n[\s\S]*?(?:\n\2|$)""")
    for (match in fencedRe.findAll(text)) {
        val offset = match.groups[1]?.value?.length ?: 0
        val start = match.range.first + offset
        regions.add(CodeRegion(start, start + match.value.length - offset))
    }

    // Inline code: `...`
    val inlineRe = Regex("""`+[^`]+`+""")
    for (match in inlineRe.findAll(text)) {
        val start = match.range.first
        val end = match.range.last + 1
        val insideFenced = regions.any { start >= it.start && end <= it.end }
        if (!insideFenced) {
            regions.add(CodeRegion(start, end))
        }
    }

    return regions.sortedBy { it.start }
}

/** Check if a character position is inside any code region. */
fun isInsideCode(pos: Int, regions: List<CodeRegion>): Boolean {
    return regions.any { pos >= it.start && pos < it.end }
}
