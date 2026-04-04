package com.xiaomo.telegram.messaging

import android.util.Log
import com.xiaomo.telegram.TelegramClient
import kotlinx.coroutines.*

class TelegramTyping(private val client: TelegramClient) {
    companion object {
        private const val TAG = "TelegramTyping"
        private const val RENEWAL_INTERVAL_MS = 4000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    suspend fun trigger(chatId: String) {
        client.sendChatAction(chatId, "typing")
    }

    fun startContinuous(chatId: String) {
        activeJobs[chatId]?.cancel()
        activeJobs[chatId] = scope.launch {
            while (isActive) {
                try {
                    trigger(chatId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Typing indicator failed: ${e.message}")
                }
                delay(RENEWAL_INTERVAL_MS)
            }
        }
    }

    fun stopContinuous(chatId: String) {
        activeJobs.remove(chatId)?.cancel()
    }

    fun stopAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun cleanup() {
        stopAll()
        scope.cancel()
    }
}
