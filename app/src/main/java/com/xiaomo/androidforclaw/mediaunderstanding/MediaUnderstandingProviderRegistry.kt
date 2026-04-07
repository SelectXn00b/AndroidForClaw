package com.xiaomo.androidforclaw.mediaunderstanding

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MediaUnderstandingProviderRegistry {
    fun listProviders(config: OpenClawConfig? = null): List<MediaUnderstandingProvider> {
        TODO("List registered media understanding providers")
    }
    fun getProvider(providerId: String?, config: OpenClawConfig? = null): MediaUnderstandingProvider? {
        TODO("Get media understanding provider by ID")
    }
}
