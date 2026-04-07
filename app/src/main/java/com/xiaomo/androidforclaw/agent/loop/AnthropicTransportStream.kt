package com.xiaomo.androidforclaw.agent.loop

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/anthropic-transport-stream.ts
 *
 * Anthropic-specific streaming transport adapter.
 * Android: streaming is handled by UnifiedLLMProvider; this file exists for 1:1 alignment.
 */

object AnthropicTransportStream {
    // Android uses UnifiedLLMProvider.chatWithToolsStreaming() which handles
    // Anthropic SSE streaming internally. This stub exists for file alignment.
}
