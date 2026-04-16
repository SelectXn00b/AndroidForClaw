package com.xiaomo.hermes.agent.memory

import org.junit.Assert.*
import org.junit.Test

/**
 * ChunkUtils 单元测试 — 对齐 OpenClaw chunkMarkdown 实现
 */
class ChunkUtilsTest {

    // ===== 常量对齐 =====

    @Test
    fun `default chunk tokens is 400`() {
        val field = ChunkUtils::class.java.getDeclaredField("DEFAULT_CHUNK_TOKENS")
        field.isAccessible = true
        assertEquals(400, field.getInt(null))
    }

    @Test
    fun `default chunk overlap is 80`() {
        val field = ChunkUtils::class.java.getDeclaredField("DEFAULT_CHUNK_OVERLAP")
        field.isAccessible = true
        assertEquals(80, field.getInt(null))
    }

    // ===== chunkMarkdown =====

    @Test
    fun `empty content returns minimal chunks`() {
        val chunks = ChunkUtils.chunkMarkdown("")
        // "".lines() = [""], so may produce 1 chunk with empty text
        assertTrue("Should produce 0 or 1 chunk", chunks.size <= 1)
    }

    @Test
    fun `single short line returns one chunk`() {
        val chunks = ChunkUtils.chunkMarkdown("Hello world")
        assertEquals(1, chunks.size)
        assertEquals("Hello world", chunks[0].text)
        assertEquals(1, chunks[0].startLine)
        assertEquals(1, chunks[0].endLine)
    }

    @Test
    fun `chunk records correct line numbers`() {
        val content = (1..5).joinToString("\n") { "Line $it" }
        val chunks = ChunkUtils.chunkMarkdown(content)
        assertTrue(chunks.isNotEmpty())
        assertEquals(1, chunks.first().startLine)
    }

    @Test
    fun `long content produces multiple chunks`() {
        // 400 tokens * 4 = 1600 chars per chunk
        // Create content > 1600 chars
        val content = (1..100).joinToString("\n") { "This is line number $it with some extra content to make it longer" }
        val chunks = ChunkUtils.chunkMarkdown(content)
        assertTrue("Should produce multiple chunks, got ${chunks.size}", chunks.size > 1)
    }

    @Test
    fun `chunks have overlap`() {
        // Create enough content for multiple chunks
        val content = (1..100).joinToString("\n") { "Line $it: some text content here for testing overlap behavior" }
        val chunks = ChunkUtils.chunkMarkdown(content)
        if (chunks.size >= 2) {
            // With overlap, chunk N+1 should start before chunk N ends
            val firstEnd = chunks[0].endLine
            val secondStart = chunks[1].startLine
            assertTrue(
                "Chunks should overlap: first ends at $firstEnd, second starts at $secondStart",
                secondStart <= firstEnd
            )
        }
    }

    @Test
    fun `chunk text is not empty`() {
        val content = "# Header\n\nSome content\n\nMore content"
        val chunks = ChunkUtils.chunkMarkdown(content)
        chunks.forEach { chunk ->
            assertTrue("Chunk text should not be blank", chunk.text.isNotBlank())
        }
    }

    @Test
    fun `chunk hash is consistent`() {
        val content = "Hello world"
        val chunks1 = ChunkUtils.chunkMarkdown(content)
        val chunks2 = ChunkUtils.chunkMarkdown(content)
        assertEquals(chunks1[0].hash, chunks2[0].hash)
    }

    @Test
    fun `different content produces different hashes`() {
        val chunks1 = ChunkUtils.chunkMarkdown("Hello")
        val chunks2 = ChunkUtils.chunkMarkdown("World")
        assertNotEquals(chunks1[0].hash, chunks2[0].hash)
    }

    @Test
    fun `custom tokens and overlap`() {
        val content = (1..50).joinToString("\n") { "Line $it with content" }
        val smallChunks = ChunkUtils.chunkMarkdown(content, tokens = 100, overlap = 20)
        val largeChunks = ChunkUtils.chunkMarkdown(content, tokens = 800, overlap = 100)
        assertTrue(
            "Smaller token budget should produce more chunks: small=${smallChunks.size} large=${largeChunks.size}",
            smallChunks.size >= largeChunks.size
        )
    }

    // ===== extractKeywords =====

    @Test
    fun `extractKeywords removes short words`() {
        val keywords = ChunkUtils.extractKeywords("I am a big fan of AI")
        assertFalse("Should not contain 'I'", keywords.contains("i"))
        assertFalse("Should not contain 'am'", keywords.contains("am"))
        assertFalse("Should not contain 'a'", keywords.contains("a"))
        assertFalse("Should not contain 'of'", keywords.contains("of"))
    }

    @Test
    fun `extractKeywords returns lowercase`() {
        val keywords = ChunkUtils.extractKeywords("OpenClaw Android Memory")
        keywords.forEach { kw ->
            assertEquals("Keyword should be lowercase: $kw", kw.lowercase(), kw)
        }
    }

    @Test
    fun `extractKeywords handles empty input`() {
        val keywords = ChunkUtils.extractKeywords("")
        assertTrue(keywords.isEmpty())
    }

    @Test
    fun `extractKeywords removes punctuation`() {
        val keywords = ChunkUtils.extractKeywords("hello, world! test.")
        keywords.forEach { kw ->
            assertFalse("Should not contain punctuation: $kw", kw.contains(Regex("[,!.]")))
        }
    }

    @Test
    fun `extractKeywords handles Chinese text`() {
        val keywords = ChunkUtils.extractKeywords("Android 开发 内存管理")
        assertTrue("Should have keywords", keywords.isNotEmpty())
    }

    // ===== hashText =====

    @Test
    fun `hashText returns consistent SHA-256`() {
        val hash1 = ChunkUtils.hashText("test")
        val hash2 = ChunkUtils.hashText("test")
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 hex = 64 chars
    }

    @Test
    fun `hashText different input different output`() {
        assertNotEquals(ChunkUtils.hashText("a"), ChunkUtils.hashText("b"))
    }
}
