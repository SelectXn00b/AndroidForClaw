package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/text/model-special-tokens.ts
 *
 * Strip model-emitted special tokens (<|im_start|>, <|endoftext|>, etc.)
 * from assistant output, respecting code regions.
 */

private val QUICK_SPECIAL_TOKEN_RE = Regex("""<\|""")

private val SPECIAL_TOKEN_PATTERNS = listOf(
    Regex("""<\|im_start\|>[^\n]*\n?"""),
    Regex("""<\|im_end\|>"""),
    Regex("""<\|endoftext\|>"""),
    Regex("""<\|end\|>"""),
    Regex("""<\|eot_id\|>"""),
    Regex("""<\|start_header_id\|>[^\n]*<\|end_header_id\|>\n?"""),
    Regex("""<\|begin_of_text\|>"""),
    Regex("""<\|system\|>"""),
    Regex("""<\|user\|>"""),
    Regex("""<\|assistant\|>""")
)

fun stripModelSpecialTokens(text: String): String {
    if (text.isEmpty()) return text
    if (!QUICK_SPECIAL_TOKEN_RE.containsMatchIn(text)) return text

    val codeRegions = findCodeRegions(text)
    var result = text

    for (pattern in SPECIAL_TOKEN_PATTERNS) {
        val matches = pattern.findAll(result).toList().reversed()
        for (match in matches) {
            if (!isInsideCode(match.range.first, codeRegions)) {
                result = result.removeRange(match.range)
            }
        }
    }

    return result
}
