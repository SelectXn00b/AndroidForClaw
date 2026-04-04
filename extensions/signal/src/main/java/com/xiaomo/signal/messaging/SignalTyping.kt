package com.xiaomo.signal.messaging

import android.util.Log
import com.xiaomo.signal.SignalClient
import kotlinx.coroutines.*

class SignalTyping(private val client: SignalClient) {
    companion object {
        private const val TAG = "SignalTyping"
        private const val RENEWAL_INTERVAL_MS = 4000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    suspend fun trigger(recipient: String) {
        client.sendTypingIndicator(recipient)
    }

    fun startContinuous(recipient: String) {
        activeJobs[recipient]?.cancel()
        activeJobs[recipient] = scope.launch {
            while (isActive) {
                try {
                    trigger(recipient)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Typing indicator failed: ${e.message}")
                }
                delay(RENEWAL_INTERVAL_MS)
            }
        }
    }

    fun stopContinuous(recipient: String) {
        activeJobs.remove(recipient)?.cancel()
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
