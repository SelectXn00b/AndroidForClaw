package com.xiaomo.hermes.providers

import com.xiaomo.hermes.logging.Log

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/api-key-rotation.ts
 *
 * API key rotation — when a request fails with a rate limit error (429),
 * try the next API key from the comma-separated list.
 */
object ApiKeyRotation {

    private const val TAG = "ApiKeyRotation"

    /**
     * Deduplicate and trim API keys, dropping empty strings.
     */
    fun dedupeApiKeys(raw: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val keys = mutableListOf<String>()
        for (value in raw) {
            val key = value.trim()
            if (key.isEmpty() || key in seen) continue
            seen.add(key)
            keys.add(key)
        }
        return keys
    }

    /**
     * Split a possibly comma-separated API key string into a deduplicated list.
     */
    fun splitApiKeys(apiKey: String?): List<String> {
        if (apiKey.isNullOrBlank()) return emptyList()
        return dedupeApiKeys(apiKey.split(","))
    }

    /**
     * Execute a request with API key rotation.
     *
     * Tries each key sequentially. On rate limit (429), moves to the next key.
     * Non-retryable errors or key exhaustion rethrow the last error.
     *
     * @param apiKeys Deduplicated API keys to try
     * @param provider Provider name (for logging)
     * @param execute The actual request function that takes an API key
     * @param shouldRetry Predicate to check if an error is retryable (defaults to rate limit check)
     */
    suspend fun <T> executeWithApiKeyRotation(
        apiKeys: List<String>,
        provider: String,
        execute: suspend (apiKey: String) -> T,
        shouldRetry: (Exception) -> Boolean = ::isApiKeyRateLimitError
    ): T {
        val keys = dedupeApiKeys(apiKeys)
        if (keys.isEmpty()) {
            throw LLMException("No API keys configured for provider \"$provider\".")
        }

        var lastError: Exception? = null
        for ((attempt, apiKey) in keys.withIndex()) {
            try {
                return execute(apiKey)
            } catch (e: Exception) {
                lastError = e
                val retryable = shouldRetry(e)
                if (!retryable || attempt + 1 >= keys.size) {
                    break
                }
                Log.w(TAG, "API key #${attempt + 1} hit rate limit for $provider, rotating to next key")
            }
        }

        throw lastError ?: LLMException("Failed to run API request for $provider.")
    }

    /**
     * Check if an error indicates an API key rate limit (429).
     */
    fun isApiKeyRateLimitError(error: Exception): Boolean {
        val message = error.message?.lowercase() ?: ""
        return message.contains("429") || message.contains("rate limit")
    }
}
