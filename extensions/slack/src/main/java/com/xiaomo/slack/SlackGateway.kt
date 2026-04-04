package com.xiaomo.slack

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.*

class SlackGateway(
    private val client: SlackClient,
    private val appToken: String,
    private val botId: String,
    private val eventFlow: MutableSharedFlow<SlackEvent>
) {

    companion object {
        private const val TAG = "SlackGateway"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val wsClient = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null
    @Volatile var isConnected = false
        private set

    fun start() {
        scope.launch {
            connect()
        }
    }

    private suspend fun connect() {
        try {
            val wssUrl = client.openSocketConnection(appToken).getOrThrow()
            Log.d(TAG, "Got WebSocket URL: ${wssUrl.take(50)}...")

            val request = Request.Builder().url(wssUrl).build()
            webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    isConnected = true
                    scope.launch { eventFlow.emit(SlackEvent.Connected) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { handleMessage(text) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    isConnected = false
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    isConnected = false
                    scope.launch { reconnect() }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    isConnected = false
                    scope.launch {
                        eventFlow.emit(SlackEvent.Error(t))
                        reconnect()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            eventFlow.emit(SlackEvent.Error(e))
            reconnect()
        }
    }

    private suspend fun reconnect() {
        delay(5000)
        if (scope.isActive) {
            Log.d(TAG, "Reconnecting...")
            connect()
        }
    }

    private suspend fun handleMessage(text: String) {
        try {
            val envelope = gson.fromJson(text, JsonObject::class.java) ?: return
            val envelopeId = envelope.get("envelope_id")?.asString

            // ACK immediately (must respond within 3 seconds)
            if (envelopeId != null) {
                val ack = JsonObject().apply { addProperty("envelope_id", envelopeId) }
                webSocket?.send(gson.toJson(ack))
            }

            val type = envelope.get("type")?.asString ?: return

            when (type) {
                "events_api" -> {
                    val payload = envelope.getAsJsonObject("payload") ?: return
                    val event = payload.getAsJsonObject("event") ?: return
                    handleEvent(event)
                }
                "disconnect" -> {
                    Log.d(TAG, "Received disconnect, reconnecting...")
                    webSocket?.close(1000, "disconnect requested")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    private suspend fun handleEvent(event: JsonObject) {
        val eventType = event.get("type")?.asString ?: return
        if (eventType != "message") return

        // Ignore messages with subtypes (edits, joins, etc.) except thread_broadcast
        val subtype = event.get("subtype")?.asString
        if (subtype != null && subtype != "thread_broadcast") return

        // Ignore bot messages
        if (event.has("bot_id")) return
        val userId = event.get("user")?.asString ?: return
        if (userId == botId) return

        val text = event.get("text")?.asString ?: return
        val channelId = event.get("channel")?.asString ?: return
        val ts = event.get("ts")?.asString ?: return
        val threadTs = event.get("thread_ts")?.asString
        val channelType = event.get("channel_type")?.asString ?: "channel"

        eventFlow.emit(
            SlackEvent.Message(
                userId = userId,
                channelId = channelId,
                channelType = if (channelType == "im") "direct" else "group",
                text = text,
                ts = ts,
                threadTs = threadTs,
                timestamp = (ts.substringBefore('.').toLongOrNull() ?: 0L)
            )
        )
    }

    fun stop() {
        isConnected = false
        webSocket?.close(1000, "Shutting down")
        webSocket = null
        scope.cancel()
    }
}

sealed class SlackEvent {
    data object Connected : SlackEvent()
    data class Error(val exception: Throwable) : SlackEvent()
    data class Message(
        val userId: String,
        val channelId: String,
        val channelType: String,
        val text: String,
        val ts: String,
        val threadTs: String?,
        val timestamp: Long
    ) : SlackEvent()
}
