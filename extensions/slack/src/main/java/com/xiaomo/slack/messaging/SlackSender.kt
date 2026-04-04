package com.xiaomo.slack.messaging

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.slack.SlackClient

class SlackSender(private val client: SlackClient) {
    companion object {
        private const val TAG = "SlackSender"
        private const val MAX_MESSAGE_LENGTH = 4000

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
        channel: String,
        text: String,
        threadTs: String? = null
    ): Result<JsonObject> {
        Log.d(TAG, "Sending to $channel: ${text.take(50)}")
        return client.postMessage(channel, text, threadTs)
    }

    suspend fun sendChunked(
        channel: String,
        text: String,
        threadTs: String? = null
    ): List<Result<JsonObject>> {
        val chunks = splitMessageIntoChunks(text)
        return chunks.map { chunk -> send(channel, chunk, threadTs) }
    }
}
