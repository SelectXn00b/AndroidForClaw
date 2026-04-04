package com.xiaomo.telegram

import android.util.Log

class TelegramDirectory(private val client: TelegramClient) {
    companion object {
        private const val TAG = "TelegramDirectory"
    }

    data class ChatInfo(
        val id: String,
        val title: String?,
        val type: String,
        val username: String?
    )

    suspend fun lookupChat(chatId: String): ChatInfo? {
        return try {
            val result = client.getChat(chatId)
            result.getOrNull()?.let { json ->
                ChatInfo(
                    id = json.get("id")?.asString ?: chatId,
                    title = json.get("title")?.asString
                        ?: json.get("first_name")?.asString,
                    type = json.get("type")?.asString ?: "unknown",
                    username = json.get("username")?.asString
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup chat $chatId: ${e.message}")
            null
        }
    }

    suspend fun lookupUser(userId: String): String? {
        return lookupChat(userId)?.let { info ->
            info.title ?: info.username ?: info.id
        }
    }

    suspend fun lookupGroup(groupId: String): String? {
        return lookupChat(groupId)?.title
    }
}
