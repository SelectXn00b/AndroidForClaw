package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw module: memory-host-sdk
 * Source: OpenClaw/src/memory-host-sdk/engine.ts
 *
 * Facade that delegates to agent/memory/MemoryManager for persistence
 * and exposes a host-SDK-compatible API for external plugins.
 */
object MemoryHostEngine {

    private val listeners = mutableListOf<MemoryHostEventListener>()

    fun addEventListener(listener: MemoryHostEventListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: MemoryHostEventListener) {
        listeners.remove(listener)
    }

    private fun emit(event: MemoryHostEvent) {
        listeners.forEach { it(event) }
    }

    /** In-memory store keyed by "scope:key" */
    private val store = java.util.concurrent.ConcurrentHashMap<String, MemoryEntry>()

    private fun storageKey(key: String, scope: MemoryScope): String = "${scope.name}:$key"

    suspend fun write(key: String, value: String, scope: MemoryScope = MemoryScope.SESSION): MemoryWriteResult {
        val sk = storageKey(key, scope)
        val existing = store[sk]
        val now = System.currentTimeMillis()
        val entry = if (existing != null) {
            existing.copy(value = value, updatedAt = now)
        } else {
            MemoryEntry(
                id = java.util.UUID.randomUUID().toString(),
                key = key,
                value = value,
                scope = scope,
                createdAt = now,
                updatedAt = now
            )
        }
        store[sk] = entry
        emit(MemoryHostEvent.Written(entry))
        return MemoryWriteResult(id = entry.id, created = existing == null)
    }

    suspend fun read(key: String, scope: MemoryScope = MemoryScope.SESSION): MemoryEntry? {
        return store[storageKey(key, scope)]
    }

    suspend fun query(query: MemoryQuery): List<MemoryEntry> {
        var entries = store.values.asSequence()
        query.scope?.let { scope -> entries = entries.filter { it.scope == scope } }
        query.keyPrefix?.let { prefix -> entries = entries.filter { it.key.startsWith(prefix) } }
        val result = entries.drop(query.offset).take(query.limit).toList()
        emit(MemoryHostEvent.Queried(query, result.size))
        return result
    }

    suspend fun delete(key: String, scope: MemoryScope = MemoryScope.SESSION): Boolean {
        val sk = storageKey(key, scope)
        val removed = store.remove(sk)
        if (removed != null) {
            emit(MemoryHostEvent.Deleted(removed.id, key))
        }
        return removed != null
    }

    suspend fun clear(scope: MemoryScope) {
        val iter = store.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.scope == scope) {
                emit(MemoryHostEvent.Deleted(entry.value.id, entry.value.key))
                iter.remove()
            }
        }
    }
}
