package com.xiaomo.slack

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

class SlackClient(private val botToken: String) {

    companion object {
        private const val TAG = "SlackClient"
        private const val BASE_URL = "https://slack.com/api"
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun apiPost(method: String, body: JsonObject, token: String = botToken): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/$method")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                if (json.get("ok")?.asBoolean != true) {
                    val error = json.get("error")?.asString ?: "Unknown error"
                    return@withContext Result.failure(Exception("Slack API: $error"))
                }
                Result.success(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun openSocketConnection(appToken: String): Result<String> {
        val body = JsonObject()
        return apiPost("apps.connections.open", body, appToken).map {
            it.get("url")?.asString ?: throw Exception("No URL in response")
        }
    }

    suspend fun authTest(): Result<JsonObject> {
        return apiPost("auth.test", JsonObject())
    }

    suspend fun postMessage(
        channel: String,
        text: String,
        threadTs: String? = null
    ): Result<JsonObject> {
        val body = JsonObject().apply {
            addProperty("channel", channel)
            addProperty("text", text)
            if (threadTs != null) addProperty("thread_ts", threadTs)
        }
        return apiPost("chat.postMessage", body)
    }

    suspend fun addReaction(channel: String, name: String, timestamp: String): Result<Unit> {
        val body = JsonObject().apply {
            addProperty("channel", channel)
            addProperty("name", name)
            addProperty("timestamp", timestamp)
        }
        return apiPost("reactions.add", body).map { }
    }

    suspend fun removeReaction(channel: String, name: String, timestamp: String): Result<Unit> {
        val body = JsonObject().apply {
            addProperty("channel", channel)
            addProperty("name", name)
            addProperty("timestamp", timestamp)
        }
        return apiPost("reactions.remove", body).map { }
    }

    suspend fun usersInfo(userId: String): Result<JsonObject> {
        val body = JsonObject().apply { addProperty("user", userId) }
        return apiPost("users.info", body).map {
            it.getAsJsonObject("user") ?: JsonObject()
        }
    }

    suspend fun conversationsInfo(channelId: String): Result<JsonObject> {
        val body = JsonObject().apply { addProperty("channel", channelId) }
        return apiPost("conversations.info", body).map {
            it.getAsJsonObject("channel") ?: JsonObject()
        }
    }
}
