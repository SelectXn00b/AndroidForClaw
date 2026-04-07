package com.xiaomo.androidforclaw.mediaunderstanding

enum class MediaUnderstandingCapability { IMAGE_DESCRIPTION, AUDIO_TRANSCRIPTION, VIDEO_DESCRIPTION }

data class MediaAttachment(
    val data: ByteArray,
    val mimeType: String,
    val filename: String? = null,
    val url: String? = null
) {
    override fun equals(other: Any?) = this === other || (other is MediaAttachment && data.contentEquals(other.data))
    override fun hashCode(): Int = data.contentHashCode()
}

data class MediaUnderstandingOutput(
    val text: String,
    val capability: MediaUnderstandingCapability,
    val provider: String? = null,
    val model: String? = null
)

data class AudioTranscriptionRequest(
    val audio: MediaAttachment,
    val language: String? = null,
    val provider: String? = null
)

data class AudioTranscriptionResult(
    val text: String,
    val language: String? = null,
    val durationMs: Long? = null
)

data class ImageDescriptionRequest(val image: MediaAttachment, val prompt: String? = null, val provider: String? = null)
data class ImageDescriptionResult(val text: String, val provider: String? = null, val model: String? = null)

interface MediaUnderstandingProvider {
    val id: String
    val capabilities: Set<MediaUnderstandingCapability>
    suspend fun describeImage(request: ImageDescriptionRequest): ImageDescriptionResult
    suspend fun transcribeAudio(request: AudioTranscriptionRequest): AudioTranscriptionResult
}
