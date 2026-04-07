package com.xiaomo.androidforclaw.mediaunderstanding

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MediaUnderstandingProviderRegistry {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, MediaUnderstandingProvider>()

    fun register(provider: MediaUnderstandingProvider) {
        providers[provider.id] = provider
    }

    fun unregister(providerId: String) {
        providers.remove(providerId)
    }

    fun listProviders(config: OpenClawConfig? = null): List<MediaUnderstandingProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): MediaUnderstandingProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId]
    }
}
