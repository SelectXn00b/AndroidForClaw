package com.xiaomo.androidforclaw.musicgeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MusicGenerationRuntime {
    suspend fun generateMusic(request: MusicGenerationRequest, config: OpenClawConfig? = null): MusicGenerationResult {
        TODO("Generate music with fallback across providers")
    }
    fun listRuntimeProviders(config: OpenClawConfig? = null): List<MusicGenerationProvider> {
        TODO("List runtime-available music generation providers")
    }
}
