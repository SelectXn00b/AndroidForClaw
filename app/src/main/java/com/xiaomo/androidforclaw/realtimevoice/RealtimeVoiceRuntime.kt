package com.xiaomo.androidforclaw.realtimevoice

import com.xiaomo.androidforclaw.config.OpenClawConfig

object RealtimeVoiceRuntime {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, RealtimeVoiceProvider>()

    fun register(provider: RealtimeVoiceProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<RealtimeVoiceProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): RealtimeVoiceProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }

    fun canonicalizeProviderId(providerId: String?, config: OpenClawConfig? = null): String? {
        if (providerId == null) return null
        val normalized = providerId.lowercase().trim()
        val provider = providers[normalized]
            ?: providers.values.find { p -> p.aliases.any { it.lowercase() == normalized } }
        return provider?.id
    }
}
