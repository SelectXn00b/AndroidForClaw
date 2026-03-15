package com.xiaomo.androidforclaw.agent.memory

import org.junit.Assert.*
import org.junit.Test

/**
 * Memory 系统常量对齐 OpenClaw 验证
 */
class MemoryConstantsTest {

    @Test
    fun `MemoryIndex DEFAULT_MAX_RESULTS matches OpenClaw`() {
        assertEquals(6, MemoryIndex.DEFAULT_MAX_RESULTS)
    }

    @Test
    fun `MemoryIndex DEFAULT_MIN_SCORE matches OpenClaw`() {
        assertEquals(0.35f, MemoryIndex.DEFAULT_MIN_SCORE)
    }

    @Test
    fun `MemoryIndex DEFAULT_HYBRID_VECTOR_WEIGHT matches OpenClaw`() {
        assertEquals(0.7f, MemoryIndex.DEFAULT_HYBRID_VECTOR_WEIGHT)
    }

    @Test
    fun `MemoryIndex DEFAULT_HYBRID_TEXT_WEIGHT matches OpenClaw`() {
        assertEquals(0.3f, MemoryIndex.DEFAULT_HYBRID_TEXT_WEIGHT)
    }

    @Test
    fun `MemoryIndex DEFAULT_HYBRID_CANDIDATE_MULTIPLIER matches OpenClaw`() {
        assertEquals(4, MemoryIndex.DEFAULT_HYBRID_CANDIDATE_MULTIPLIER)
    }

    @Test
    fun `MemoryIndex SNIPPET_MAX_CHARS matches OpenClaw`() {
        assertEquals(700, MemoryIndex.SNIPPET_MAX_CHARS)
    }

    @Test
    fun `vector and text weights sum to 1`() {
        val sum = MemoryIndex.DEFAULT_HYBRID_VECTOR_WEIGHT + MemoryIndex.DEFAULT_HYBRID_TEXT_WEIGHT
        assertEquals(1.0f, sum, 0.001f)
    }
}
