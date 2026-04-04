package com.xiaomo.signal.messaging

import android.util.Log
import com.xiaomo.signal.SignalClient

class SignalReactions(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalReactions"
        const val EMOJI_THINKING = "\uD83E\uDD14"
        const val EMOJI_CHECK = "✅"
        const val EMOJI_CROSS = "❌"
        const val EMOJI_THUMBS_UP = "\uD83D\uDC4D"
    }

    suspend fun add(
        recipient: String,
        emoji: String,
        targetAuthor: String,
        targetTimestamp: Long
    ): Result<Unit> {
        Log.d(TAG, "Adding reaction $emoji for $targetTimestamp")
        return client.sendReaction(recipient, emoji, targetAuthor, targetTimestamp)
    }

    suspend fun addThinking(recipient: String, targetAuthor: String, targetTimestamp: Long): Result<Unit> =
        add(recipient, EMOJI_THINKING, targetAuthor, targetTimestamp)

    suspend fun addCheck(recipient: String, targetAuthor: String, targetTimestamp: Long): Result<Unit> =
        add(recipient, EMOJI_CHECK, targetAuthor, targetTimestamp)

    suspend fun addCross(recipient: String, targetAuthor: String, targetTimestamp: Long): Result<Unit> =
        add(recipient, EMOJI_CROSS, targetAuthor, targetTimestamp)
}
