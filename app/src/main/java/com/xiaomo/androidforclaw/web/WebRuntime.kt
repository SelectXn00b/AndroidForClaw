package com.xiaomo.androidforclaw.web

/**
 * OpenClaw module: web
 * Source: OpenClaw/src/web/provider-runtime-shared.ts
 *
 * Shared runtime helpers for web search and web fetch provider resolution,
 * credential detection, and tool definition construction.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class WebProviderDefinition(
    val providerId: String,
    val label: String?,
    val requiresCredential: Boolean,
    val envKey: String?
)

fun resolveWebProviderConfig(config: OpenClawConfig, scope: String): Map<String, Any?> {
    TODO("Resolve web provider config from OpenClawConfig")
}

fun resolveWebProviderDefinition(
    providerId: String,
    config: OpenClawConfig? = null
): WebProviderDefinition? {
    TODO("Resolve web provider definition")
}

fun readWebProviderEnvValue(envKey: String): String? {
    TODO("Read web provider env value")
}

fun hasWebProviderEntryCredential(envKey: String): Boolean {
    TODO("Check if web provider credential is configured")
}

fun providerRequiresCredential(providerId: String): Boolean {
    TODO("Check if provider requires credential")
}
