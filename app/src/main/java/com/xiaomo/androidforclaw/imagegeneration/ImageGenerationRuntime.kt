package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/runtime.ts
 *
 * Provider-agnostic image generation runtime with model/provider resolution,
 * parameter validation, and fallback support.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object ImageGenerationRuntime {
    suspend fun generateImage(
        request: ImageGenerationRequest,
        config: OpenClawConfig? = null
    ): ImageGenerationResult {
        val providers = if (request.provider != null) {
            val p = ImageGenerationProviderRegistry.getProvider(request.provider, config)
                ?: throw IllegalStateException("Image generation provider not found: ${request.provider}")
            listOf(p)
        } else {
            ImageGenerationProviderRegistry.listProviders(config)
        }

        if (providers.isEmpty()) {
            throw IllegalStateException("No image generation providers registered")
        }

        var lastError: Throwable? = null
        for (provider in providers) {
            try {
                return provider.generate(request)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All image generation providers failed")
    }

    fun listRuntimeProviders(config: OpenClawConfig? = null): List<ImageGenerationProvider> {
        return ImageGenerationProviderRegistry.listProviders(config)
    }
}
