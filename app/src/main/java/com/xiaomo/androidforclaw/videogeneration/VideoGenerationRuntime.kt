package com.xiaomo.androidforclaw.videogeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object VideoGenerationRuntime {
    suspend fun generateVideo(request: VideoGenerationRequest, config: OpenClawConfig? = null): VideoGenerationResult {
        TODO("Generate video with fallback across providers")
    }
    fun listRuntimeProviders(config: OpenClawConfig? = null): List<VideoGenerationProvider> {
        TODO("List runtime-available video generation providers")
    }
}
