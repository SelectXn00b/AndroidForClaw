package com.xiaomo.androidforclaw.videogeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object VideoGenerationRuntime {
    suspend fun generateVideo(request: VideoGenerationRequest, config: OpenClawConfig? = null): VideoGenerationResult {
        val providers = if (request.provider != null) {
            val p = VideoGenerationProviderRegistry.getProvider(request.provider, config)
                ?: throw IllegalStateException("Video generation provider not found: ${request.provider}")
            listOf(p)
        } else {
            VideoGenerationProviderRegistry.listProviders(config)
        }

        if (providers.isEmpty()) {
            throw IllegalStateException("No video generation providers registered")
        }

        var lastError: Throwable? = null
        for (provider in providers) {
            try {
                return provider.generate(request)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All video generation providers failed")
    }

    fun listRuntimeProviders(config: OpenClawConfig? = null): List<VideoGenerationProvider> {
        return VideoGenerationProviderRegistry.listProviders(config)
    }
}
