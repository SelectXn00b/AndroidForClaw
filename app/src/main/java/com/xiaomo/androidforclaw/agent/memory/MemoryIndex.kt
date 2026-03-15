package com.xiaomo.androidforclaw.agent.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Memory Index — SQLite + FTS5 + vector search.
 * Aligned with OpenClaw MemoryIndexManager.
 */
class MemoryIndex(
    context: Context,
    private val embeddingProvider: EmbeddingProvider?
) {
    companion object {
        private const val TAG = "MemoryIndex"
        private const val DB_NAME = "memory_index.db"
        private const val DB_VERSION = 1

        // OpenClaw constants
        const val DEFAULT_MAX_RESULTS = 6
        const val DEFAULT_MIN_SCORE = 0.35f
        const val DEFAULT_HYBRID_VECTOR_WEIGHT = 0.7f
        const val DEFAULT_HYBRID_TEXT_WEIGHT = 0.3f
        const val DEFAULT_HYBRID_CANDIDATE_MULTIPLIER = 4
        const val SNIPPET_MAX_CHARS = 700
    }

    private val dbHelper = MemoryDbHelper(context)
    private val mutex = Mutex()
    var ftsAvailable = true
        private set

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
    }

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
    suspend fun indexFile(file: File, source: String = "memory") = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = file.absolutePath
                val content = file.readText()
                val fileHash = ChunkUtils.hashText(content)

                val db = dbHelper.writableDatabase

                // Check if file unchanged
                val cursor = db.rawQuery(
                    "SELECT hash FROM files WHERE path = ?", arrayOf(path)
                )
                val existingHash = if (cursor.moveToFirst()) cursor.getString(0) else null
                cursor.close()

                if (existingHash == fileHash) {
                    return@withContext // File unchanged
                }

                // Delete old chunks for this file
                if (ftsAvailable) {
                    db.execSQL("DELETE FROM chunks_fts WHERE rowid IN (SELECT rowid FROM chunks WHERE path = ?)", arrayOf(path))
                }
                db.delete("chunks", "path = ?", arrayOf(path))

                // Chunk the file
                val chunks = ChunkUtils.chunkMarkdown(content)
                if (chunks.isEmpty()) return@withContext

                // Get embeddings (batch)
                val embeddings = embeddingProvider?.embedBatch(chunks.map { it.text })

                // Insert chunks
                val stmt = db.compileStatement(
                    "INSERT INTO chunks (source, path, start_line, end_line, text, hash, embedding, indexed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                )

                db.beginTransaction()
                try {
                    for ((idx, chunk) in chunks.withIndex()) {
                        stmt.clearBindings()
                        stmt.bindString(1, source)
                        stmt.bindString(2, path)
                        stmt.bindLong(3, chunk.startLine.toLong())
                        stmt.bindLong(4, chunk.endLine.toLong())
                        stmt.bindString(5, chunk.text)
                        stmt.bindString(6, chunk.hash)

                        val embedding = embeddings?.getOrNull(idx)
                        if (embedding != null) {
                            stmt.bindBlob(7, floatArrayToBlob(embedding))
                        } else {
                            stmt.bindNull(7)
                        }
                        stmt.bindLong(8, System.currentTimeMillis())
                        stmt.executeInsert()
                    }

                    // Update FTS index
                    if (ftsAvailable) {
                        db.execSQL(
                            "INSERT INTO chunks_fts(rowid, text) SELECT rowid, text FROM chunks WHERE path = ?",
                            arrayOf(path)
                        )
                    }

                    // Upsert file record
                    db.execSQL(
                        "INSERT OR REPLACE INTO files (path, source, size, mtime, hash) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(path, source, file.length(), file.lastModified(), fileHash)
                    )

                    db.setTransactionSuccessful()
                    Log.d(TAG, "Indexed $path: ${chunks.size} chunks")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index file: ${file.absolutePath}", e)
            }
        }
    }

    /**
     * Remove a file from the index.
     */
    suspend fun removeFile(path: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (ftsAvailable) {
                db.execSQL("DELETE FROM chunks_fts WHERE rowid IN (SELECT rowid FROM chunks WHERE path = ?)", arrayOf(path))
            }
            db.delete("chunks", "path = ?", arrayOf(path))
            db.delete("files", "path = ?", arrayOf(path))
        }
    }

    /**
     * Vector search using cosine similarity.
     */
    suspend fun searchVector(queryEmbedding: FloatArray, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val results = mutableListOf<SearchResult>()

        val cursor = db.rawQuery(
            "SELECT path, source, start_line, end_line, text, embedding FROM chunks WHERE embedding IS NOT NULL",
            null
        )

        while (cursor.moveToNext()) {
            val embedding = blobToFloatArray(cursor.getBlob(5))
            val score = cosineSimilarity(queryEmbedding, embedding)
            results.add(SearchResult(
                path = cursor.getString(0),
                source = cursor.getString(1),
                startLine = cursor.getInt(2),
                endLine = cursor.getInt(3),
                text = cursor.getString(4),
                score = score
            ))
        }
        cursor.close()

        results.sortByDescending { it.score }
        results.take(limit)
    }

    /**
     * FTS5 keyword search.
     */
    suspend fun searchKeyword(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val keywords = ChunkUtils.extractKeywords(query)
        if (keywords.isEmpty()) return@withContext emptyList()

        val db = dbHelper.readableDatabase
        val results = mutableListOf<SearchResult>()

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
                    results.add(SearchResult(
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
            val rawResults = mutableListOf<Pair<SearchResult, Double>>()
            while (cursor.moveToNext()) {
                val rank = cursor.getDouble(5) // BM25 returns negative scores (lower = better)
                rawResults.add(Pair(
                    SearchResult(
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
                    // BM25: more negative = better match → invert to 0-1
                    val normalized = ((rank - minRank) / range).toFloat()
                    val score = 1f - normalized  // invert: best match → highest score
                    results.add(result.copy(score = score))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FTS5 search failed", e)
        }

        results
    }

    /**
     * Hybrid search: vector + FTS5, merged.
     * Aligned with OpenClaw mergeHybridResults.
     */
    suspend fun hybridSearch(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        minScore: Float = DEFAULT_MIN_SCORE
    ): List<SearchResult> {
        val candidateLimit = maxResults * DEFAULT_HYBRID_CANDIDATE_MULTIPLIER

        // Vector search
        val vectorResults = if (embeddingProvider?.isAvailable == true) {
            val queryEmbedding = embeddingProvider.embed(query)
            if (queryEmbedding != null) {
                searchVector(queryEmbedding, candidateLimit)
            } else emptyList()
        } else emptyList()

        // Keyword search
        val keywordResults = searchKeyword(query, candidateLimit)

        // If no vector results, use keyword only
        if (vectorResults.isEmpty()) {
            return keywordResults
                .filter { it.score >= minScore }
                .take(maxResults)
        }

        // Merge: deduplicate by (path, startLine) and combine scores
        val merged = mutableMapOf<String, SearchResult>()

        for (r in vectorResults) {
            val key = "${r.path}:${r.startLine}"
            val existing = merged[key]
            val vectorScore = r.score * DEFAULT_HYBRID_VECTOR_WEIGHT
            merged[key] = if (existing != null) {
                existing.copy(score = existing.score + vectorScore)
            } else {
                r.copy(score = vectorScore)
            }
        }

        for (r in keywordResults) {
            val key = "${r.path}:${r.startLine}"
            val existing = merged[key]
            val textScore = r.score * DEFAULT_HYBRID_TEXT_WEIGHT
            merged[key] = if (existing != null) {
                existing.copy(score = existing.score + textScore)
            } else {
                r.copy(score = textScore)
            }
        }

        return merged.values
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(maxResults)
    }

    /**
     * Sync: index all files in the given list, remove stale entries.
     */
    suspend fun sync(files: List<File>, source: String = "memory") {
        val currentPaths = files.map { it.absolutePath }.toSet()

        // Index each file
        for (file in files) {
            if (file.exists() && file.isFile) {
                indexFile(file, source)
            }
        }

        // Remove stale files from index
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT DISTINCT path FROM files WHERE source = ?", arrayOf(source))
            val indexedPaths = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indexedPaths.add(cursor.getString(0))
            }
            cursor.close()

            for (path in indexedPaths) {
                if (path !in currentPaths) {
                    removeFile(path)
                }
            }
        }
    }

    // ---- Utility functions ----

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var magA = 0f; var magB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]; magA += a[i] * a[i]; magB += b[i] * b[i]
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom < 1e-10f) 0f else dot / denom
    }

    private fun floatArrayToBlob(arr: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in arr) buf.putFloat(v)
        return buf.array()
    }

    private fun blobToFloatArray(blob: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(blob.size / 4) { buf.getFloat() }
    }

    // ---- SQLite Helper ----

    private class MemoryDbHelper(context: Context) : SQLiteOpenHelper(
        context, DB_NAME, null, DB_VERSION
    ) {
        var ftsCreated = true
            private set

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS files (
                    path TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    size INTEGER,
                    mtime INTEGER,
                    hash TEXT
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source TEXT NOT NULL,
                    path TEXT NOT NULL,
                    start_line INTEGER NOT NULL,
                    end_line INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    hash TEXT NOT NULL,
                    embedding BLOB,
                    indexed_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_path ON chunks(path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_hash ON chunks(hash)")
            try {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(text, content='')")
            } catch (e: Exception) {
                Log.w(TAG, "FTS5 not available on this device, falling back to LIKE search", e)
                ftsCreated = false
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS chunks_fts")
            db.execSQL("DROP TABLE IF EXISTS chunks")
            db.execSQL("DROP TABLE IF EXISTS files")
            onCreate(db)
        }
    }
}
