package com.xiaomo.signal

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

class SignalClient(
    private val baseUrl: String,
    private val phoneNumber: String
) {

    companion object {
        private const val TAG = "SignalClient"
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun receive(): Result<JsonArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/receive/$phoneNumber")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val array = gson.fromJson(body, JsonArray::class.java) ?: JsonArray()
            Result.success(array)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        recipient: String,
        message: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("message", message)
                addProperty("number", phoneNumber)
                val recipients = JsonArray()
                recipients.add(recipient)
                add("recipients", recipients)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/v2/send")
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java) ?: JsonObject()
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTypingIndicator(recipient: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("recipient", recipient)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/v1/typing-indicator/$phoneNumber")
                .put(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendReaction(
        recipient: String,
        emoji: String,
        targetAuthor: String,
        targetTimestamp: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                addProperty("recipient", recipient)
                addProperty("reaction", emoji)
                addProperty("target_author", targetAuthor)
                addProperty("target_timestamp", targetTimestamp)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/v1/reactions/$phoneNumber")
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun about(): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/about")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = gson.fromJson(body, JsonObject::class.java) ?: JsonObject()
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
