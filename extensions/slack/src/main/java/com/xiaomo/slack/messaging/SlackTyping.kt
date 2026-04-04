package com.xiaomo.slack.messaging

import android.util.Log

// Slack Bot API does not support typing indicators.
// This is a no-op placeholder to maintain the consistent channel interface.
class SlackTyping {
    companion object {
        private const val TAG = "SlackTyping"
    }

    fun startContinuous(channelId: String) {
        Log.d(TAG, "Typing indicator not supported by Slack Bot API")
    }

    fun stopContinuous(channelId: String) {
        // no-op
    }

    fun stopAll() {
        // no-op
    }

    fun cleanup() {
        // no-op
    }
}
