package com.xiaomo.androidforclaw.mediageneration

/**
 * OpenClaw module: media-generation
 * Source: OpenClaw/src/media-generation/runtime-shared.ts
 *
 * Shared runtime utilities for media generation capabilities —
 * model candidate resolution with fallback chains, error formatting.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class ParsedProviderModelRef(val provider: String, val model: String)

fun parseProviderModelRef(ref: String): ParsedProviderModelRef? {
    val parts = ref.split("/", limit = 2)
    return if (parts.size == 2) ParsedProviderModelRef(parts[0], parts[1]) else null
}

fun resolveCapabilityModelCandidates(
    capability: String,
    config: OpenClawConfig? = null,
    defaultModel: String? = null
): List<ParsedProviderModelRef> {
    val candidates = mutableListOf<ParsedProviderModelRef>()
    val seen = mutableSetOf<String>()
    fun add(raw: String?) {
        val parsed = raw?.let { parseProviderModelRef(it) } ?: return
        val key = "${parsed.provider}/${parsed.model}"
        if (seen.add(key)) candidates.add(parsed)
    }
    // Default fallback
    add(defaultModel)
    return candidates
}

fun buildNoCapabilityModelConfiguredMessage(capability: String): String =
    "No $capability model configured. Please configure a provider with $capability support."
