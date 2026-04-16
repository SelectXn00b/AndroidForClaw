package com.xiaomo.hermes.process

import com.xiaomo.hermes.shared.resolveProcessScopedMap
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * OpenClaw module: process
 * Source: OpenClaw/src/process/command-queue.ts + lanes.ts
 *
 * Lane-based command queue with concurrency control, draining, generation
 * tracking, and active-task waiters. 1:1 aligned with TS implementation.
 */

// --- lanes.ts ---

object CommandLane {
    const val Main = "main"
    const val Cron = "cron"
    const val Subagent = "subagent"
    const val Nested = "nested"
}

// --- Error types ---

class CommandLaneClearedError(lane: String? = null) : Exception(
    if (lane != null) "Command lane \"$lane\" cleared" else "Command lane cleared"
) {
    init {
        // Align: this.name = "CommandLaneClearedError"
    }
}

class GatewayDrainingError : Exception(
    "Gateway is draining for restart; new tasks are not accepted"
)

// --- Queue entry & lane state ---

private class QueueEntry(
    val task: suspend () -> Any?,
    val deferred: CompletableDeferred<Any?>,
    val enqueuedAt: Long,
    val warnAfterMs: Long,
    val onWait: ((waitMs: Long, queuedAhead: Int) -> Unit)?
)

private class LaneState(
    val lane: String,
    val queue: MutableList<QueueEntry> = mutableListOf(),
    val activeTaskIds: MutableSet<Int> = mutableSetOf(),
    var maxConcurrent: Int = 1,
    var draining: Boolean = false,
    var generation: Int = 0
)

private class ActiveTaskWaiter(
    val activeTaskIds: Set<Int>,
    val deferred: CompletableDeferred<DrainResult>,
    var timeoutJob: Job? = null
)

data class DrainResult(val drained: Boolean)

data class EnqueueOptions(
    val warnAfterMs: Long = 2_000L,
    val onWait: ((waitMs: Long, queuedAhead: Int) -> Unit)? = null
)

// --- Global queue state (process-scoped singleton) ---

private class CommandQueueState {
    @Volatile
    var gatewayDraining: Boolean = false
    val lanes = ConcurrentHashMap<String, LaneState>()
    val activeTaskWaiters = ConcurrentHashMap.newKeySet<ActiveTaskWaiter>()
    val nextTaskId = AtomicInteger(1)
}

private fun getQueueState(): CommandQueueState {
    @Suppress("UNUSED_VARIABLE")
    val map = resolveProcessScopedMap<CommandQueueState>("openclaw.commandQueueState")
    return map.getOrPut("instance") { CommandQueueState() }
}

// --- Internal helpers ---

private fun normalizeLane(lane: String): String {
    val trimmed = lane.trim()
    return trimmed.ifEmpty { CommandLane.Main }
}

private fun getLaneDepth(state: LaneState): Int {
    return state.queue.size + state.activeTaskIds.size
}

private fun getLaneState(lane: String): LaneState {
    val queueState = getQueueState()
    return queueState.lanes.getOrPut(lane) {
        LaneState(lane = lane)
    }
}

private fun completeTask(state: LaneState, taskId: Int, taskGeneration: Int): Boolean {
    if (taskGeneration != state.generation) {
        return false
    }
    state.activeTaskIds.remove(taskId)
    return true
}

private fun hasPendingActiveTasks(taskIds: Set<Int>): Boolean {
    val queueState = getQueueState()
    for (state in queueState.lanes.values) {
        for (taskId in state.activeTaskIds) {
            if (taskId in taskIds) return true
        }
    }
    return false
}

private fun resolveActiveTaskWaiter(waiter: ActiveTaskWaiter, result: DrainResult) {
    val queueState = getQueueState()
    if (!queueState.activeTaskWaiters.remove(waiter)) return
    waiter.timeoutJob?.cancel()
    waiter.deferred.complete(result)
}

private fun notifyActiveTaskWaiters() {
    val queueState = getQueueState()
    for (waiter in queueState.activeTaskWaiters.toList()) {
        if (waiter.activeTaskIds.isEmpty() || !hasPendingActiveTasks(waiter.activeTaskIds)) {
            resolveActiveTaskWaiter(waiter, DrainResult(drained = true))
        }
    }
}

private fun isExpectedNonErrorLaneFailure(err: Throwable): Boolean {
    return err::class.simpleName == "LiveSessionModelSwitchError"
}

/**
 * Drain a lane: pump queued entries into active slots up to maxConcurrent.
 * Each active task runs in a coroutine. On completion it pumps the next
 * entry, mirroring the TS recursive pump() pattern.
 */
private fun drainLane(lane: String, scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
    val state = getLaneState(lane)
    if (state.draining) return
    state.draining = true

    try {
        while (state.activeTaskIds.size < state.maxConcurrent && state.queue.isNotEmpty()) {
            val entry = state.queue.removeAt(0)
            val waitedMs = System.currentTimeMillis() - entry.enqueuedAt
            if (waitedMs >= entry.warnAfterMs) {
                try {
                    entry.onWait?.invoke(waitedMs, state.queue.size)
                } catch (_: Exception) {
                    // lane onWait callback failed
                }
            }
            val taskId = getQueueState().nextTaskId.getAndIncrement()
            val taskGeneration = state.generation
            state.activeTaskIds.add(taskId)

            scope.launch {
                try {
                    val result = entry.task()
                    val completedCurrentGeneration = completeTask(state, taskId, taskGeneration)
                    if (completedCurrentGeneration) {
                        notifyActiveTaskWaiters()
                        drainLane(lane, scope)
                    }
                    entry.deferred.complete(result)
                } catch (err: Throwable) {
                    completeTask(state, taskId, taskGeneration).also { completedCurrentGeneration ->
                        if (completedCurrentGeneration) {
                            notifyActiveTaskWaiters()
                            drainLane(lane, scope)
                        }
                    }
                    entry.deferred.completeExceptionally(err)
                }
            }
        }
    } finally {
        state.draining = false
    }
}

// --- Public API (aligned with TS exports) ---

/**
 * Mark gateway as draining for restart so new enqueues fail fast with
 * [GatewayDrainingError] instead of being silently killed on shutdown.
 */
fun markGatewayDraining() {
    getQueueState().gatewayDraining = true
}

fun setCommandLaneConcurrency(lane: String, maxConcurrent: Int) {
    val cleaned = normalizeLane(lane)
    val state = getLaneState(cleaned)
    state.maxConcurrent = max(1, maxConcurrent)
    drainLane(cleaned)
}

@Suppress("UNCHECKED_CAST")
suspend fun <T> enqueueCommandInLane(
    lane: String,
    task: suspend () -> T,
    opts: EnqueueOptions = EnqueueOptions()
): T {
    val queueState = getQueueState()
    if (queueState.gatewayDraining) {
        throw GatewayDrainingError()
    }
    val cleaned = normalizeLane(lane)
    val state = getLaneState(cleaned)
    val deferred = CompletableDeferred<Any?>()

    state.queue.add(
        QueueEntry(
            task = { task() },
            deferred = deferred,
            enqueuedAt = System.currentTimeMillis(),
            warnAfterMs = opts.warnAfterMs,
            onWait = opts.onWait
        )
    )
    drainLane(cleaned)
    return deferred.await() as T
}

suspend fun <T> enqueueCommand(
    task: suspend () -> T,
    opts: EnqueueOptions = EnqueueOptions()
): T {
    return enqueueCommandInLane(CommandLane.Main, task, opts)
}

fun getQueueSize(lane: String = CommandLane.Main): Int {
    val resolved = normalizeLane(lane)
    val state = getQueueState().lanes[resolved] ?: return 0
    return getLaneDepth(state)
}

fun getTotalQueueSize(): Int {
    var total = 0
    for (s in getQueueState().lanes.values) {
        total += getLaneDepth(s)
    }
    return total
}

fun clearCommandLane(lane: String = CommandLane.Main): Int {
    val cleaned = normalizeLane(lane)
    val state = getQueueState().lanes[cleaned] ?: return 0
    val removed = state.queue.size
    val pending = state.queue.toList()
    state.queue.clear()
    for (entry in pending) {
        entry.deferred.completeExceptionally(CommandLaneClearedError(cleaned))
    }
    return removed
}

/**
 * Reset all lane runtime state to idle. Used after in-process restarts
 * where interrupted tasks' finally blocks may not run, leaving stale
 * active task IDs that permanently block new work from draining.
 *
 * Bumps lane generation and clears execution counters so stale completions
 * from old in-flight tasks are ignored. Queued entries are intentionally
 * preserved — they represent pending user work that should still execute
 * after restart.
 */
fun resetAllLanes() {
    val queueState = getQueueState()
    queueState.gatewayDraining = false
    val lanesToDrain = mutableListOf<String>()
    for (state in queueState.lanes.values) {
        state.generation += 1
        state.activeTaskIds.clear()
        state.draining = false
        if (state.queue.isNotEmpty()) {
            lanesToDrain.add(state.lane)
        }
    }
    // Drain after the full reset pass so all lanes are in a clean state first.
    for (lane in lanesToDrain) {
        drainLane(lane)
    }
    notifyActiveTaskWaiters()
}

/** Returns the total number of actively executing tasks across all lanes. */
fun getActiveTaskCount(): Int {
    var total = 0
    for (s in getQueueState().lanes.values) {
        total += s.activeTaskIds.size
    }
    return total
}

/**
 * Wait for all currently active tasks across all lanes to finish.
 * Resolves when no tasks are active or when [timeoutMs] elapses.
 *
 * New tasks enqueued after this call are ignored — only tasks that are
 * already executing are waited on.
 */
suspend fun waitForActiveTasks(timeoutMs: Long): DrainResult {
    val queueState = getQueueState()
    val activeAtStart = mutableSetOf<Int>()
    for (state in queueState.lanes.values) {
        activeAtStart.addAll(state.activeTaskIds)
    }

    if (activeAtStart.isEmpty()) {
        return DrainResult(drained = true)
    }
    if (timeoutMs <= 0) {
        return DrainResult(drained = false)
    }

    val deferred = CompletableDeferred<DrainResult>()
    val waiter = ActiveTaskWaiter(
        activeTaskIds = activeAtStart,
        deferred = deferred
    )
    waiter.timeoutJob = CoroutineScope(Dispatchers.Default).launch {
        delay(timeoutMs)
        resolveActiveTaskWaiter(waiter, DrainResult(drained = false))
    }
    queueState.activeTaskWaiters.add(waiter)
    // Check immediately in case all tasks already finished
    notifyActiveTaskWaiters()
    return deferred.await()
}

/**
 * Test-only hard reset that discards all queue state, including preserved
 * queued work from previous generations.
 */
fun resetCommandQueueStateForTest() {
    val queueState = getQueueState()
    queueState.gatewayDraining = false
    queueState.lanes.clear()
    for (waiter in queueState.activeTaskWaiters.toList()) {
        resolveActiveTaskWaiter(waiter, DrainResult(drained = true))
    }
    queueState.nextTaskId.set(1)
}
