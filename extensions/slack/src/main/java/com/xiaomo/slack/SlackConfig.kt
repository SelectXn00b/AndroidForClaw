package com.xiaomo.slack

data class SlackConfig(
    val enabled: Boolean = false,
    val botToken: String = "",
    val appToken: String? = null,
    val signingSecret: String? = null,
    val mode: String = "socket",
    val botId: String = "",
    val botUsername: String = "",
    val dmPolicy: String = "open",
    val groupPolicy: String = "open",
    val requireMention: Boolean = true,
    val historyLimit: Int = 50,
    val model: String? = null
)
