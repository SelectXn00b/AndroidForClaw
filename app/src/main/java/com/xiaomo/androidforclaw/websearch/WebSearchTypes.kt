package com.xiaomo.androidforclaw.websearch

/**
 * OpenClaw module: web-search
 * Source: OpenClaw/src/web-search/runtime.ts
 */

data class WebSearchProviderEntry(
    val id: String,
    val label: String?,
    val configured: Boolean
)

data class WebSearchDefinitionResult(
    val providerId: String,
    val enabled: Boolean
)

data class WebSearchResult(
    val query: String,
    val results: List<WebSearchResultItem>,
    val providerId: String
)

data class WebSearchResultItem(
    val title: String,
    val url: String,
    val snippet: String? = null
)
