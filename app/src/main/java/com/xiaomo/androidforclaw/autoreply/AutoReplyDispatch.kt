package com.xiaomo.androidforclaw.autoreply

/**
 * OpenClaw module: auto-reply
 * Source: OpenClaw/src/auto-reply/dispatch.ts
 *
 * Handles automatic reply generation for incoming chat messages:
 * directive parsing, slash commands, model routing, message queuing,
 * block streaming, and reply delivery.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class DispatchInboundResult(
    val replied: Boolean,
    val commandDetected: String? = null,
    val error: String? = null
)

object AutoReplyDispatch {

    suspend fun dispatchInboundMessage(
        text: String,
        sessionKey: String,
        channel: String,
        config: OpenClawConfig
    ): DispatchInboundResult {
        // 1. Check for slash command
        val cmdMatch = detectCommand(text)
        if (cmdMatch != null) {
            return DispatchInboundResult(replied = false, commandDetected = cmdMatch)
        }

        // 2. Delegate to agent loop (via MainEntryNew / AgentMessageReceiver)
        // The actual LLM call is handled by the existing Android agent pipeline;
        // this function serves as the gating layer before that pipeline.
        return DispatchInboundResult(replied = true)
    }

    private fun detectCommand(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null
        val cmd = trimmed.split("\\s+".toRegex(), limit = 2).firstOrNull() ?: return null
        return cmd.lowercase()
    }
}
