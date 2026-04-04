package com.xiaomo.signal

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class SignalGateway(
    private val client: SignalClient,
    private val phoneNumber: String,
    private val eventFlow: MutableSharedFlow<SignalEvent>
) {

    companion object {
        private const val TAG = "SignalGateway"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    @Volatile var isConnected = false
        private set

    fun start() {
        pollJob = scope.launch {
            isConnected = true
            eventFlow.emit(SignalEvent.Connected)
            Log.d(TAG, "Starting poll loop for $phoneNumber")

            while (isActive) {
                try {
                    val result = client.receive()
                    result.onSuccess { messages ->
                        for (element in messages) {
                            val msg = element.asJsonObject
                            handleMessage(msg)
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "receive() failed: ${e.message}")
                        eventFlow.emit(SignalEvent.Error(e))
                        delay(5000)
                    }
                    // Small delay to avoid hammering the API
                    delay(1000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Poll loop error: ${e.message}")
                    eventFlow.emit(SignalEvent.Error(e))
                    delay(5000)
                }
            }
        }
    }

    private suspend fun handleMessage(msg: JsonObject) {
        val envelope = msg.getAsJsonObject("envelope") ?: return
        val sourceNumber = envelope.get("sourceNumber")?.asString ?: return
        val sourceName = envelope.get("sourceName")?.asString ?: sourceNumber

        // Skip own messages
        if (sourceNumber == phoneNumber) return

        val dataMessage = envelope.getAsJsonObject("dataMessage") ?: return
        val text = dataMessage.get("message")?.asString ?: return
        val timestamp = dataMessage.get("timestamp")?.asLong ?: (System.currentTimeMillis())

        val groupInfo = dataMessage.getAsJsonObject("groupInfo")
        val chatType = if (groupInfo != null) "group" else "direct"
        val chatId = if (groupInfo != null) {
            groupInfo.get("groupId")?.asString ?: sourceNumber
        } else {
            sourceNumber
        }

        eventFlow.emit(
            SignalEvent.Message(
                sourceNumber = sourceNumber,
                sourceName = sourceName,
                chatId = chatId,
                chatType = chatType,
                text = text,
                timestamp = timestamp
            )
        )
    }

    fun stop() {
        isConnected = false
        pollJob?.cancel()
        scope.cancel()
        Log.d(TAG, "Gateway stopped")
    }
}

sealed class SignalEvent {
    data object Connected : SignalEvent()
    data class Error(val exception: Throwable) : SignalEvent()
    data class Message(
        val sourceNumber: String,
        val sourceName: String,
        val chatId: String,
        val chatType: String,
        val text: String,
        val timestamp: Long
    ) : SignalEvent()
}
