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

    suspend fun write(key: String, value: String, scope: MemoryScope = MemoryScope.SESSION): MemoryWriteResult {
        TODO("Delegate to MemoryManager, emit Written event")
    }

    suspend fun read(key: String, scope: MemoryScope = MemoryScope.SESSION): MemoryEntry? {
        TODO("Delegate to MemoryManager")
    }

    suspend fun query(query: MemoryQuery): List<MemoryEntry> {
        TODO("Delegate to MemoryManager, emit Queried event")
    }

    suspend fun delete(key: String, scope: MemoryScope = MemoryScope.SESSION): Boolean {
        TODO("Delegate to MemoryManager, emit Deleted event")
    }

    suspend fun clear(scope: MemoryScope) {
        TODO("Delete all entries in scope")
    }
}
