package com.xiaomo.androidforclaw.websearch

/**
 * OpenClaw module: web-search
 * Source: OpenClaw/src/web-search/runtime.ts
 *
 * Web search tool runtime that selects a search provider (Brave, Google,
 * Tavily, DuckDuckGo, etc.) and executes search queries.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object WebSearchRuntime {

    fun resolveWebSearchEnabled(
        searchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        TODO("Resolve web search enabled state")
    }

    fun listWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        TODO("List available web search providers")
    }

    fun listConfiguredWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        TODO("List configured web search providers")
    }

    fun resolveWebSearchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        TODO("Resolve which web search provider to use")
    }

    fun resolveWebSearchDefinition(
        config: OpenClawConfig? = null
    ): WebSearchDefinitionResult? {
        TODO("Resolve and create the active web search tool definition")
    }

    suspend fun runWebSearch(query: String, config: OpenClawConfig? = null): WebSearchResult {
        TODO("Execute web search through resolved provider")
    }
}
