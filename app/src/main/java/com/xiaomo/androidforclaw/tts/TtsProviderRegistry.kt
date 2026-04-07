package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/provider-registry.ts
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import java.util.concurrent.ConcurrentHashMap

object TtsProviderRegistry {

    private val providers = ConcurrentHashMap<SpeechProviderId, SpeechProviderPlugin>()

    fun register(provider: SpeechProviderPlugin) {
        providers[provider.id] = provider
        // Also register aliases for lookup
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun canonicalizeSpeechProviderId(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderId? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        // Check direct match first
        if (providers.containsKey(normalized)) return providers[normalized]!!.id
        // Check aliases
        val byAlias = providers.values.find { plugin ->
            plugin.aliases.any { it.lowercase() == normalized }
        }
        return byAlias?.id
    }

    fun listSpeechProviders(config: OpenClawConfig? = null): List<SpeechProviderPlugin> {
        // Return unique providers (exclude alias duplicates)
        return providers.values.distinctBy { it.id }
    }

    fun getSpeechProvider(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderPlugin? {
        if (providerId == null) return providers.values.firstOrNull()
        val canonical = canonicalizeSpeechProviderId(providerId, config) ?: return null
        return providers[canonical]
    }
}
