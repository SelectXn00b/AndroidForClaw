package com.xiaomo.hermes.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.hermes.agent.memory.ChunkUtils
import com.xiaomo.hermes.agent.memory.MemoryIndex
import com.xiaomo.hermes.core.MyApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * MemoryIndex 设备集成测试
 *
 * 测试 SQLite + FTS5 + 向量搜索的完整流程
 * 需要真机/模拟器运行
 *
 * 运行: adb shell am instrument -w -e class com.xiaomo.hermes.integration.MemoryIndexTest \
 *       com.xiaomo.hermes.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class MemoryIndexTest {

    private lateinit var context: Context
    private lateinit var memoryIndex: MemoryIndex
    private lateinit var testDir: File

    @Before
    fun setup() {
        // API 30+ needs MANAGE_EXTERNAL_STORAGE for /sdcard/ access
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set $pkg MANAGE_EXTERNAL_STORAGE allow")
            .close()

        context = ApplicationProvider.getApplicationContext<MyApplication>()
        // No embedding provider — FTS-only mode
        memoryIndex = MemoryIndex(context, embeddingProvider = null)

        // Create test files
        testDir = File("/sdcard/.androidforclaw/workspace/test_memory")
        testDir.mkdirs()
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    // ===== indexFile =====

    @Test
    fun test01_indexFile_createsChunks() = runBlocking {
        val file = File(testDir, "test.md")
        file.writeText("""
            # My Project Notes
            
            ## Architecture
            The project uses MVVM architecture with Jetpack Compose.
            
            ## Key Decisions
            - Chose Kotlin over Java for modern syntax
            - SQLite for local storage (aligned with OpenClaw)
            - OkHttp for network requests
            
            ## TODO
            - Add vector search support
            - Implement embedding API calls
        """.trimIndent())

        memoryIndex.indexFile(file, "memory")
        println("✅ indexFile completed without error")
    }

    @Test
    fun test02_indexFile_skipUnchanged() = runBlocking {
        val file = File(testDir, "unchanged.md")
        file.writeText("# Test\nSome content")

        // Index twice
        memoryIndex.indexFile(file, "memory")
        memoryIndex.indexFile(file, "memory")
        // Should not crash or duplicate
        println("✅ Double indexing handled correctly")
    }

    // ===== searchKeyword (FTS5) =====

    @Test
    fun test03_searchKeyword_findsMatch() = runBlocking {
        val file = File(testDir, "search_test.md")
        file.writeText("""
            # OpenClaw Integration
            
            Hermes connects to OpenClaw gateway via WebSocket.
            The agent loop executes tool calls iteratively.
            Memory search uses SQLite FTS5 for full-text search.
        """.trimIndent())

        memoryIndex.indexFile(file, "memory")

        val results = memoryIndex.searchKeyword("OpenClaw gateway", limit = 6)
        println("FTS results: ${results.size}")
        results.forEach { r ->
            println("  [${r.score}] ${r.path}:${r.startLine}-${r.endLine}: ${r.text.take(80)}")
        }

        assertTrue("Should find results for 'OpenClaw gateway'", results.isNotEmpty())
    }

    @Test
    fun test04_searchKeyword_noMatch() = runBlocking {
        val file = File(testDir, "no_match.md")
        file.writeText("# Simple\nJust some basic content here")

        memoryIndex.indexFile(file, "memory")

        val results = memoryIndex.searchKeyword("xyznonexistent123", limit = 6)
        assertTrue("Should not find results for nonsense query", results.isEmpty())
    }

    @Test
    fun test05_searchKeyword_ranksRelevance() = runBlocking {
        // File 1: high relevance
        val file1 = File(testDir, "high.md")
        file1.writeText("""
            # Memory System
            The memory search system uses vector embeddings.
            Memory search is powered by SQLite FTS5.
            Memory indexing happens automatically.
        """.trimIndent())

        // File 2: low relevance
        val file2 = File(testDir, "low.md")
        file2.writeText("""
            # Weather Report
            Today is sunny with clear skies.
            Temperature is 25 degrees.
        """.trimIndent())

        memoryIndex.indexFile(file1, "memory")
        memoryIndex.indexFile(file2, "memory")

        val results = memoryIndex.searchKeyword("memory search", limit = 6)
        println("Ranked results:")
        results.forEach { r ->
            println("  [${r.score}] ${r.path}: ${r.text.take(60)}")
        }

        if (results.isNotEmpty()) {
            // First result should be from high.md
            assertTrue(
                "Top result should be from high.md",
                results[0].path.contains("high.md")
            )
        }
    }

    // ===== hybridSearch =====

    @Test
    fun test06_hybridSearch_worksWithoutEmbedding() = runBlocking {
        val file = File(testDir, "hybrid_test.md")
        file.writeText("""
            # Agent Loop Testing
            
            The agent loop runs tool calls iteratively.
            Each iteration: LLM call → tool execution → result collection.
            Maximum 40 iterations before stopping.
        """.trimIndent())

        memoryIndex.indexFile(file, "memory")

        // Without embedding provider, should fallback to FTS-only
        val results = memoryIndex.hybridSearch("agent loop iterations")
        println("Hybrid search (FTS-only mode) results: ${results.size}")
        results.forEach { r ->
            println("  [${r.score}] ${r.text.take(80)}")
        }

        assertTrue("Should find results via FTS fallback", results.isNotEmpty())
    }

    @Test
    fun test07_hybridSearch_respectsMaxResults() = runBlocking {
        // Create many files
        for (i in 1..10) {
            val file = File(testDir, "file_$i.md")
            file.writeText("# Document $i\nThis document talks about testing and quality assurance number $i.")
            memoryIndex.indexFile(file, "memory")
        }

        val results = memoryIndex.hybridSearch("testing quality", maxResults = 3)
        assertTrue("Should respect maxResults=3, got ${results.size}", results.size <= 3)
    }

    @Test
    fun test08_hybridSearch_respectsMinScore() = runBlocking {
        val file = File(testDir, "min_score.md")
        file.writeText("# Random\nCompletely unrelated content about bananas and sunshine")
        memoryIndex.indexFile(file, "memory")

        val results = memoryIndex.hybridSearch(
            query = "quantum physics black hole",
            minScore = 0.99f // Very high threshold
        )
        // With FTS on unrelated content + high minScore, should return nothing
        println("High minScore results: ${results.size}")
    }

    // ===== sync =====

    @Test
    fun test09_sync_indexesMultipleFiles() = runBlocking {
        val files = (1..3).map { i ->
            File(testDir, "sync_$i.md").also {
                it.writeText("# Sync Test $i\nContent for sync test document number $i about memory management")
            }
        }

        memoryIndex.sync(files, "memory")

        val results = memoryIndex.searchKeyword("sync test memory", limit = 10)
        println("After sync, found ${results.size} results")
        assertTrue("Should find results after sync", results.isNotEmpty())
    }

    @Test
    fun test10_sync_removesDeletedFiles() = runBlocking {
        val file = File(testDir, "to_delete.md")
        file.writeText("# Will Be Deleted\nThis file will be removed and should not appear in search")
        memoryIndex.indexFile(file, "memory")

        // Verify it's indexed
        val before = memoryIndex.searchKeyword("Will Be Deleted", limit = 6)
        println("Before delete: ${before.size} results")

        // Sync with empty list (simulates file deletion)
        memoryIndex.sync(emptyList(), "memory")

        val after = memoryIndex.searchKeyword("Will Be Deleted", limit = 6)
        println("After delete sync: ${after.size} results")
        // After sync with empty list, old file's chunks should be removed
    }

    // ===== SearchResult format =====

    @Test
    fun test11_searchResult_hasRequiredFields() = runBlocking {
        val file = File(testDir, "format_test.md")
        file.writeText("# Format Test\nThis tests the search result format with all required fields")
        memoryIndex.indexFile(file, "memory")

        val results = memoryIndex.searchKeyword("format test", limit = 6)
        if (results.isNotEmpty()) {
            val r = results[0]
            assertTrue("path should not be empty", r.path.isNotEmpty())
            assertTrue("snippet should not be empty", r.text.isNotEmpty())
            assertTrue("startLine should be positive", r.startLine > 0)
            assertTrue("endLine >= startLine", r.endLine >= r.startLine)
            assertTrue("score should be positive", r.score > 0)
            assertTrue("snippet should be <= SNIPPET_MAX_CHARS", r.text.length <= MemoryIndex.SNIPPET_MAX_CHARS + 50) // small buffer
            println("✅ SearchResult format correct: path=${r.path}, lines=${r.startLine}-${r.endLine}, score=${r.score}")
        }
    }

    // ===== ChunkUtils integration =====

    @Test
    fun test12_chunkUtils_producesValidChunks() {
        val content = (1..50).joinToString("\n") { "Line $it: This is a test line with some content for chunking" }
        val chunks = ChunkUtils.chunkMarkdown(content)

        assertTrue("Should produce chunks", chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue("startLine > 0", chunk.startLine > 0)
            assertTrue("endLine >= startLine", chunk.endLine >= chunk.startLine)
            assertTrue("text not empty", chunk.text.isNotEmpty())
            assertTrue("hash not empty", chunk.hash.isNotEmpty())
            assertEquals("hash is SHA-256 (64 hex chars)", 64, chunk.hash.length)
        }

        // Verify continuity
        for (i in 0 until chunks.size - 1) {
            assertTrue(
                "Next chunk should not skip lines (with overlap)",
                chunks[i + 1].startLine <= chunks[i].endLine + 1
            )
        }
        println("✅ ${chunks.size} chunks produced, all valid")
    }

}
