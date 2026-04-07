package com.xiaomo.androidforclaw.agent.session

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-utils.ts
 *
 * Utility functions for the embedded agent runner.
 */

/**
 * Leaked model control token patterns (aligned with OpenClaw 2026.3.11)
 *
 * Strips `<|...|>` and full-width `<｜...｜>` variant delimiters that
 * GLM-5, DeepSeek, and other models may leak into assistant text.
 * See: OpenClaw #42173
 */
internal val CONTROL_TOKEN_RE = Regex("<[|｜][^|｜]*[|｜]>")

/**
 * Strip control tokens from a single text string.
 * Aligned with OpenClaw stripModelSpecialTokens:
 * Replace each match with a single space, then collapse runs of spaces.
 */
fun stripControlTokensFromText(text: String): String {
    if (!text.contains('<')) return text
    if (!CONTROL_TOKEN_RE.containsMatchIn(text)) return text
    return CONTROL_TOKEN_RE.replace(text, " ").replace(Regex("  +"), " ").trim()
}
