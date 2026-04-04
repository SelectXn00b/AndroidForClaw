package com.xiaomo.telegram

import android.util.Log

class TelegramAccounts {
    companion object {
        private const val TAG = "TelegramAccounts"
        private const val DEFAULT_ACCOUNT_ID = "default"
    }

    data class TelegramAccount(
        val id: String,
        val config: TelegramConfig
    )

    fun resolveAccount(baseConfig: TelegramConfig, accountId: String? = null): TelegramAccount {
        val id = accountId?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCOUNT_ID
        Log.d(TAG, "Resolving account: $id")
        return TelegramAccount(id = id, config = baseConfig)
    }

    fun isAccountConfigured(config: TelegramConfig): Boolean {
        return config.enabled && config.botToken.isNotBlank()
    }
}
