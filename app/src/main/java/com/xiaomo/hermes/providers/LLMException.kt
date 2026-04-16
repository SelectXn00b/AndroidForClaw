package com.xiaomo.hermes.providers

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/failover-error.ts
 */


/**
 * Legacy LLM API Exception
 */
class LLMException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
