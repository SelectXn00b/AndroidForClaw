package com.xiaomo.androidforclaw.mediaunderstanding

/**
 * OpenClaw module: media-understanding
 * Source: OpenClaw/src/media-understanding/runtime.ts
 *
 * Multi-modal media analysis: image description, audio transcription,
 * video description via pluggable provider registry.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

object MediaUnderstandingRuntime {
    suspend fun describeImage(image: MediaAttachment, config: OpenClawConfig? = null): ImageDescriptionResult {
        val provider = MediaUnderstandingProviderRegistry.listProviders(config)
            .firstOrNull { MediaUnderstandingCapability.IMAGE_DESCRIPTION in it.capabilities }
            ?: throw IllegalStateException("No media understanding provider with IMAGE_DESCRIPTION capability")
        return provider.describeImage(ImageDescriptionRequest(image = image))
    }

    suspend fun transcribeAudio(audio: MediaAttachment, config: OpenClawConfig? = null): AudioTranscriptionResult {
        val provider = MediaUnderstandingProviderRegistry.listProviders(config)
            .firstOrNull { MediaUnderstandingCapability.AUDIO_TRANSCRIPTION in it.capabilities }
            ?: throw IllegalStateException("No media understanding provider with AUDIO_TRANSCRIPTION capability")
        return provider.transcribeAudio(AudioTranscriptionRequest(audio = audio))
    }

    suspend fun processMediaAttachments(
        attachments: List<MediaAttachment>,
        config: OpenClawConfig? = null
    ): List<MediaUnderstandingOutput> {
        return attachments.mapNotNull { attachment ->
            try {
                when {
                    attachment.mimeType.startsWith("image/") -> {
                        val result = describeImage(attachment, config)
                        MediaUnderstandingOutput(
                            text = result.text,
                            capability = MediaUnderstandingCapability.IMAGE_DESCRIPTION,
                            provider = result.provider,
                            model = result.model
                        )
                    }
                    attachment.mimeType.startsWith("audio/") -> {
                        val result = transcribeAudio(attachment, config)
                        MediaUnderstandingOutput(
                            text = result.text,
                            capability = MediaUnderstandingCapability.AUDIO_TRANSCRIPTION,
                            provider = null,
                            model = null
                        )
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
