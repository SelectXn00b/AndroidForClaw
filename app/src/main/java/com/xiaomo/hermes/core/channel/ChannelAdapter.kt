package com.xiaomo.hermes.core.channel

/**
 * Abstraction over per-channel differences in the message processing pipeline.
 * Each channel (Discord, Telegram, Slack, Signal) implements this interface
 * so that [ChannelMessageProcessor] can run the common pipeline.
 */
interface ChannelAdapter {
    val channelName: String       // "discord" / "telegram" / "slack" / "signal"
    val sessionPrefix: String     // used as session key prefix, e.g. "telegram"
    val messageCharLimit: Int     // max characters per outbound message chunk
    val supportsReactions: Boolean
    val supportsTyping: Boolean

    // --- Reactions ---
    suspend fun addThinkingReaction()
    suspend fun removeThinkingReaction()
    suspend fun addCompletionReaction()
    suspend fun addErrorReaction()

    // --- Typing indicator ---
    fun startTyping()
    fun stopTyping()

    // --- Outbound messaging ---
    suspend fun sendMessageChunk(text: String, isFirstChunk: Boolean)
    suspend fun sendErrorMessage(error: String)

    // --- Message context ---
    fun isGroupContext(): Boolean
    fun getUserMessage(): String
    fun getSessionKey(): String   // e.g. "telegram_${chatId}"
    fun getSenderId(): String = ""   // sender's open_id / user_id for routing
    fun getChatId(): String = ""     // chat/group ID for routing
    /** Account identifier for multi-account routing (e.g. appId for feishu, bot token for telegram) */
    val accountId: String get() = ""

    // --- System prompt ---
    fun buildSystemPrompt(): String
}
