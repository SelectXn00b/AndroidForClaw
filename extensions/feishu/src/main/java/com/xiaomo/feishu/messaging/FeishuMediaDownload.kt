package com.xiaomo.feishu.messaging

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/media.ts (downloadImageFeishu, downloadMessageResourceFeishu)
 *
 * Downloads media attachments from Feishu messages.
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import java.io.File

/**
 * 媒体下载结果
 */
data class DownloadResult(
    val file: File,
    val contentType: String? = null
)

/**
 * 飞书媒体下载器
 * 对齐 OpenClaw media.ts download APIs
 */
class FeishuMediaDownload(
    private val client: FeishuClient,
    private val cacheDir: File
) {
    companion object {
        private const val TAG = "FeishuMediaDownload"
        private const val MEDIA_CACHE_DIR = "feishu_media"
    }

    private val mediaCacheDir: File by lazy {
        File(cacheDir, MEDIA_CACHE_DIR).also { it.mkdirs() }
    }

    /**
     * 下载独立图片
     * 对齐 OpenClaw downloadImageFeishu
     *
     * API: GET /open-apis/im/v1/images/{image_key}
     */
    suspend fun downloadImage(imageKey: String): Result<DownloadResult> {
        // Check cache
        val cached = File(mediaCacheDir, "img_$imageKey")
        if (cached.exists() && cached.length() > 0) {
            Log.d(TAG, "Image cache hit: $imageKey")
            return Result.success(DownloadResult(cached))
        }

        val result = client.downloadRaw("/open-apis/im/v1/images/$imageKey")
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }

        val bytes = result.getOrNull()!!
        cached.writeBytes(bytes)
        Log.d(TAG, "Downloaded image: $imageKey (${bytes.size} bytes)")
        return Result.success(DownloadResult(cached))
    }

    /**
     * 下载消息附件资源
     * 对齐 OpenClaw downloadMessageResourceFeishu
     *
     * API: GET /open-apis/im/v1/messages/{message_id}/resources/{file_key}?type={image|file}
     */
    suspend fun downloadMessageResource(
        messageId: String,
        fileKey: String,
        type: String = "file"
    ): Result<DownloadResult> {
        // Check cache
        val cached = File(mediaCacheDir, "res_${fileKey}")
        if (cached.exists() && cached.length() > 0) {
            Log.d(TAG, "Resource cache hit: $fileKey")
            return Result.success(DownloadResult(cached))
        }

        val path = "/open-apis/im/v1/messages/$messageId/resources/$fileKey?type=$type"
        val result = client.downloadRaw(path)
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull()!!)
        }

        val bytes = result.getOrNull()!!
        cached.writeBytes(bytes)
        Log.d(TAG, "Downloaded resource: $fileKey (${bytes.size} bytes)")
        return Result.success(DownloadResult(cached))
    }

    /**
     * 根据 MediaKeys 自动下载
     * 返回本地文件路径描述（追加到消息内容中）
     */
    suspend fun downloadMedia(
        messageId: String,
        mediaKeys: MediaKeys
    ): Result<DownloadResult> {
        return when (mediaKeys.mediaType) {
            "image" -> {
                val key = mediaKeys.imageKey
                    ?: return Result.failure(Exception("Missing image_key"))
                downloadImage(key)
            }
            "file", "audio", "video", "sticker" -> {
                val key = mediaKeys.fileKey
                    ?: return Result.failure(Exception("Missing file_key"))
                downloadMessageResource(messageId, key, "file")
            }
            else -> Result.failure(Exception("Unsupported media type: ${mediaKeys.mediaType}"))
        }
    }

    /**
     * 清理过期缓存（超过 24 小时）
     */
    fun cleanupCache(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        mediaCacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }
}
