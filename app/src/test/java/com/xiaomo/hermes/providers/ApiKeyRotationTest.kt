package com.xiaomo.hermes.providers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApiKeyRotationTest {

    // ===== splitApiKeys =====

    @Test
    fun `null returns empty list`() {
        assertEquals(emptyList<String>(), ApiKeyRotation.splitApiKeys(null))
    }

    @Test
    fun `blank returns empty list`() {
        assertEquals(emptyList<String>(), ApiKeyRotation.splitApiKeys("  "))
    }

    @Test
    fun `single key`() {
        assertEquals(listOf("sk-abc"), ApiKeyRotation.splitApiKeys("sk-abc"))
    }

    @Test
    fun `comma separated keys`() {
        assertEquals(listOf("sk-1", "sk-2", "sk-3"), ApiKeyRotation.splitApiKeys("sk-1,sk-2,sk-3"))
    }

    @Test
    fun `keys are trimmed`() {
        assertEquals(listOf("sk-1", "sk-2"), ApiKeyRotation.splitApiKeys(" sk-1 , sk-2 "))
    }

    @Test
    fun `empty segments are filtered`() {
        assertEquals(listOf("sk-1", "sk-2"), ApiKeyRotation.splitApiKeys("sk-1,,sk-2,"))
    }

    @Test
    fun `duplicate keys are deduped`() {
        assertEquals(listOf("sk-1"), ApiKeyRotation.splitApiKeys("sk-1,sk-1,sk-1"))
    }

    // ===== dedupeApiKeys =====

    @Test
    fun `dedupe preserves order`() {
        assertEquals(listOf("a", "b", "c"), ApiKeyRotation.dedupeApiKeys(listOf("a", "b", "c", "a")))
    }

    @Test
    fun `dedupe all same returns single`() {
        assertEquals(listOf("x"), ApiKeyRotation.dedupeApiKeys(listOf("x", "x", "x")))
    }

    @Test
    fun `dedupe trims before comparing`() {
        assertEquals(listOf("k"), ApiKeyRotation.dedupeApiKeys(listOf(" k ", "k")))
    }

    @Test
    fun `dedupe drops empty strings`() {
        assertEquals(listOf("a"), ApiKeyRotation.dedupeApiKeys(listOf("", "a", "", " ")))
    }

    // ===== isApiKeyRateLimitError =====

    @Test
    fun `message containing 429 is rate limit`() {
        assertTrue(ApiKeyRotation.isApiKeyRateLimitError(Exception("HTTP 429 Too Many Requests")))
    }

    @Test
    fun `message containing rate limit is rate limit`() {
        assertTrue(ApiKeyRotation.isApiKeyRateLimitError(Exception("Rate limit exceeded")))
    }

    @Test
    fun `other exception is not rate limit`() {
        assertFalse(ApiKeyRotation.isApiKeyRateLimitError(Exception("Connection refused")))
    }

    @Test
    fun `null message is not rate limit`() {
        assertFalse(ApiKeyRotation.isApiKeyRateLimitError(Exception()))
    }

    // ===== executeWithApiKeyRotation =====

    @Test
    fun `first key succeeds immediately`() = runBlocking {
        val result = ApiKeyRotation.executeWithApiKeyRotation(
            apiKeys = listOf("key-1", "key-2"),
            provider = "test",
            execute = { apiKey -> "result-$apiKey" }
        )
        assertEquals("result-key-1", result)
    }

    @Test
    fun `rotates to next key on rate limit`() = runBlocking {
        var callCount = 0
        val result = ApiKeyRotation.executeWithApiKeyRotation(
            apiKeys = listOf("key-1", "key-2"),
            provider = "test",
            execute = { apiKey ->
                callCount++
                if (apiKey == "key-1") throw Exception("429 Too Many Requests")
                "result-$apiKey"
            }
        )
        assertEquals("result-key-2", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `throws last error when all keys exhausted`() = runBlocking {
        try {
            ApiKeyRotation.executeWithApiKeyRotation(
                apiKeys = listOf("key-1", "key-2"),
                provider = "test",
                execute = { throw Exception("429 rate limit") }
            )
            fail("Should have thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun `non-retryable error throws immediately`() = runBlocking {
        var callCount = 0
        try {
            ApiKeyRotation.executeWithApiKeyRotation(
                apiKeys = listOf("key-1", "key-2"),
                provider = "test",
                execute = {
                    callCount++
                    throw Exception("Invalid API key")
                }
            )
            fail("Should have thrown")
        } catch (e: Exception) {
            assertEquals(1, callCount)
            assertTrue(e.message!!.contains("Invalid"))
        }
    }

    @Test
    fun `empty keys throws LLMException`() = runBlocking {
        try {
            ApiKeyRotation.executeWithApiKeyRotation(
                apiKeys = emptyList(),
                provider = "test",
                execute = { "ok" }
            )
            fail("Should have thrown")
        } catch (e: LLMException) {
            assertTrue(e.message!!.contains("No API keys"))
        }
    }
}
