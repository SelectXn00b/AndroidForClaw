package com.xiaomo.slack

import android.util.Log

class SlackDirectory(private val client: SlackClient) {
    companion object {
        private const val TAG = "SlackDirectory"
    }

    suspend fun lookupUser(userId: String): String? {
        return try {
            val result = client.usersInfo(userId)
            result.getOrNull()?.let { user ->
                user.getAsJsonObject("profile")?.get("display_name")?.asString?.takeIf { it.isNotBlank() }
                    ?: user.get("real_name")?.asString
                    ?: user.get("name")?.asString
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup user $userId: ${e.message}")
            null
        }
    }

    suspend fun lookupChannel(channelId: String): String? {
        return try {
            val result = client.conversationsInfo(channelId)
            result.getOrNull()?.get("name")?.asString
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup channel $channelId: ${e.message}")
            null
        }
    }

    suspend fun lookupGroup(groupId: String): String? = lookupChannel(groupId)
}
