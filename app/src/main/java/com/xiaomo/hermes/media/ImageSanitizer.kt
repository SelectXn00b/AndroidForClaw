package com.xiaomo.hermes.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log

/**
 * Image sanitization aligned with OpenClaw's image-sanitization.ts + tool-images.ts.
 *
 * Limits:
 *   DEFAULT_IMAGE_MAX_DIMENSION_PX = 1200
 *   DEFAULT_IMAGE_MAX_BYTES = 5 * 1024 * 1024  (5 MB)
 *
 * Resize grid (from OpenClaw buildImageResizeSideGrid):
 *   sides = [1800, 1600, 1400, 1200, 1000, 800]
 *   quality = [85, 75, 65, 55, 45, 35]
 *
 * Strategy: try each (side, quality) combo; first result ≤ maxBytes wins.
 */
object ImageSanitizer {

    private const val TAG = "ImageSanitizer"

    const val DEFAULT_MAX_DIMENSION_PX = 1200
    const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024  // 5 MB

    private val SIDE_GRID = intArrayOf(1800, 1600, 1400, 1200, 1000, 800)
    private val QUALITY_STEPS = intArrayOf(85, 75, 65, 55, 45, 35)

    /**
     * Holds a sanitized image ready for the LLM API.
     */
    data class SanitizedImage(
        val base64: String,
        val mimeType: String,  // always "image/jpeg" after sanitization
        val width: Int,
        val height: Int,
        val originalBytes: Int,
        val sanitizedBytes: Int,
        val resized: Boolean
    )

    /**
     * Sanitize a base64-encoded image: decode → check size → resize/recompress if needed.
     *
     * @param base64Data raw base64 string (no data: prefix)
     * @param sourceMimeType original mime type hint
     * @param maxDimensionPx max width/height in pixels
     * @param maxBytes max output size in bytes
     * @return SanitizedImage or null if decoding fails
     */
    fun sanitize(
        base64Data: String,
        sourceMimeType: String = "image/jpeg",
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): SanitizedImage? {
        val rawBytes = try {
            Base64.decode(base64Data, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64: ${e.message}")
            return null
        }

        val originalSize = rawBytes.size

        // Decode bitmap to get dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
        val origWidth = options.outWidth
        val origHeight = options.outHeight

        if (origWidth <= 0 || origHeight <= 0) {
            Log.e(TAG, "Failed to decode image dimensions: ${origWidth}x${origHeight}")
            return null
        }

        // Check if already within limits
        val maxDim = maxOf(origWidth, origHeight)
        if (maxDim <= maxDimensionPx && originalSize <= maxBytes) {
            Log.d(TAG, "Image already within limits: ${origWidth}x${origHeight}, ${formatBytes(originalSize)}")
            return SanitizedImage(
                base64 = base64Data,
                mimeType = sourceMimeType,
                width = origWidth,
                height = origHeight,
                originalBytes = originalSize,
                sanitizedBytes = originalSize,
                resized = false
            )
        }

        Log.d(TAG, "Image needs sanitization: ${origWidth}x${origHeight}, ${formatBytes(originalSize)}")

        // Build side grid aligned with OpenClaw: filter to ≤ maxDimensionPx, dedupe, desc sort
        val sideGrid = SIDE_GRID
            .map { minOf(maxDimensionPx, it) }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

        // Decode the full bitmap once
        val fullBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: run {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }

        var smallest: Pair<ByteArray, Int>? = null  // (bytes, size)

        try {
            for (side in sideGrid) {
                for (quality in QUALITY_STEPS) {
                    val result = resizeToJpeg(fullBitmap, side, quality)
                    val resultSize = result.size

                    if (smallest == null || resultSize < smallest!!.second) {
                        smallest = result to resultSize
                    }

                    if (resultSize <= maxBytes) {
                        val resultBase64 = Base64.encodeToString(result, Base64.NO_WRAP)
                        val reductionPct = if (originalSize > 0) {
                            ((originalSize - resultSize).toDouble() / originalSize * 100).toInt()
                        } else 0

                        Log.i(TAG, "Image resized: ${origWidth}x${origHeight} ${formatBytes(originalSize)} → " +
                                "${formatBytes(resultSize)} (-${reductionPct}%) [side=$side, q=$quality]")

                        fullBitmap.recycle()
                        return SanitizedImage(
                            base64 = resultBase64,
                            mimeType = "image/jpeg",
                            width = minOf(origWidth, side),
                            height = minOf(origHeight, side),
                            originalBytes = originalSize,
                            sanitizedBytes = resultSize,
                            resized = true
                        )
                    }
                }
            }
        } finally {
            if (!fullBitmap.isRecycled) fullBitmap.recycle()
        }

        // All attempts exceeded maxBytes — use smallest candidate anyway
        if (smallest != null) {
            val best = smallest!!
            Log.w(TAG, "Image could not be reduced below ${formatBytes(maxBytes)}, " +
                    "using best effort: ${formatBytes(best.second)}")
            val resultBase64 = Base64.encodeToString(best.first, Base64.NO_WRAP)
            return SanitizedImage(
                base64 = resultBase64,
                mimeType = "image/jpeg",
                width = origWidth,
                height = origHeight,
                originalBytes = originalSize,
                sanitizedBytes = best.second,
                resized = true
            )
        }

        Log.e(TAG, "Image sanitization failed completely")
        return null
    }

    /**
     * Resize bitmap to fit within maxSide (maintaining aspect ratio) and compress to JPEG.
     */
    private fun resizeToJpeg(bitmap: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = maxOf(w, h)

        val scaled = if (maxDim > maxSide) {
            val scale = maxSide.toFloat() / maxDim
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap  // don't enlarge
        }

        val stream = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }

    /**
     * Sanitize multiple images.
     */
    fun sanitizeAll(
        images: List<ImageData>,
        maxDimensionPx: Int = DEFAULT_MAX_DIMENSION_PX,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): List<SanitizedImage> {
        return images.mapNotNull { img ->
            sanitize(img.base64, img.mimeType, maxDimensionPx, maxBytes)
        }
    }

    private fun formatBytes(bytes: Int): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))}MB"
        }
    }
}

/**
 * Raw image data before sanitization.
 */
data class ImageData(
    val base64: String,
    val mimeType: String = "image/jpeg"
)
