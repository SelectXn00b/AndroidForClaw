package com.xiaomo.hermes.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-runner/system-prompt.ts
 *
 * Embedded system prompt wrapper types.
 */

/**
 * Prompt Mode (reference OpenClaw)
 * Moved from ContextBuilder.Companion for 1:1 file alignment.
 */
enum class PromptMode {
    FULL,      // Main Agent - All 22 parts
    MINIMAL,   // Sub Agent - Core parts only
    NONE       // Minimal mode - Basic identity only
}

/**
 * Channel context for messaging awareness (passed from gateway layer).
 * Tells the agent where the current message came from and how replies are routed.
 */
data class ChannelContext(
    val channel: String = "android",
    val chatId: String? = null,
    val chatType: String? = null,
    val senderId: String? = null,
    val messageId: String? = null
)
