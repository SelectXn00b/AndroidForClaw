package com.xiaomo.signal

data class SignalConfig(
    val enabled: Boolean = false,
    val phoneNumber: String = "",
    val httpUrl: String = "http://127.0.0.1",
    val httpPort: Int = 8080,
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50,
    val model: String? = null
)
