package com.xiaomo.androidforclaw.realtimevoice

import com.xiaomo.androidforclaw.config.OpenClawConfig

object RealtimeVoiceRuntime {
    fun listProviders(config: OpenClawConfig? = null): List<RealtimeVoiceProvider> {
        TODO("List registered realtime voice providers")
    }
    fun getProvider(providerId: String?, config: OpenClawConfig? = null): RealtimeVoiceProvider? {
        TODO("Get realtime voice provider by ID")
    }
    fun canonicalizeProviderId(providerId: String?, config: OpenClawConfig? = null): String? {
        TODO("Canonicalize realtime voice provider ID")
    }
}
