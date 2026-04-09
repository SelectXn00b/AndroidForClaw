package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/bootstrap-cache.ts
 *
 * Cache for loaded bootstrap files to avoid re-reading from disk.
 */

import java.util.concurrent.ConcurrentHashMap

object BootstrapCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun get(key: String): String? = cache[key]
    fun put(key: String, value: String) { cache[key] = value }
    fun invalidate(key: String) { cache.remove(key) }
    fun clear() { cache.clear() }
}
