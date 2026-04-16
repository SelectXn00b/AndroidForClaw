package com.xiaomo.hermes.web

/**
 * OpenClaw module: web
 * Source: OpenClaw/src/web/provider-runtime-shared.ts
 *
 * Shared runtime helpers for web search and web fetch provider resolution,
 * credential detection, and tool definition construction.
 */

import com.xiaomo.hermes.config.OpenClawConfig

data class WebProviderDefinition(
    val providerId: String,
    val label: String?,
    val requiresCredential: Boolean,
    val envKey: String?
)

/** Known web provider definitions with their env keys */
private val KNOWN_WEB_PROVIDERS = mapOf(
    "brave" to WebProviderDefinition("brave", "Brave Search", true, "BRAVE_API_KEY"),
    "google" to WebProviderDefinition("google", "Google Search", true, "GOOGLE_API_KEY"),
    "tavily" to WebProviderDefinition("tavily", "Tavily", true, "TAVILY_API_KEY"),
    "duckduckgo" to WebProviderDefinition("duckduckgo", "DuckDuckGo", false, null),
    "firecrawl" to WebProviderDefinition("firecrawl", "Firecrawl", true, "FIRECRAWL_API_KEY"),
    "jina" to WebProviderDefinition("jina", "Jina Reader", true, "JINA_API_KEY")
)

fun resolveWebProviderConfig(config: OpenClawConfig, scope: String): Map<String, Any?> {
    // Resolve provider entries from the tools section of config based on scope (e.g. "search", "fetch")
    val providers = config.resolveProviders()
    val result = mutableMapOf<String, Any?>()
    result["scope"] = scope
    result["providers"] = providers.keys.toList()
    return result
}

fun resolveWebProviderDefinition(
    providerId: String,
    config: OpenClawConfig? = null
): WebProviderDefinition? {
    return KNOWN_WEB_PROVIDERS[providerId.lowercase()]
}

fun readWebProviderEnvValue(envKey: String): String? {
    return System.getenv(envKey)
}

fun hasWebProviderEntryCredential(envKey: String): Boolean {
    return !System.getenv(envKey).isNullOrBlank()
}

fun providerRequiresCredential(providerId: String): Boolean {
    return KNOWN_WEB_PROVIDERS[providerId.lowercase()]?.requiresCredential ?: true
}
