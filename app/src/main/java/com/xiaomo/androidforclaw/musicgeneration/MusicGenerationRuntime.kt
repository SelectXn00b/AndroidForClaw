package com.xiaomo.androidforclaw.musicgeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MusicGenerationRuntime {
    suspend fun generateMusic(request: MusicGenerationRequest, config: OpenClawConfig? = null): MusicGenerationResult {
        val providers = if (request.provider != null) {
            val p = MusicGenerationProviderRegistry.getProvider(request.provider, config)
                ?: throw IllegalStateException("Music generation provider not found: ${request.provider}")
            listOf(p)
        } else {
            MusicGenerationProviderRegistry.listProviders(config)
        }

        if (providers.isEmpty()) {
            throw IllegalStateException("No music generation providers registered")
        }

        var lastError: Throwable? = null
        for (provider in providers) {
            try {
                return provider.generate(request)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All music generation providers failed")
    }

    fun listRuntimeProviders(config: OpenClawConfig? = null): List<MusicGenerationProvider> {
        return MusicGenerationProviderRegistry.listProviders(config)
    }
}
