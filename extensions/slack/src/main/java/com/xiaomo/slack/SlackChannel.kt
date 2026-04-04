package com.xiaomo.slack

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SlackChannel private constructor(
    private val config: SlackConfig
) {

    private val _eventFlow = MutableSharedFlow<ChannelEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<ChannelEvent> = _eventFlow

    private var client: SlackClient? = null
    private var gateway: SlackGateway? = null
    private var currentBotId: String? = null
    private var currentBotUsername: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun startInternal() {
        val token = config.botToken
        if (token.isBlank()) throw IllegalArgumentException("Bot token is required")
        val appToken = config.appToken
        if (appToken.isNullOrBlank()) throw IllegalArgumentException("App token is required for socket mode")

        val c = SlackClient(token)
        client = c

        // Get bot info via auth.test
        val authResult = c.authTest().getOrThrow()
        currentBotId = authResult.get("user_id")?.asString ?: ""
        currentBotUsername = authResult.get("user")?.asString ?: ""
        Log.d(TAG, "Bot info: id=$currentBotId username=$currentBotUsername")

        // Start gateway
        val internalFlow = MutableSharedFlow<SlackEvent>(extraBufferCapacity = 64)
        val gw = SlackGateway(c, appToken, currentBotId ?: "", internalFlow)
        gateway = gw
        gw.start()

        scope.launch {
            internalFlow.collect { event -> handleGatewayEvent(event) }
        }
    }

    private suspend fun handleGatewayEvent(event: SlackEvent) {
        when (event) {
            is SlackEvent.Connected -> {
                _eventFlow.emit(ChannelEvent.Connected)
            }
            is SlackEvent.Error -> {
                _eventFlow.emit(ChannelEvent.Error(event.exception))
            }
            is SlackEvent.Message -> {
                // Check mention requirement for group channels
                if (event.channelType == "group" && config.requireMention) {
                    val mentioned = event.text.contains("<@${currentBotId}>")
                    if (!mentioned) return
                }

                _eventFlow.emit(
                    ChannelEvent.Message(
                        userId = event.userId,
                        channelId = event.channelId,
                        channelType = event.channelType,
                        text = event.text,
                        ts = event.ts,
                        threadTs = event.threadTs,
                        timestamp = event.timestamp
                    )
                )
            }
        }
    }

    fun isConnected(): Boolean = gateway?.isConnected == true
    fun getBotId(): String? = currentBotId
    fun getBotUsername(): String? = currentBotUsername
    fun getClient(): SlackClient? = client

    suspend fun postMessage(
        channel: String,
        text: String,
        threadTs: String? = null
    ): Result<JsonObject> {
        return client?.postMessage(channel, text, threadTs)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun addReaction(channel: String, name: String, timestamp: String): Result<Unit> {
        return client?.addReaction(channel, name, timestamp)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun removeReaction(channel: String, name: String, timestamp: String): Result<Unit> {
        return client?.removeReaction(channel, name, timestamp)
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
        private const val TAG = "SlackChannel"
        private var instance: SlackChannel? = null

        suspend fun start(context: Context, config: SlackConfig): Result<SlackChannel> {
            return try {
                val channel = SlackChannel(config)
                channel.startInternal()
                instance = channel
                Log.d(TAG, "Slack channel started")
                Result.success(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Slack channel: ${e.message}", e)
                Result.failure(e)
            }
        }

        fun stop() {
            instance?.stopInternal()
            instance = null
            Log.d(TAG, "Slack channel stopped")
        }

        fun getInstance(): SlackChannel? = instance
    }

    sealed class ChannelEvent {
        data object Connected : ChannelEvent()
        data class Error(val error: Throwable) : ChannelEvent()
        data class Message(
            val userId: String,
            val channelId: String,
            val channelType: String,
            val text: String,
            val ts: String,
            val threadTs: String?,
            val timestamp: Long
        ) : ChannelEvent()
    }
}
