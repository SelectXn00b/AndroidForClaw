package com.xiaomo.androidforclaw.videogeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object VideoGenerationProviderRegistry {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, VideoGenerationProvider>()

    fun register(provider: VideoGenerationProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<VideoGenerationProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): VideoGenerationProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }
}
