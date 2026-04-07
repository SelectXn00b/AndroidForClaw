package com.xiaomo.androidforclaw.webfetch

/**
 * OpenClaw module: web-fetch
 * Source: OpenClaw/src/web-fetch/runtime.ts
 */

data class WebFetchProviderEntry(
    val id: String,
    val label: String?,
    val configured: Boolean
)

data class WebFetchDefinitionResult(
    val providerId: String,
    val enabled: Boolean
)
