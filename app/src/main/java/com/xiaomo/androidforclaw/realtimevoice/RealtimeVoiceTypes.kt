package com.xiaomo.androidforclaw.realtimevoice

enum class RealtimeVoiceRole { SYSTEM, USER, ASSISTANT }
enum class RealtimeVoiceCloseReason { NORMAL, ERROR, TIMEOUT, CANCELLED }

data class RealtimeVoiceTool(val name: String, val description: String, val parameters: Map<String, Any?>? = null)
data class RealtimeVoiceToolCallEvent(val callId: String, val toolName: String, val arguments: String)

interface RealtimeVoiceBridgeCallbacks {
    fun onAudio(data: ByteArray)
    fun onTranscript(role: RealtimeVoiceRole, text: String, isFinal: Boolean)
    fun onToolCall(event: RealtimeVoiceToolCallEvent)
    fun onError(error: Throwable)
    fun onClose(reason: RealtimeVoiceCloseReason)
}

interface RealtimeVoiceBridge {
    val isConnected: Boolean
    suspend fun connect()
    fun sendAudio(data: ByteArray)
    fun setMediaTimestamp(timestampMs: Long)
    fun submitToolResult(callId: String, result: String)
    fun acknowledgeMark(markId: String)
    fun close(reason: RealtimeVoiceCloseReason = RealtimeVoiceCloseReason.NORMAL)
}

interface RealtimeVoiceProvider {
    val id: String
    val aliases: List<String>
    val label: String?
    suspend fun createBridge(callbacks: RealtimeVoiceBridgeCallbacks, tools: List<RealtimeVoiceTool>? = null): RealtimeVoiceBridge
}
