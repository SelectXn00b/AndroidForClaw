package com.xiaomo.androidforclaw.agent.memory

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.sync.Mutex
import java.io.File

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/sqlite.ts, sqlite-vec.ts, search-manager.ts, hybrid.ts
 *
 * Memory Index — facade over SQLite + FTS5 + vector search.
 * Delegates to MemorySqlite, MemoryVec, SearchManager, and HybridSearch.
 * Aligned with OpenClaw MemoryIndexManager.
 */
class MemoryIndex(
    context: Context,
    private val embeddingProvider: EmbeddingProvider?
) {
    companion object {
        private const val TAG = "MemoryIndex"

        // Re-export constants from HybridSearcher for backward compatibility
        const val DEFAULT_MAX_RESULTS = HybridSearcher.DEFAULT_MAX_RESULTS
        const val DEFAULT_MIN_SCORE = HybridSearcher.DEFAULT_MIN_SCORE
        const val DEFAULT_HYBRID_VECTOR_WEIGHT = HybridSearcher.DEFAULT_HYBRID_VECTOR_WEIGHT
        const val DEFAULT_HYBRID_TEXT_WEIGHT = HybridSearcher.DEFAULT_HYBRID_TEXT_WEIGHT
        const val DEFAULT_HYBRID_CANDIDATE_MULTIPLIER = HybridSearcher.DEFAULT_HYBRID_CANDIDATE_MULTIPLIER
        const val SNIPPET_MAX_CHARS = HybridSearcher.SNIPPET_MAX_CHARS
    }

    private val dbHelper = MemoryDbHelper(context)
    private val mutex = Mutex()
    var ftsAvailable = true
        private set

    // Delegates
    private val sqliteStore: MemorySqliteStore
    private val vecSearch: MemoryVecSearch
    private val searchManager: SearchManager
    private val hybridSearcher: HybridSearcher

    init {
        // Trigger DB creation and check FTS5 availability
        try {
            val db = dbHelper.writableDatabase
            // If DB already existed, probe FTS5 availability
            if (dbHelper.ftsCreated) {
                try {
                    db.rawQuery("SELECT * FROM chunks_fts LIMIT 0", null).close()
                } catch (e: Exception) {
                    ftsAvailable = false
                    Log.w(TAG, "FTS5 table not available", e)
                }
            } else {
                ftsAvailable = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DB", e)
        }

        sqliteStore = MemorySqliteStore(dbHelper, embeddingProvider, ftsAvailable, mutex)
        vecSearch = MemoryVecSearch(dbHelper)
        searchManager = SearchManager(dbHelper) { ftsAvailable }
        hybridSearcher = HybridSearcher(vecSearch, searchManager, embeddingProvider)
    }

    /** Backward-compatible type alias for search results. */
    @Deprecated("Use MemorySearchResult directly", ReplaceWith("MemorySearchResult"))
    data class SearchResult(
        val path: String,
        val source: String,
        val startLine: Int,
        val endLine: Int,
        val text: String,
        val score: Float
    )

    /**
     * Index a single file: chunk it, compute embeddings, store in DB.
     * Skips unchanged files (by hash).
     */
    suspend fun indexFile(file: File, source: String = "memory") =
        sqliteStore.indexFile(file, source)

    /**
     * Remove a file from the index.
     */
    suspend fun removeFile(path: String) =
        sqliteStore.removeFile(path)

    /**
     * Vector search using cosine similarity.
     */
    suspend fun searchVector(queryEmbedding: FloatArray, limit: Int): List<MemorySearchResult> =
        vecSearch.searchVector(queryEmbedding, limit)

    /**
     * FTS5 keyword search.
     */
    suspend fun searchKeyword(query: String, limit: Int): List<MemorySearchResult> =
        searchManager.searchKeyword(query, limit)

    /**
     * Hybrid search: vector + FTS5, merged.
     * Aligned with OpenClaw mergeHybridResults.
     */
    suspend fun hybridSearch(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        minScore: Float = DEFAULT_MIN_SCORE
    ): List<MemorySearchResult> =
        hybridSearcher.hybridSearch(query, maxResults, minScore)

    /**
     * Sync: index all files in the given list, remove stale entries.
     */
    suspend fun sync(files: List<File>, source: String = "memory") =
        sqliteStore.sync(files, source)
}
