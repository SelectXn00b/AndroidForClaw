package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/provider-registry.ts
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object ImageGenerationProviderRegistry {

    private val providers = java.util.concurrent.ConcurrentHashMap<String, ImageGenerationProvider>()

    fun register(provider: ImageGenerationProvider) {
        providers[provider.id] = provider
        provider.aliases.forEach { alias -> providers[alias.lowercase()] = provider }
    }

    fun unregister(providerId: String) {
        val provider = providers.remove(providerId)
        provider?.aliases?.forEach { providers.remove(it.lowercase()) }
    }

    fun listProviders(config: OpenClawConfig? = null): List<ImageGenerationProvider> {
        return providers.values.distinctBy { it.id }
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): ImageGenerationProvider? {
        if (providerId == null) return providers.values.firstOrNull()
        return providers[providerId] ?: providers[providerId.lowercase()]
    }
}
