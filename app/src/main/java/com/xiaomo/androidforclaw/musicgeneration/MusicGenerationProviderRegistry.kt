package com.xiaomo.androidforclaw.musicgeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MusicGenerationProviderRegistry {
    fun listProviders(config: OpenClawConfig? = null): List<MusicGenerationProvider> {
        TODO("List registered music generation providers")
    }
    fun getProvider(providerId: String?, config: OpenClawConfig? = null): MusicGenerationProvider? {
        TODO("Get music generation provider by ID")
    }
}
