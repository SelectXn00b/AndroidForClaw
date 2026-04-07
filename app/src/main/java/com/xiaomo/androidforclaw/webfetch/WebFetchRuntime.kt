package com.xiaomo.androidforclaw.webfetch

/**
 * OpenClaw module: web-fetch
 * Source: OpenClaw/src/web-fetch/runtime.ts
 *
 * Web fetch tool runtime that selects and configures a web content fetching
 * provider (e.g., Firecrawl, Jina) based on config and available credentials.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object WebFetchRuntime {

    fun resolveWebFetchEnabled(
        fetchConfig: Map<String, Any?>?,
        sandboxed: Boolean? = null
    ): Boolean {
        TODO("Resolve web fetch enabled state")
    }

    fun listWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        TODO("List available web fetch providers")
    }

    fun listConfiguredWebFetchProviders(config: OpenClawConfig? = null): List<WebFetchProviderEntry> {
        TODO("List configured web fetch providers")
    }

    fun resolveWebFetchProviderId(
        config: OpenClawConfig? = null,
        preferredId: String? = null
    ): String? {
        TODO("Resolve which web fetch provider to use")
    }

    fun resolveWebFetchDefinition(
        config: OpenClawConfig? = null
    ): WebFetchDefinitionResult? {
        TODO("Resolve and create the active web fetch tool definition")
    }
}
