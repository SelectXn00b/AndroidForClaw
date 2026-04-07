package com.xiaomo.androidforclaw.websearch

/**
 * OpenClaw module: web-search
 * Source: OpenClaw/src/web-search/runtime.ts
 *
 * Web search tool runtime that selects a search provider (Brave, Google,
 * Tavily, DuckDuckGo, etc.) and executes search queries.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.web.resolveWebProviderDefinition
import com.xiaomo.androidforclaw.web.hasWebProviderEntryCredential

object WebSearchRuntime {

    /** Known web search provider IDs */
    private val SEARCH_PROVIDER_IDS = listOf("brave", "google", "tavily", "duckduckgo")

    fun resolveWebSearchEnabled(
        searchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        if (sandboxed == true) {
            return searchConfig?.get("enabled") == true
        }
        return searchConfig?.get("enabled") != false
    }

    fun listWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        return SEARCH_PROVIDER_IDS.mapNotNull { id ->
            val def = resolveWebProviderDefinition(id, config)
            if (def != null) {
                val configured = if (def.envKey != null) hasWebProviderEntryCredential(def.envKey) else true
                WebSearchProviderEntry(id = id, label = def.label, configured = configured)
            } else {
                WebSearchProviderEntry(id = id, label = id, configured = false)
            }
        }
    }

    fun listConfiguredWebSearchProviders(config: OpenClawConfig? = null): List<WebSearchProviderEntry> {
        return listWebSearchProviders(config).filter { it.configured }
    }

    fun resolveWebSearchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        if (preferredId != null) {
            val providers = listConfiguredWebSearchProviders(config)
            if (providers.any { it.id == preferredId }) return preferredId
        }
        return listConfiguredWebSearchProviders(config).firstOrNull()?.id
    }

    fun resolveWebSearchDefinition(
        config: OpenClawConfig? = null
    ): WebSearchDefinitionResult? {
        val providerId = resolveWebSearchProviderId(config) ?: return null
        return WebSearchDefinitionResult(
            providerId = providerId,
            enabled = true
        )
    }

    suspend fun runWebSearch(query: String, config: OpenClawConfig? = null): WebSearchResult {
        val providerId = resolveWebSearchProviderId(config)
            ?: throw IllegalStateException("No web search provider configured")
        // Stub: real implementation would delegate to the resolved provider's API.
        // Returns empty results for now as actual API integration is provider-specific.
        return WebSearchResult(
            query = query,
            results = emptyList(),
            providerId = providerId
        )
    }
}
