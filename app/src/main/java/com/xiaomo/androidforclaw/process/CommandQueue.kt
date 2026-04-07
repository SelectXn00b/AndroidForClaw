package com.xiaomo.androidforclaw.process

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/command-queue.ts
 *
 * Serialises command execution so only one command runs at a time
 * within a given session scope.
 */
class CommandQueue {

    private val mutex = Mutex()
    private val history = mutableListOf<ExecResult>()

    suspend fun <T> enqueue(block: suspend () -> T): T = mutex.withLock {
        block()
    }

    suspend fun lastResult(): ExecResult? = mutex.withLock { history.lastOrNull() }

    suspend fun addToHistory(result: ExecResult) = mutex.withLock {
        history.add(result)
    }

    suspend fun clearHistory() = mutex.withLock {
        history.clear()
    }
}
