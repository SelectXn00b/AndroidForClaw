package com.xiaomo.slack

import android.util.Log

class SlackAccounts {
    companion object {
        private const val TAG = "SlackAccounts"
        private const val DEFAULT_ACCOUNT_ID = "default"
    }

    data class SlackAccount(
        val id: String,
        val config: SlackConfig
    )

    fun resolveAccount(baseConfig: SlackConfig, accountId: String? = null): SlackAccount {
        val id = accountId?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCOUNT_ID
        Log.d(TAG, "Resolving account: $id")
        return SlackAccount(id = id, config = baseConfig)
    }

    fun isAccountConfigured(config: SlackConfig): Boolean {
        return config.enabled && config.botToken.isNotBlank()
    }
}
