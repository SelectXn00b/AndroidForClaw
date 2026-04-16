package com.xiaomo.hermes.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/defaults.ts
 *
 * Hermes adaptation: default agent constants.
 */

/**
 * Default agent constants.
 * Aligned with OpenClaw agents/defaults.ts.
 */
object AgentDefaults {
    const val DEFAULT_PROVIDER = "anthropic"
    const val DEFAULT_MODEL = "claude-opus-4-6"
    const val DEFAULT_CONTEXT_TOKENS = 200_000
}
