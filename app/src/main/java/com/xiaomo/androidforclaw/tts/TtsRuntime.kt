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

    private val TTS_DIRECTIVE_REGEX = Regex(
        """\[tts(?::(\w+))?(?:\s+voice=(\w+))?\](.*?)\[/tts\]""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    suspend fun synthesizeSpeech(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        val provider = TtsProviderRegistry.getSpeechProvider(request.provider)
            ?: throw IllegalStateException(
                "No TTS provider found${request.provider?.let { " for ID: $it" } ?: ""}"
            )
        return provider.synthesize(request)
    }

    suspend fun listSpeechVoices(
        providerId: String? = null,
        config: OpenClawConfig? = null
    ): List<SpeechVoiceOption> {
        val provider = TtsProviderRegistry.getSpeechProvider(providerId, config)
            ?: return emptyList()
        return provider.listVoices()
    }

    fun getTtsMaxLength(config: OpenClawConfig? = null): Int {
        if (config != null) {
            return resolveTtsConfig(config).maxLength
        }
        return 4000
    }

    suspend fun summarizeTextForTts(text: String, maxLength: Int): String {
        // Truncate to maxLength as a simple fallback.
        // A real implementation would delegate to an LLM for intelligent summarization.
        if (text.length <= maxLength) return text
        return text.take(maxLength - 3) + "..."
    }

    fun parseTtsDirectives(text: String): TtsDirectiveParseResult {
        val match = TTS_DIRECTIVE_REGEX.find(text)
        if (match == null) {
            return TtsDirectiveParseResult(
                cleanedText = text,
                ttsText = null,
                hasDirective = false
            )
        }
        val provider = match.groupValues[1].takeIf { it.isNotEmpty() }
        val voice = match.groupValues[2].takeIf { it.isNotEmpty() }
        val ttsContent = match.groupValues[3].trim()
        val cleanedText = text.replace(match.value, "").trim()

        return TtsDirectiveParseResult(
            cleanedText = cleanedText,
            ttsText = ttsContent,
            hasDirective = true,
            overrides = TtsDirectiveOverrides(
                ttsText = ttsContent,
                provider = provider,
                voice = voice
            )
        )
    }
}
