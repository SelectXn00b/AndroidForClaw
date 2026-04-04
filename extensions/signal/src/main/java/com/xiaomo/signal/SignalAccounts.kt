package com.xiaomo.signal

import android.util.Log

class SignalAccounts {
    companion object {
        private const val TAG = "SignalAccounts"
        private const val DEFAULT_ACCOUNT_ID = "default"
    }

    data class SignalAccount(
        val id: String,
        val config: SignalConfig
    )

    fun resolveAccount(baseConfig: SignalConfig, accountId: String? = null): SignalAccount {
        val id = accountId?.takeIf { it.isNotBlank() } ?: DEFAULT_ACCOUNT_ID
        Log.d(TAG, "Resolving account: $id")
        return SignalAccount(id = id, config = baseConfig)
    }

    fun isAccountConfigured(config: SignalConfig): Boolean {
        return config.enabled && config.phoneNumber.isNotBlank()
    }
}
