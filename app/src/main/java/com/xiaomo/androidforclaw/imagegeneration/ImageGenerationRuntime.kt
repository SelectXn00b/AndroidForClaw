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
        TODO("Generate image with fallback across providers")
    }

    fun listRuntimeProviders(config: OpenClawConfig? = null): List<ImageGenerationProvider> {
        TODO("List runtime-available image generation providers")
    }
}
