package com.xiaomo.telegram.messaging

import android.util.Log
import com.xiaomo.telegram.TelegramClient

class TelegramReactions(private val client: TelegramClient) {
    companion object {
        private const val TAG = "TelegramReactions"
        const val EMOJI_THINKING = "\uD83E\uDD14"
        const val EMOJI_CHECK = "✅"
        const val EMOJI_CROSS = "❌"
        const val EMOJI_EYES = "\uD83D\uDC40"
        const val EMOJI_THUMBS_UP = "\uD83D\uDC4D"
    }

    suspend fun add(chatId: String, messageId: Long, emoji: String): Result<Unit> {
        Log.d(TAG, "Adding reaction $emoji to $messageId in $chatId")
        return client.setMessageReaction(chatId, messageId, emoji)
    }

    suspend fun remove(chatId: String, messageId: Long): Result<Unit> {
        Log.d(TAG, "Removing reactions from $messageId in $chatId")
        return client.removeMessageReaction(chatId, messageId)
    }

    suspend fun addThinking(chatId: String, messageId: Long): Result<Unit> =
        add(chatId, messageId, EMOJI_THINKING)

    suspend fun addCheck(chatId: String, messageId: Long): Result<Unit> =
        add(chatId, messageId, EMOJI_CHECK)

    suspend fun addCross(chatId: String, messageId: Long): Result<Unit> =
        add(chatId, messageId, EMOJI_CROSS)
}
