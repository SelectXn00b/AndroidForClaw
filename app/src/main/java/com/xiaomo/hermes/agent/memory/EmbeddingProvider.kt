package com.xiaomo.hermes.agent.memory

import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/embeddings.ts, embeddings-openai.ts
 *
 * Embedding provider — aligned with OpenClaw embedding integration.
 * Calls OpenAI-compatible embedding API (text-embedding-3-small).
 */
class EmbeddingProvider(
    private val baseUrl: String = "https://api.openai.com/v1",
    private val apiKey: String = "",
    private val model: String = "text-embedding-3-small"
) {
    companion object {
        private const val TAG = "EmbeddingProvider"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val isAvailable: Boolean get() = apiKey.isNotBlank()
    val modelName: String get() = model
    val providerName: String get() = baseUrl

    /**
     * Embed a single text. Returns normalized FloatArray.
     */
    suspend fun embed(text: String): FloatArray? {
        val results = embedBatch(listOf(text))
        return results?.firstOrNull()
    }

    /**
     * Embed a batch of texts. Returns normalized FloatArrays.
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray>? = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            Log.w(TAG, "Embedding provider not configured (no API key)")
            return@withContext null
        }
        if (texts.isEmpty()) return@withContext emptyList()

        try {
            val url = "${baseUrl.trimEnd('/')}/embeddings"

            val inputArray = JSONArray()
            texts.forEach { inputArray.put(it) }

            val body = JSONObject().apply {
                put("input", inputArray)
                put("model", model)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Embedding API error: ${response.code} ${response.body?.string()?.take(200)}")
                return@withContext null
            }

            val json = JSONObject(response.body!!.string())
            val data = json.getJSONArray("data")

            val results = mutableListOf<FloatArray>()
            for (i in 0 until data.length()) {
                val embeddingArr = data.getJSONObject(i).getJSONArray("embedding")
                val vec = FloatArray(embeddingArr.length()) { embeddingArr.getDouble(it).toFloat() }
                normalize(vec)
                results.add(vec)
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Embedding API call failed", e)
            null
        }
    }

    /**
     * L2 normalize in-place.
     */
    private fun normalize(vec: FloatArray) {
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        val mag = sqrt(sumSq)
        if (mag > 1e-10f) {
            for (i in vec.indices) vec[i] /= mag
        }
    }
}
