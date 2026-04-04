package com.xiaomo.telegram

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class TelegramChannel private constructor(
    private val config: TelegramConfig
) {

    private val _eventFlow = MutableSharedFlow<ChannelEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<ChannelEvent> = _eventFlow

    private var client: TelegramClient? = null
    private var gateway: TelegramGateway? = null
    private var currentBotId: String? = null
    private var currentBotUsername: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun startInternal() {
        val token = config.botToken
        if (token.isBlank()) throw IllegalArgumentException("Bot token is required")

        val c = TelegramClient(token)
        client = c

        val me = c.getMe().getOrThrow()
        currentBotId = me.get("id")?.asString ?: ""
        currentBotUsername = me.get("username")?.asString ?: ""
        Log.d(TAG, "Bot info: id=$currentBotId username=$currentBotUsername")

        val internalFlow = MutableSharedFlow<TelegramEvent>(extraBufferCapacity = 64)
        val gw = TelegramGateway(c, currentBotId ?: "", internalFlow)
        gateway = gw
        gw.start()

        scope.launch {
            internalFlow.collect { event -> handleGatewayEvent(event) }
        }
    }

    private suspend fun handleGatewayEvent(event: TelegramEvent) {
        when (event) {
            is TelegramEvent.Connected -> {
                _eventFlow.emit(ChannelEvent.Connected)
            }
            is TelegramEvent.Error -> {
                _eventFlow.emit(ChannelEvent.Error(event.exception))
            }
            is TelegramEvent.Message -> {
                if (event.chatType == "group" && config.requireMention) {
                    val botUsername = currentBotUsername
                    if (botUsername != null) {
                        val mentioned = event.mentions.any {
                            it.equals("@$botUsername", ignoreCase = true)
                        }
                        if (!mentioned) return
                    }
                }

                _eventFlow.emit(
                    ChannelEvent.Message(
                        messageId = event.messageId,
                        chatId = event.chatId,
                        chatType = event.chatType,
                        senderId = event.senderId,
                        senderName = event.senderName,
                        senderUsername = event.senderUsername,
                        content = event.content,
                        mentions = event.mentions,
                        replyToMessageId = event.replyToMessageId,
                        timestamp = event.timestamp
                    )
                )
            }
        }
    }

    fun isConnected(): Boolean = gateway?.isConnected == true
    fun getBotId(): String? = currentBotId
    fun getBotUsername(): String? = currentBotUsername
    fun getClient(): TelegramClient? = client

    suspend fun sendMessage(
        chatId: String,
        text: String,
        replyToMessageId: Long? = null
    ): Result<JsonObject> {
        return client?.sendMessage(chatId, text, replyToMessageId)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun sendChatAction(chatId: String, action: String = "typing"): Result<Unit> {
        return client?.sendChatAction(chatId, action)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun setMessageReaction(chatId: String, messageId: Long, emoji: String): Result<Unit> {
        return client?.setMessageReaction(chatId, messageId, emoji)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun removeMessageReaction(chatId: String, messageId: Long): Result<Unit> {
        return client?.removeMessageReaction(chatId, messageId)
            ?: Result.failure(Exception("Client not initialized"))
    }

    private fun stopInternal() {
        scope.cancel()
        gateway?.stop()
        gateway = null
        client = null
        currentBotId = null
        currentBotUsername = null
    }

    companion object {
        private const val TAG = "TelegramChannel"
        private var instance: TelegramChannel? = null

        suspend fun start(context: Context, config: TelegramConfig): Result<TelegramChannel> {
            return try {
                val channel = TelegramChannel(config)
                channel.startInternal()
                instance = channel
                Log.d(TAG, "Telegram channel started")
                Result.success(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Telegram channel: ${e.message}", e)
                Result.failure(e)
            }
        }

        fun stop() {
            instance?.stopInternal()
            instance = null
            Log.d(TAG, "Telegram channel stopped")
        }

        fun getInstance(): TelegramChannel? = instance
    }

    sealed class ChannelEvent {
        data object Connected : ChannelEvent()
        data class Error(val error: Throwable) : ChannelEvent()
        data class Message(
            val messageId: Long,
            val chatId: String,
            val chatType: String,
            val senderId: String,
            val senderName: String,
            val senderUsername: String?,
            val content: String,
            val mentions: List<String>,
            val replyToMessageId: Long?,
            val timestamp: Long
        ) : ChannelEvent()
    }
}
