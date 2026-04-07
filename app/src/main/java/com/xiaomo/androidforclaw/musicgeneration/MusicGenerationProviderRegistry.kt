package com.xiaomo.androidforclaw.musicgeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MusicGenerationProviderRegistry {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, MusicGenerationProvider>()

    fun register(provider: MusicGenerationProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<MusicGenerationProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): MusicGenerationProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }
}
