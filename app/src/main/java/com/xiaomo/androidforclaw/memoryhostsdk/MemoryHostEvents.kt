package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw module: memory-host-sdk
 * Source: OpenClaw/src/memory-host-sdk/events.ts
 *
 * Event types emitted by the memory engine for observability.
 */

sealed class MemoryHostEvent {
    data class Written(val entry: MemoryEntry) : MemoryHostEvent()
    data class Deleted(val id: String, val key: String) : MemoryHostEvent()
    data class Queried(val query: MemoryQuery, val resultCount: Int) : MemoryHostEvent()
    data class Error(val operation: String, val message: String) : MemoryHostEvent()
}

typealias MemoryHostEventListener = (MemoryHostEvent) -> Unit
