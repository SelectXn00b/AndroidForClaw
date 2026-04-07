package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/tts-config.ts, status-config.ts
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

data class TtsConfig(
    val enabled: Boolean = false,
    val provider: String? = null,
    val voice: String? = null,
    val autoMode: String = "off",
    val maxLength: Int = 4000
)

fun isTtsEnabled(config: OpenClawConfig): Boolean {
    val ttsSkill = config.skills.entries["tts"]
    return ttsSkill?.enabled ?: false
}

fun isTtsProviderConfigured(config: OpenClawConfig): Boolean {
    val ttsSkill = config.skills.entries["tts"]
    val providerName = ttsSkill?.config?.get("provider") as? String
    return !providerName.isNullOrBlank() &&
        TtsProviderRegistry.getSpeechProvider(providerName) != null
}

fun resolveTtsConfig(config: OpenClawConfig): TtsConfig {
    val ttsSkill = config.skills.entries["tts"]
    val skillConfig = ttsSkill?.config ?: emptyMap()
    return TtsConfig(
        enabled = ttsSkill?.enabled ?: false,
        provider = skillConfig["provider"] as? String,
        voice = skillConfig["voice"] as? String,
        autoMode = (skillConfig["autoMode"] as? String) ?: "off",
        maxLength = (skillConfig["maxLength"] as? Number)?.toInt() ?: 4000
    )
}
