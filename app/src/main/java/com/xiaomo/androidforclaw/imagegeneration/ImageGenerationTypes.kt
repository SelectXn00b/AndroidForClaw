package com.xiaomo.androidforclaw.imagegeneration

/**
 * OpenClaw module: image-generation
 * Source: OpenClaw/src/image-generation/types.ts
 */

data class ImageGenerationResolution(val width: Int, val height: Int)

data class ImageGenerationSourceImage(
    val data: ByteArray,
    val mimeType: String,
    val role: String = "reference"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageGenerationSourceImage) return false
        return data.contentEquals(other.data) && mimeType == other.mimeType && role == other.role
    }
    override fun hashCode(): Int = data.contentHashCode()
}

data class GeneratedImageAsset(
    val data: ByteArray,
    val mimeType: String = "image/png",
    val revisedPrompt: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImageAsset) return false
        return data.contentEquals(other.data) && mimeType == other.mimeType
    }
    override fun hashCode(): Int = data.contentHashCode()
}

data class ImageGenerationRequest(
    val prompt: String,
    val provider: String? = null,
    val model: String? = null,
    val resolution: ImageGenerationResolution? = null,
    val sourceImages: List<ImageGenerationSourceImage>? = null,
    val n: Int = 1
)

data class ImageGenerationResult(
    val images: List<GeneratedImageAsset>,
    val provider: String,
    val model: String
)

data class ImageGenerationProviderCapabilities(
    val supportsEdit: Boolean = false,
    val maxResolution: ImageGenerationResolution? = null,
    val supportedFormats: List<String> = listOf("png")
)

interface ImageGenerationProvider {
    val id: String
    val aliases: List<String>
    val label: String?
    val defaultModel: String?
    val capabilities: ImageGenerationProviderCapabilities
    suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult
}
