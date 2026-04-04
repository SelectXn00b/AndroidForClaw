package com.xiaomo.slack.messaging

import android.util.Log
import com.xiaomo.slack.SlackClient

class SlackReactions(private val client: SlackClient) {
    companion object {
        private const val TAG = "SlackReactions"
        const val THINKING = "thinking_face"
        const val CHECK = "white_check_mark"
        const val CROSS = "x"
        const val EYES = "eyes"
        const val THUMBS_UP = "thumbsup"
    }

    suspend fun add(channel: String, name: String, timestamp: String): Result<Unit> {
        Log.d(TAG, "Adding reaction :$name: to $timestamp in $channel")
        return client.addReaction(channel, name, timestamp)
    }

    suspend fun remove(channel: String, name: String, timestamp: String): Result<Unit> {
        Log.d(TAG, "Removing reaction :$name: from $timestamp in $channel")
        return client.removeReaction(channel, name, timestamp)
    }

    suspend fun addThinking(channel: String, timestamp: String): Result<Unit> =
        add(channel, THINKING, timestamp)

    suspend fun addCheck(channel: String, timestamp: String): Result<Unit> =
        add(channel, CHECK, timestamp)

    suspend fun addCross(channel: String, timestamp: String): Result<Unit> =
        add(channel, CROSS, timestamp)
}
