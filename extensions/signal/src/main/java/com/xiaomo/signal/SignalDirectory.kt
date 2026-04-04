package com.xiaomo.signal

import android.util.Log

// signal-cli has limited directory/lookup capabilities.
// This provides a basic interface for consistency with other channels.
class SignalDirectory(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalDirectory"
    }

    suspend fun lookupUser(phoneNumber: String): String? {
        // signal-cli doesn't provide contact name lookup via REST API
        Log.d(TAG, "Looking up user: $phoneNumber")
        return phoneNumber
    }

    suspend fun lookupGroup(groupId: String): String? {
        Log.d(TAG, "Looking up group: $groupId")
        return null
    }
}
