package com.xiaomo.hermes.agent.memory

import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/search-manager.ts
 *
 * FTS5 keyword search for memory index.
 * Provides BM25-ranked full-text search with LIKE fallback.
 */

/**
 * Keyword search using FTS5 or LIKE fallback.
 */
internal class SearchManager(
    private val dbHelper: MemoryDbHelper,
    private val ftsAvailableProvider: () -> Boolean
) {
    companion object {
        private const val TAG = "SearchManager"
    }

    /**
     * FTS5 keyword search.
     */
    suspend fun searchKeyword(query: String, limit: Int): List<MemorySearchResult> = withContext(Dispatchers.IO) {
        val keywords = ChunkUtils.extractKeywords(query)
        if (keywords.isEmpty()) return@withContext emptyList()

        val ftsAvailable = ftsAvailableProvider()
        val db = dbHelper.readableDatabase
        val results = mutableListOf<MemorySearchResult>()

        if (!ftsAvailable) {
            // LIKE fallback
            val likeConditions = keywords.joinToString(" OR ") { "text LIKE ?" }
            val likeArgs = keywords.map { "%$it%" }.toTypedArray() + arrayOf(limit.toString())
            try {
                val cursor = db.rawQuery(
                    """SELECT path, source, start_line, end_line, text
                       FROM chunks WHERE $likeConditions LIMIT ?""",
                    likeArgs
                )
                while (cursor.moveToNext()) {
                    val text = cursor.getString(4)
                    val matchCount = keywords.count { text.contains(it, ignoreCase = true) }
                    results.add(MemorySearchResult(
                        path = cursor.getString(0),
                        source = cursor.getString(1),
                        startLine = cursor.getInt(2),
                        endLine = cursor.getInt(3),
                        text = text,
                        score = matchCount.toFloat() / keywords.size
                    ))
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "LIKE keyword search failed", e)
            }
            return@withContext results.sortedByDescending { it.score }
        }

        val ftsQuery = keywords.joinToString(" OR ")

        try {
            val cursor = db.rawQuery(
                """SELECT c.path, c.source, c.start_line, c.end_line, c.text,
                      bm25(chunks_fts) as rank
                   FROM chunks_fts f
                   JOIN chunks c ON c.rowid = f.rowid
                   WHERE chunks_fts MATCH ?
                   ORDER BY rank
                   LIMIT ?""",
                arrayOf(ftsQuery, limit.toString())
            )

            // Normalize BM25 scores to 0-1 range
            val rawResults = mutableListOf<Pair<MemorySearchResult, Double>>()
            while (cursor.moveToNext()) {
                val rank = cursor.getDouble(5) // BM25 returns negative scores (lower = better)
                rawResults.add(Pair(
                    MemorySearchResult(
                        path = cursor.getString(0),
                        source = cursor.getString(1),
                        startLine = cursor.getInt(2),
                        endLine = cursor.getInt(3),
                        text = cursor.getString(4),
                        score = 0f // will be normalized
                    ), rank
                ))
            }
            cursor.close()

            if (rawResults.isNotEmpty()) {
                val minRank = rawResults.minOf { it.second }
                val maxRank = rawResults.maxOf { it.second }
                val range = if (maxRank - minRank > 0) maxRank - minRank else 1.0

                for ((result, rank) in rawResults) {
                    // BM25: more negative = better match -> invert to 0-1
                    val normalized = ((rank - minRank) / range).toFloat()
                    val score = 1f - normalized  // invert: best match -> highest score
                    results.add(result.copy(score = score))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FTS5 search failed", e)
        }

        results
    }
}
