package com.xiaomo.androidforclaw.musicgeneration

/**
 * OpenClaw module: music-generation
 * Source: OpenClaw/src/music-generation/types.ts
 */

enum class MusicGenerationOutputFormat { MP3, WAV, OGG, FLAC }

data class GeneratedMusicAsset(
    val data: ByteArray,
    val format: MusicGenerationOutputFormat = MusicGenerationOutputFormat.MP3,
    val durationMs: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedMusicAsset) return false
        return data.contentEquals(other.data) && format == other.format
    }
    override fun hashCode(): Int = data.contentHashCode()
}

data class MusicGenerationRequest(
    val prompt: String,
    val provider: String? = null,
    val model: String? = null,
    val durationMs: Long? = null,
    val outputFormat: MusicGenerationOutputFormat? = null
)

data class MusicGenerationResult(
    val assets: List<GeneratedMusicAsset>,
    val provider: String,
    val model: String
)

interface MusicGenerationProvider {
    val id: String
    val aliases: List<String>
    val label: String?
    val defaultModel: String?
    suspend fun generate(request: MusicGenerationRequest): MusicGenerationResult
}
