package com.xiaomo.signal.messaging

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.signal.SignalClient

class SignalSender(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalSender"
        private const val MAX_MESSAGE_LENGTH = 2000

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

    suspend fun send(recipient: String, text: String): Result<JsonObject> {
        Log.d(TAG, "Sending to $recipient: ${text.take(50)}")
        return client.sendMessage(recipient, text)
    }

    suspend fun sendChunked(recipient: String, text: String): List<Result<JsonObject>> {
        val chunks = splitMessageIntoChunks(text)
        return chunks.map { chunk -> send(recipient, chunk) }
    }
}
