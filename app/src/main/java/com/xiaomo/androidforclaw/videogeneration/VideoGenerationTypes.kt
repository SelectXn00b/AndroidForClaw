package com.xiaomo.androidforclaw.videogeneration

data class GeneratedVideoAsset(
    val data: ByteArray,
    val mimeType: String = "video/mp4",
    val durationMs: Long? = null
) {
    override fun equals(other: Any?) = this === other || (other is GeneratedVideoAsset && data.contentEquals(other.data))
    override fun hashCode(): Int = data.contentHashCode()
}

data class VideoGenerationRequest(
    val prompt: String,
    val provider: String? = null,
    val model: String? = null,
    val durationMs: Long? = null,
    val sourceImage: ByteArray? = null
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode(): Int = prompt.hashCode()
}

data class VideoGenerationResult(val assets: List<GeneratedVideoAsset>, val provider: String, val model: String)

interface VideoGenerationProvider {
    val id: String
    val aliases: List<String>
    val label: String?
    val defaultModel: String?
    suspend fun generate(request: VideoGenerationRequest): VideoGenerationResult
}
