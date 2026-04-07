package com.xiaomo.androidforclaw.memoryhostsdk

/**
 * OpenClaw module: memory-host-sdk
 * Source: OpenClaw/src/memory-host-sdk/types.ts
 */

data class MemoryEntry(
    val id: String,
    val key: String,
    val value: String,
    val scope: MemoryScope = MemoryScope.SESSION,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val metadata: Map<String, String> = emptyMap()
)

enum class MemoryScope { SESSION, AGENT, ACCOUNT, GLOBAL }

data class MemoryQuery(
    val scope: MemoryScope? = null,
    val keyPrefix: String? = null,
    val limit: Int = 100,
    val offset: Int = 0
)

data class MemoryWriteResult(
    val id: String,
    val created: Boolean
)
