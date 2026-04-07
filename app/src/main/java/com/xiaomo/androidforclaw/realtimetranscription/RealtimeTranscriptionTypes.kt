package com.xiaomo.androidforclaw.realtimetranscription

typealias RealtimeTranscriptionProviderId = String

data class TranscriptionSegment(val text: String, val startMs: Long? = null, val endMs: Long? = null, val isFinal: Boolean = false)

interface RealtimeTranscriptionSessionCallbacks {
    fun onTranscript(segment: TranscriptionSegment)
    fun onError(error: Throwable)
    fun onClose()
}

interface RealtimeTranscriptionSession {
    val providerId: RealtimeTranscriptionProviderId
    val isConnected: Boolean
    suspend fun connect()
    fun sendAudio(data: ByteArray)
    fun close()
}

interface RealtimeTranscriptionProvider {
    val id: RealtimeTranscriptionProviderId
    val aliases: List<String>
    val label: String?
    suspend fun createSession(callbacks: RealtimeTranscriptionSessionCallbacks): RealtimeTranscriptionSession
}
