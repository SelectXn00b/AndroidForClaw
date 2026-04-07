package com.xiaomo.androidforclaw.realtimetranscription

import com.xiaomo.androidforclaw.config.OpenClawConfig

object RealtimeTranscriptionRuntime {
    fun listProviders(config: OpenClawConfig? = null): List<RealtimeTranscriptionProvider> {
        TODO("List registered realtime transcription providers")
    }
    fun getProvider(providerId: String?, config: OpenClawConfig? = null): RealtimeTranscriptionProvider? {
        TODO("Get realtime transcription provider by ID")
    }
    fun canonicalizeProviderId(providerId: String?, config: OpenClawConfig? = null): RealtimeTranscriptionProviderId? {
        TODO("Canonicalize realtime transcription provider ID")
    }
}
