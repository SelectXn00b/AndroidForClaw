package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/provider-registry.ts
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object TtsProviderRegistry {

    fun canonicalizeSpeechProviderId(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderId? {
        TODO("Canonicalize speech provider ID")
    }

    fun listSpeechProviders(config: OpenClawConfig? = null): List<SpeechProviderPlugin> {
        TODO("List registered speech providers")
    }

    fun getSpeechProvider(
        providerId: String?,
        config: OpenClawConfig? = null
    ): SpeechProviderPlugin? {
        TODO("Get speech provider by ID")
    }
}
