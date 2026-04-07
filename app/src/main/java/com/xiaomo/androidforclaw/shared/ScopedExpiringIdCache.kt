package com.xiaomo.androidforclaw.shared

import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/scoped-expiring-id-cache.ts
 *
 * A cache that associates IDs with scopes and auto-expires entries after a TTL.
 */

class ScopedExpiringIdCache(
    private val ttlMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val scope: String, val expiresAt: Long)

    private val store = ConcurrentHashMap<String, Entry>()

    /** Add an ID to the cache with the given scope. */
    fun add(id: String, scope: String) {
        prune()
        store[id] = Entry(scope, clock() + ttlMs)
    }

    /** Check if an ID exists (not expired) in the given scope. */
    fun has(id: String, scope: String): Boolean {
        val entry = store[id] ?: return false
        if (entry.expiresAt < clock()) {
            store.remove(id)
            return false
        }
        return entry.scope == scope
    }

    /** Check if an ID exists (not expired) in any scope. */
    fun hasAny(id: String): Boolean {
        val entry = store[id] ?: return false
        if (entry.expiresAt < clock()) {
            store.remove(id)
            return false
        }
        return true
    }

    /** Remove an ID from the cache. */
    fun remove(id: String) {
        store.remove(id)
    }

    /** Remove all expired entries. */
    fun prune() {
        val now = clock()
        val iter = store.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.expiresAt < now) iter.remove()
        }
    }

    /** Current number of non-expired entries. */
    val size: Int get() {
        prune()
        return store.size
    }

    fun clear() = store.clear()
}
