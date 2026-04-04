package com.xiaomo.telegram

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class TelegramGateway(
    private val client: TelegramClient,
    private val botId: String,
    private val eventFlow: MutableSharedFlow<TelegramEvent>
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    @Volatile var isConnected = false
        private set

    fun start() {
        pollJob = scope.launch {
            var offset: Long? = null
            isConnected = true
            eventFlow.emit(TelegramEvent.Connected)
            Log.d(TAG, "Starting long-poll loop")

            while (isActive) {
                try {
                    val result = client.getUpdates(offset, timeout = 30)
                    result.onSuccess { updates ->
                        for (element in updates) {
                            val update = element.asJsonObject
                            val updateId = update.get("update_id")?.asLong ?: continue
                            offset = updateId + 1
                            val message = update.getAsJsonObject("message")
                                ?: update.getAsJsonObject("edited_message")
                                ?: update.getAsJsonObject("channel_post")
                                ?: continue
                            handleMessage(message)
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "getUpdates failed: ${e.message}")
                        eventFlow.emit(TelegramEvent.Error(e))
                        delay(5000)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Poll loop error: ${e.message}")
                    eventFlow.emit(TelegramEvent.Error(e))
                    delay(5000)
                }
            }
        }
    }

    private suspend fun handleMessage(message: JsonObject) {
        val from = message.getAsJsonObject("from") ?: return
        val senderId = from.get("id")?.asString ?: return
        if (senderId == botId) return
        if (from.get("is_bot")?.asBoolean == true) return

        val chat = message.getAsJsonObject("chat") ?: return
        val chatId = chat.get("id")?.asString ?: return
        val chatType = chat.get("type")?.asString ?: "private"

        val text = message.get("text")?.asString
            ?: message.get("caption")?.asString
            ?: return

        val messageId = message.get("message_id")?.asLong ?: return
        val senderName = buildString {
            append(from.get("first_name")?.asString ?: "")
            val lastName = from.get("last_name")?.asString
            if (!lastName.isNullOrEmpty()) append(" $lastName")
        }.ifEmpty { senderId }
        val senderUsername = from.get("username")?.asString

        // Parse mentions from entities
        val entities = message.getAsJsonArray("entities")
        val mentions = mutableListOf<String>()
        if (entities != null) {
            for (entity in entities) {
                val obj = entity.asJsonObject
                val type = obj.get("type")?.asString ?: continue
                if (type == "mention") {
                    val off = obj.get("offset")?.asInt ?: continue
                    val len = obj.get("length")?.asInt ?: continue
                    if (off + len <= text.length) {
                        mentions.add(text.substring(off, off + len))
                    }
                }
            }
        }

        val replyToMessageId = message.getAsJsonObject("reply_to_message")
            ?.get("message_id")?.asLong

        eventFlow.emit(
            TelegramEvent.Message(
                messageId = messageId,
                chatId = chatId,
                chatType = if (chatType == "private") "direct" else "group",
                senderId = senderId,
                senderName = senderName,
                senderUsername = senderUsername,
                content = text,
                mentions = mentions,
                replyToMessageId = replyToMessageId,
                timestamp = message.get("date")?.asLong ?: (System.currentTimeMillis() / 1000)
            )
        )
    }

    fun stop() {
        isConnected = false
        pollJob?.cancel()
        scope.cancel()
        Log.d(TAG, "Gateway stopped")
    }

    companion object {
        private const val TAG = "TelegramGateway"
    }
}

sealed class TelegramEvent {
    data object Connected : TelegramEvent()
    data class Error(val exception: Throwable) : TelegramEvent()
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
    ) : TelegramEvent()
}
