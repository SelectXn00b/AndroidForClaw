package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/provider-types.ts, tts-config.ts, directives.ts
 */

typealias SpeechProviderId = String

enum class SpeechSynthesisTarget { AUDIO_FILE, VOICE_NOTE }

data class SpeechVoiceOption(
    val id: String,
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val locale: String? = null,
    val gender: String? = null,
    val personalities: List<String>? = null
)

data class SpeechSynthesisRequest(
    val text: String,
    val provider: SpeechProviderId? = null,
    val voice: String? = null,
    val target: SpeechSynthesisTarget = SpeechSynthesisTarget.AUDIO_FILE,
    val timeoutMs: Long = 30_000
)

data class SpeechSynthesisResult(
    val audioData: ByteArray,
    val outputFormat: String,
    val fileExtension: String,
    val voiceCompatible: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeechSynthesisResult) return false
        return audioData.contentEquals(other.audioData) &&
            outputFormat == other.outputFormat &&
            fileExtension == other.fileExtension
    }
    override fun hashCode(): Int = audioData.contentHashCode()
}

interface SpeechProviderPlugin {
    val id: SpeechProviderId
    val aliases: List<String>
    val label: String?
    val defaultVoice: String?
    suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult
    suspend fun listVoices(): List<SpeechVoiceOption>
}

data class TtsDirectiveOverrides(
    val ttsText: String? = null,
    val provider: SpeechProviderId? = null,
    val voice: String? = null
)

data class TtsDirectiveParseResult(
    val cleanedText: String,
    val ttsText: String? = null,
    val hasDirective: Boolean,
    val overrides: TtsDirectiveOverrides = TtsDirectiveOverrides(),
    val warnings: List<String> = emptyList()
)
