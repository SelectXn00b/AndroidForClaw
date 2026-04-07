package com.xiaomo.androidforclaw.webfetch

/**
 * OpenClaw module: web-fetch
 * Source: OpenClaw/src/web-fetch/runtime.ts
 *
 * Web fetch tool runtime that selects and configures a web content fetching
 * provider (e.g., Firecrawl, Jina) based on config and available credentials.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.web.resolveWebProviderDefinition
import com.xiaomo.androidforclaw.web.hasWebProviderEntryCredential

object WebFetchRuntime {

    /** Known web fetch provider IDs */
    private val FETCH_PROVIDER_IDS = listOf("firecrawl", "jina")

    fun resolveWebFetchEnabled(
        fetchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        // Disabled in sandbox mode unless explicitly enabled
        if (sandboxed == true) {
            return fetchConfig?.get("enabled") == true
        }
        // Enabled by default when not sandboxed; can be explicitly disabled
        return fetchConfig?.get("enabled") != false
    }

    fun listWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        return FETCH_PROVIDER_IDS.mapNotNull { id ->
            val def = resolveWebProviderDefinition(id, config)
            if (def != null) {
                val configured = if (def.envKey != null) hasWebProviderEntryCredential(def.envKey) else true
                WebFetchProviderEntry(id = id, label = def.label, configured = configured)
            } else {
                WebFetchProviderEntry(id = id, label = id, configured = false)
            }
        }
    }

    fun listConfiguredWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        return listWebFetchProviders(config).filter { it.configured }
    }

    fun resolveWebFetchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        if (preferredId != null) {
            val providers = listConfiguredWebFetchProviders(config)
            if (providers.any { it.id == preferredId }) return preferredId
        }
        // Return first configured provider
        return listConfiguredWebFetchProviders(config).firstOrNull()?.id
    }

    fun resolveWebFetchDefinition(
        config: OpenClawConfig? = null
    ): WebFetchDefinitionResult? {
        val providerId = resolveWebFetchProviderId(config) ?: return null
        return WebFetchDefinitionResult(
            providerId = providerId,
            enabled = true
        )
    }
}
