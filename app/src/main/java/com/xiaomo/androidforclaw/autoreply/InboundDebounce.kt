package com.xiaomo.androidforclaw.autoreply

import kotlinx.coroutines.*

class InboundDebounceController(
    private val debounceMs: Long = 500,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var pendingJob: Job? = null
    private var pendingMessage: String? = null

    fun debounce(message: String, onReady: (String) -> Unit) {
        pendingJob?.cancel()
        pendingMessage = message
        pendingJob = scope.launch {
            delay(debounceMs)
            pendingMessage?.let { onReady(it) }
            pendingMessage = null
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingMessage = null
    }
}
