package com.xiaomo.hermes.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/context-cache.ts
 *
 * Context window token cache — caches model->token-limit resolution.
 */

import java.util.concurrent.ConcurrentHashMap

object ContextCache {
    private val tokenLimitCache = ConcurrentHashMap<String, Int>()

    fun getTokenLimit(modelKey: String): Int? = tokenLimitCache[modelKey]
    fun setTokenLimit(modelKey: String, tokens: Int) { tokenLimitCache[modelKey] = tokens }
    fun clear() { tokenLimitCache.clear() }
}
