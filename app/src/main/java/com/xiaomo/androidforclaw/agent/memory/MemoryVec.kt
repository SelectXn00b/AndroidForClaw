package com.xiaomo.androidforclaw.agent.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/sqlite-vec.ts
 *
 * Vector search and embedding utilities for memory index.
 * Handles cosine similarity computation and vector-based chunk retrieval.
 */

/**
 * Vector search over stored chunk embeddings.
 */
internal class MemoryVecSearch(
    private val dbHelper: MemoryDbHelper
) {
    /**
     * Vector search using cosine similarity.
     */
    suspend fun searchVector(queryEmbedding: FloatArray, limit: Int): List<MemorySearchResult> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val results = mutableListOf<MemorySearchResult>()

        val cursor = db.rawQuery(
            "SELECT path, source, start_line, end_line, text, embedding FROM chunks WHERE embedding IS NOT NULL",
            null
        )

        while (cursor.moveToNext()) {
            val embedding = blobToFloatArray(cursor.getBlob(5))
            val score = cosineSimilarity(queryEmbedding, embedding)
            results.add(MemorySearchResult(
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
}

// ---- Vector Utility Functions ----

internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0f
    var dot = 0f; var magA = 0f; var magB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]; magA += a[i] * a[i]; magB += b[i] * b[i]
    }
    val denom = sqrt(magA) * sqrt(magB)
    return if (denom < 1e-10f) 0f else dot / denom
}

internal fun floatArrayToBlob(arr: FloatArray): ByteArray {
    val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (v in arr) buf.putFloat(v)
    return buf.array()
}

internal fun blobToFloatArray(blob: ByteArray): FloatArray {
    val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(blob.size / 4) { buf.getFloat() }
}
