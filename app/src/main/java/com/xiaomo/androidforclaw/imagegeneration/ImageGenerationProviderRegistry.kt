package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/provider-registry.ts
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object ImageGenerationProviderRegistry {
    fun listProviders(config: OpenClawConfig? = null): List<ImageGenerationProvider> {
        TODO("List registered image generation providers")
    }

    fun getProvider(providerId: String?, config: OpenClawConfig? = null): ImageGenerationProvider? {
        TODO("Get image generation provider by ID")
    }
}
