package com.ai.assistance.operit.hermes.gateway

import com.ai.assistance.operit.util.ChatMarkupRegex

/**
 * TC-CRON-SANITIZE-a (R-AGENT-031 / R-AGENT-035): canonical strip of
 * Hermes internal XML markup from a reply text segment.
 *
 * **Why this exists as a top-level object**: `HermesGatewayController.stripMarkup`
 * previously held the canonical regex chain.  But `CronAgentRunner.run`
 * (the headless cron path; `EnhancedAIService.sendMessage(stream=true)`
 * direct consumer) needed the same logic and was bypassing it ——
 * `<think>...</think>` leaked into Weixin / Telegram / local chat note
 * deliveries (2026-06-24 bug report).  Extracting to a shared object
 * gives one source of truth; the controller's `stripMarkup` now
 * delegates here.
 *
 * **Scope**: strips reply-side markup that the agent loop emits via
 * `AgentEventBus` → `EnhancedAIService.kt:1165-1209`:
 *  - `<think>...</think>` / `<thinking>...</thinking>` (closed)
 *  - `<think...>...` (unclosed — model cut off mid-stream; sweep to EOS)
 *  - `<tool name="...">...</tool>` (open + self-closing)
 *  - `<tool_result>...</tool_result>` (open + self-closing)
 *  - `<status type="complete">...</status>` (open + self-closing)
 *
 * **Out of scope**: status-tag-based slicing (multi-turn final-reply
 * selection).  That logic stays in `HermesGatewayController.extractFinalReply`
 * because it depends on the full-content `<status>` boundary walk.
 * `CronAgentRunner` cron path produces a single-turn response so it
 * only needs `strip(...)`.
 */
object HermesReplyMarkupStripper {

    /**
     * Catches unclosed `<think>` / `<thinking>` tags.  The model sometimes
     * emits `<think>…` without a matching `</think>` (stream cutoff or
     * malformed output).  After paired-tag regexes have removed properly
     * closed blocks, this sweeps any remaining opening-tag-to-end-of-string
     * residue.
     *
     * Mirror of the private `UNCLOSED_THINK_REGEX` that previously lived
     * in `HermesGatewayController`.  Kept identical to avoid behavior
     * drift.
     */
    private val UNCLOSED_THINK_REGEX = Regex(
        "<think(?:ing)?\\b[^>]*>[\\s\\S]*",
        RegexOption.IGNORE_CASE
    )

    /** Strip all internal XML markup from a text segment, leaving only user-visible text. */
    fun strip(text: String): String {
        return text
            .replace(ChatMarkupRegex.thinkTag, "")
            .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
    }
}
