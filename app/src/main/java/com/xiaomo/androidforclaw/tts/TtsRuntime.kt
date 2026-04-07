package com.xiaomo.androidforclaw.tts

/**
 * OpenClaw module: tts
 * Source: OpenClaw/src/tts/tts.ts, tts-core.ts, tts-auto-mode.ts
 *
 * Text-to-speech runtime with provider registry, voice listing, auto-mode,
 * text summarization for length limits, and telephony support.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object TtsRuntime {

    suspend fun synthesizeSpeech(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        TODO("Synthesize speech via resolved provider")
    }

    suspend fun listSpeechVoices(
        providerId: String? = null,
        config: OpenClawConfig? = null
    ): List<SpeechVoiceOption> {
        TODO("List available speech voices")
    }

    fun getTtsMaxLength(config: OpenClawConfig? = null): Int {
        TODO("Get max TTS text length")
    }

    suspend fun summarizeTextForTts(text: String, maxLength: Int): String {
        TODO("Summarize text to fit TTS length limit using LLM")
    }

    fun parseTtsDirectives(text: String): TtsDirectiveParseResult {
        TODO("Parse TTS directives from text")
    }
}
