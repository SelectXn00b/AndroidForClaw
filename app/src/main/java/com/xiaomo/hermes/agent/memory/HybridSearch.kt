package com.xiaomo.hermes.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/hybrid.ts
 *
 * Hybrid search: combines vector + FTS5 keyword results.
 * Aligned with OpenClaw mergeHybridResults.
 */

/**
 * Shared search result type used across all search strategies.
 */
data class MemorySearchResult(
    val path: String,
    val source: String,
    val startLine: Int,
    val endLine: Int,
    val text: String,
    val score: Float
)

/**
 * Hybrid search combining vector and keyword search with weighted score merging.
 */
internal class HybridSearcher(
    private val vecSearch: MemoryVecSearch,
    private val searchManager: SearchManager,
    private val embeddingProvider: EmbeddingProvider?
) {
    companion object {
        // OpenClaw constants
        const val DEFAULT_MAX_RESULTS = 6
        const val DEFAULT_MIN_SCORE = 0.35f
        const val DEFAULT_HYBRID_VECTOR_WEIGHT = 0.7f
        const val DEFAULT_HYBRID_TEXT_WEIGHT = 0.3f
        const val DEFAULT_HYBRID_CANDIDATE_MULTIPLIER = 4
        const val SNIPPET_MAX_CHARS = 700
    }

    /**
     * Hybrid search: vector + FTS5, merged.
     * Aligned with OpenClaw mergeHybridResults.
     */
    suspend fun hybridSearch(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        minScore: Float = DEFAULT_MIN_SCORE
    ): List<MemorySearchResult> {
        val candidateLimit = maxResults * DEFAULT_HYBRID_CANDIDATE_MULTIPLIER

        // Vector search
        val vectorResults = if (embeddingProvider?.isAvailable == true) {
            val queryEmbedding = embeddingProvider.embed(query)
            if (queryEmbedding != null) {
                vecSearch.searchVector(queryEmbedding, candidateLimit)
            } else emptyList()
        } else emptyList()

        // Keyword search
        val keywordResults = searchManager.searchKeyword(query, candidateLimit)

        // If no vector results, use keyword only
        if (vectorResults.isEmpty()) {
            return keywordResults
                .filter { it.score >= minScore }
                .take(maxResults)
        }

        // Merge: deduplicate by (path, startLine) and combine scores
        val merged = mutableMapOf<String, MemorySearchResult>()

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
}
