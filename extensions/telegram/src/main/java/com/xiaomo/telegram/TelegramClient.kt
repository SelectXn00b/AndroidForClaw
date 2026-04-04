package com.xiaomo.telegram

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TelegramClient(private val botToken: String) {

    private val baseUrl = "https://api.telegram.org/bot$botToken"
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun apiGet(method: String, params: Map<String, Any?> = emptyMap()): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append("$baseUrl/$method")
                    val filtered = params.filterValues { it != null }
                    if (filtered.isNotEmpty()) {
                        append("?")
                        append(filtered.entries.joinToString("&") { "${it.key}=${it.value}" })
                    }
                }
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                }
                val json = gson.fromJson(body, JsonObject::class.java)
                if (json.get("ok")?.asBoolean != true) {
                    val desc = json.get("description")?.asString ?: "Unknown error"
                    return@withContext Result.failure(Exception("Telegram API: $desc"))
                }
                Result.success(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun apiPost(method: String, body: JsonObject): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/$method")
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                if (json.get("ok")?.asBoolean != true) {
                    val desc = json.get("description")?.asString ?: "Unknown error"
                    return@withContext Result.failure(Exception("Telegram API: $desc"))
                }
                Result.success(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getMe(): Result<JsonObject> =
        apiGet("getMe").map { it.getAsJsonObject("result") ?: JsonObject() }

    suspend fun getUpdates(offset: Long?, timeout: Int = 30): Result<JsonArray> {
        val params = mutableMapOf<String, Any?>("timeout" to timeout)
        if (offset != null) params["offset"] = offset
        return apiGet("getUpdates", params).map {
            it.getAsJsonArray("result") ?: JsonArray()
        }
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        replyToMessageId: Long? = null,
        parseMode: String? = "Markdown"
    ): Result<JsonObject> {
        val body = JsonObject().apply {
            addProperty("chat_id", chatId)
            addProperty("text", text)
            if (parseMode != null) addProperty("parse_mode", parseMode)
            if (replyToMessageId != null) {
                val replyParams = JsonObject().apply {
                    addProperty("message_id", replyToMessageId)
                }
                add("reply_parameters", replyParams)
            }
        }
        // Try with Markdown first, fallback to plain text on parse error
        val result = apiPost("sendMessage", body)
        if (result.isFailure && parseMode == "Markdown") {
            val error = result.exceptionOrNull()?.message ?: ""
            if (error.contains("parse", ignoreCase = true) || error.contains("entities", ignoreCase = true)) {
                Log.d(TAG, "Markdown parse failed, retrying as plain text")
                body.remove("parse_mode")
                return apiPost("sendMessage", body).map {
                    it.getAsJsonObject("result") ?: JsonObject()
                }
            }
        }
        return result.map { it.getAsJsonObject("result") ?: JsonObject() }
    }

    suspend fun sendChatAction(chatId: String, action: String = "typing"): Result<Unit> {
        val body = JsonObject().apply {
            addProperty("chat_id", chatId)
            addProperty("action", action)
        }
        return apiPost("sendChatAction", body).map { }
    }

    suspend fun setMessageReaction(chatId: String, messageId: Long, emoji: String): Result<Unit> {
        val body = JsonObject().apply {
            addProperty("chat_id", chatId)
            addProperty("message_id", messageId)
            val reaction = JsonObject().apply {
                addProperty("type", "emoji")
                addProperty("emoji", emoji)
            }
            val arr = JsonArray()
            arr.add(reaction)
            add("reaction", arr)
        }
        return apiPost("setMessageReaction", body).map { }
    }

    suspend fun removeMessageReaction(chatId: String, messageId: Long): Result<Unit> {
        val body = JsonObject().apply {
            addProperty("chat_id", chatId)
            addProperty("message_id", messageId)
            add("reaction", JsonArray()) // empty array clears reactions
        }
        return apiPost("setMessageReaction", body).map { }
    }

    suspend fun getChat(chatId: String): Result<JsonObject> {
        val body = JsonObject().apply { addProperty("chat_id", chatId) }
        return apiPost("getChat", body).map {
            it.getAsJsonObject("result") ?: JsonObject()
        }
    }

    suspend fun sendPhoto(
        chatId: String,
        photoUrl: String,
        caption: String? = null,
        replyToMessageId: Long? = null
    ): Result<JsonObject> {
        val body = JsonObject().apply {
            addProperty("chat_id", chatId)
            addProperty("photo", photoUrl)
            if (caption != null) addProperty("caption", caption)
            if (replyToMessageId != null) {
                val replyParams = JsonObject().apply {
                    addProperty("message_id", replyToMessageId)
                }
                add("reply_parameters", replyParams)
            }
        }
        return apiPost("sendPhoto", body).map {
            it.getAsJsonObject("result") ?: JsonObject()
        }
    }

    companion object {
        private const val TAG = "TelegramClient"
    }
}
