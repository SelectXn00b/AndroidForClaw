package com.xiaomo.androidforclaw.videogeneration

import com.xiaomo.androidforclaw.config.OpenClawConfig

object VideoGenerationProviderRegistry {
    fun listProviders(config: OpenClawConfig? = null): List<VideoGenerationProvider> {
        TODO("List registered video generation providers")
    }
    fun getProvider(providerId: String?, config: OpenClawConfig? = null): VideoGenerationProvider? {
        TODO("Get video generation provider by ID")
    }
}
