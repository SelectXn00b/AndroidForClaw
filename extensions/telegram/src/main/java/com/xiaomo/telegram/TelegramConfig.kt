package com.xiaomo.telegram

data class TelegramConfig(
    val enabled: Boolean = false,
    val botToken: String = "",
    val botId: String = "",
    val botUsername: String = "",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50,
    val webhookUrl: String? = null,
    val model: String? = null
)
