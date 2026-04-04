package com.xiaomo.telegram.messaging

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.telegram.TelegramClient

class TelegramSender(private val client: TelegramClient) {
    companion object {
        private const val TAG = "TelegramSender"
        private const val MAX_MESSAGE_LENGTH = 4096

        fun splitMessageIntoChunks(text: String, maxLength: Int = MAX_MESSAGE_LENGTH - 100): List<String> {
            if (text.length <= maxLength) return listOf(text)
            val chunks = mutableListOf<String>()
            var remaining = text
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxLength) {
                    chunks.add(remaining)
                    break
                }
                var splitAt = remaining.lastIndexOf('\n', maxLength)
                if (splitAt <= 0) splitAt = remaining.lastIndexOf(". ", maxLength)
                if (splitAt <= 0) splitAt = remaining.lastIndexOf(' ', maxLength)
                if (splitAt <= 0) splitAt = maxLength
                chunks.add(remaining.substring(0, splitAt))
                remaining = remaining.substring(splitAt).trimStart()
            }
            return chunks
        }
    }

    suspend fun send(
        chatId: String,
        text: String,
        replyToMessageId: Long? = null
    ): Result<JsonObject> {
        Log.d(TAG, "Sending to $chatId: ${text.take(50)}")
        return client.sendMessage(chatId, text, replyToMessageId)
    }

    suspend fun sendChunked(
        chatId: String,
        text: String,
        replyToMessageId: Long? = null
    ): List<Result<JsonObject>> {
        val chunks = splitMessageIntoChunks(text)
        return chunks.mapIndexed { index, chunk ->
            val replyTo = if (index == 0) replyToMessageId else null
            send(chatId, chunk, replyTo)
        }
    }

    suspend fun sendPhoto(
        chatId: String,
        photoUrl: String,
        caption: String? = null,
        replyToMessageId: Long? = null
    ): Result<JsonObject> {
        Log.d(TAG, "Sending photo to $chatId: $photoUrl")
        return client.sendPhoto(chatId, photoUrl, caption, replyToMessageId)
    }
}
