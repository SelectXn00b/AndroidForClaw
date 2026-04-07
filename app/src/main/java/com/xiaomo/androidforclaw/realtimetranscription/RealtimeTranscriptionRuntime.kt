package com.xiaomo.androidforclaw.realtimetranscription

import com.xiaomo.androidforclaw.config.OpenClawConfig

object RealtimeTranscriptionRuntime {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, RealtimeTranscriptionProvider>()

    fun register(provider: RealtimeTranscriptionProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<RealtimeTranscriptionProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): RealtimeTranscriptionProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }

    fun canonicalizeProviderId(providerId: String?, config: OpenClawConfig? = null): RealtimeTranscriptionProviderId? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        val provider = providers[normalized]
            ?: providers.values.find { p -> p.aliases.any { it.lowercase() == normalized } }
        return provider?.id
    }
}
