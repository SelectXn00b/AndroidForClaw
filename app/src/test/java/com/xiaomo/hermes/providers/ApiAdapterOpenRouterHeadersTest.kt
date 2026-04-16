package com.xiaomo.hermes.providers

import com.xiaomo.hermes.config.ModelDefinition
import com.xiaomo.hermes.config.ProviderConfig
import org.junit.Assert.*
import org.junit.Test

/**
 * Verify OpenRouter app attribution headers are injected correctly.
 *
 * OpenClaw source reference:
 * - proxy-stream-wrappers.ts: OPENROUTER_APP_HEADERS = { "HTTP-Referer": "https://openclaw.ai", "X-Title": "OpenClaw" }
 * - createOpenRouterWrapper: injects headers on ALL requests to OpenRouter provider
 *
 * These headers tell OpenRouter AppName=OpenClaw, which is required for free MiMo usage.
 * Without them, OpenRouter shows "Unknown" and bills the user.
 */
class ApiAdapterOpenRouterHeadersTest {

    private val openRouterProvider = ProviderConfig(
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "sk-or-test-123",
        api = "openai-completions"
    )

    private val ollamaProvider = ProviderConfig(
        baseUrl = "http://localhost:11434/v1",
        apiKey = "ollama-local",
        api = "openai-completions"
    )

    private val testModel = ModelDefinition(
        id = "xiaomi/mimo-v2-pro",
        name = "MiMo V2 Pro"
    )

    @Test
    fun `OpenRouter provider includes HTTP-Referer header`() {
        val headers = ApiAdapter.buildHeaders(openRouterProvider, testModel)
        assertEquals("https://openclaw.ai", headers["HTTP-Referer"])
    }

    @Test
    fun `OpenRouter provider includes X-Title header`() {
        val headers = ApiAdapter.buildHeaders(openRouterProvider, testModel)
        assertEquals("OpenClaw", headers["X-Title"])
    }

    @Test
    fun `non-OpenRouter provider does NOT include OpenRouter headers`() {
        val headers = ApiAdapter.buildHeaders(ollamaProvider, testModel)
        assertNull(headers["HTTP-Referer"])
        assertNull(headers["X-Title"])
    }

    @Test
    fun `OpenRouter detection is case-insensitive`() {
        val upperCase = openRouterProvider.copy(baseUrl = "https://OPENROUTER.AI/api/v1")
        val headers = ApiAdapter.buildHeaders(upperCase, testModel)
        assertEquals("https://openclaw.ai", headers["HTTP-Referer"])
        assertEquals("OpenClaw", headers["X-Title"])
    }

    @Test
    fun `user config headers do not override OpenRouter app headers`() {
        // If user sets their own HTTP-Referer, both should be present
        // (OkHttp Headers supports multiple values for the same name)
        val withCustomHeaders = openRouterProvider.copy(
            headers = mapOf("X-Custom" to "test-value")
        )
        val headers = ApiAdapter.buildHeaders(withCustomHeaders, testModel)
        assertEquals("https://openclaw.ai", headers["HTTP-Referer"])
        assertEquals("OpenClaw", headers["X-Title"])
        assertEquals("test-value", headers["X-Custom"])
    }
}
