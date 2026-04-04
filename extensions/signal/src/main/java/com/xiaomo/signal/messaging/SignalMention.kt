package com.xiaomo.signal.messaging

// Signal does not support bot mentions in the same way as other platforms.
// This is a no-op placeholder to maintain the consistent channel interface.
object SignalMention {

    fun isMentioned(text: String, phoneNumber: String): Boolean {
        // Signal doesn't have @mention syntax for bots
        return false
    }

    fun stripMention(text: String, phoneNumber: String): String {
        return text
    }
}
