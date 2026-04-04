package com.xiaomo.signal

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SignalChannel private constructor(
    private val config: SignalConfig
) {

    private val _eventFlow = MutableSharedFlow<ChannelEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<ChannelEvent> = _eventFlow

    private var client: SignalClient? = null
    private var gateway: SignalGateway? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun startInternal() {
        val phone = config.phoneNumber
        if (phone.isBlank()) throw IllegalArgumentException("Phone number is required")

        val baseUrl = config.httpUrl.trimEnd('/') + ":" + config.httpPort
        val c = SignalClient(baseUrl, phone)
        client = c

        // Health check
        c.about().getOrThrow()
        Log.d(TAG, "signal-cli daemon reachable at $baseUrl")

        val internalFlow = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 64)
        val gw = SignalGateway(c, phone, internalFlow)
        gateway = gw
        gw.start()

        scope.launch {
            internalFlow.collect { event -> handleGatewayEvent(event) }
        }
    }

    private suspend fun handleGatewayEvent(event: SignalEvent) {
        when (event) {
            is SignalEvent.Connected -> {
                _eventFlow.emit(ChannelEvent.Connected)
            }
            is SignalEvent.Error -> {
                _eventFlow.emit(ChannelEvent.Error(event.exception))
            }
            is SignalEvent.Message -> {
                _eventFlow.emit(
                    ChannelEvent.Message(
                        sourceNumber = event.sourceNumber,
                        sourceName = event.sourceName,
                        chatId = event.chatId,
                        chatType = event.chatType,
                        text = event.text,
                        timestamp = event.timestamp
                    )
                )
            }
        }
    }

    fun isConnected(): Boolean = gateway?.isConnected == true
    fun getPhoneNumber(): String = config.phoneNumber
    fun getClient(): SignalClient? = client

    suspend fun sendMessage(recipient: String, text: String): Result<JsonObject> {
        return client?.sendMessage(recipient, text)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun sendTypingIndicator(recipient: String): Result<Unit> {
        return client?.sendTypingIndicator(recipient)
            ?: Result.failure(Exception("Client not initialized"))
    }

    suspend fun sendReaction(
        recipient: String,
        emoji: String,
        targetAuthor: String,
        targetTimestamp: Long
    ): Result<Unit> {
        return client?.sendReaction(recipient, emoji, targetAuthor, targetTimestamp)
            ?: Result.failure(Exception("Client not initialized"))
    }

    private fun stopInternal() {
        scope.cancel()
        gateway?.stop()
        gateway = null
        client = null
    }

    companion object {
        private const val TAG = "SignalChannel"
        private var instance: SignalChannel? = null

        suspend fun start(context: Context, config: SignalConfig): Result<SignalChannel> {
            return try {
                val channel = SignalChannel(config)
                channel.startInternal()
                instance = channel
                Log.d(TAG, "Signal channel started")
                Result.success(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Signal channel: ${e.message}", e)
                Result.failure(e)
            }
        }

        fun stop() {
            instance?.stopInternal()
            instance = null
            Log.d(TAG, "Signal channel stopped")
        }

        fun getInstance(): SignalChannel? = instance
    }

    sealed class ChannelEvent {
        data object Connected : ChannelEvent()
        data class Error(val error: Throwable) : ChannelEvent()
        data class Message(
            val sourceNumber: String,
            val sourceName: String,
            val chatId: String,
            val chatType: String,
            val text: String,
            val timestamp: Long
        ) : ChannelEvent()
    }
}
