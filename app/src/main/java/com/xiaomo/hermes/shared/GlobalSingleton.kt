package com.xiaomo.hermes.shared

import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/global-singleton.ts
 *
 * Process-scoped singleton registry. Uses a ConcurrentHashMap instead of globalThis.
 */

object GlobalSingleton {
    private val store = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(key: String, create: () -> T): T {
        return store.getOrPut(key) { create() } as T
    }

    fun <K, V> resolveMap(key: String): MutableMap<K, V> {
        return resolve(key) { ConcurrentHashMap<K, V>() }
    }

    fun <T> resolveSet(key: String): MutableSet<T> {
        return resolve(key) { ConcurrentHashMap.newKeySet<T>() }
    }

    fun clear() = store.clear()
}

/**
 * Process-scoped map keyed by string.
 * Aligned with TS resolveProcessScopedMap().
 */
fun <T> resolveProcessScopedMap(key: String): MutableMap<String, T> {
    return GlobalSingleton.resolveMap(key)
}
