/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/plugins/types.ts (PluginHookName, event types, handler map)
 * - ../openclaw/src/plugins/hooks.ts (mergeSubagentSpawningResult, runModifyingHook)
 * - ../openclaw/src/agents/subagent-lifecycle-events.ts (target kinds, ended reasons/outcomes)
 * - ../openclaw/src/agents/subagent-registry-completion.ts (emitSubagentEndedHookOnce)
 *
 * Hermes adaptation: lifecycle hook system for subagent events.
 * Provides extensible hook points aligned with OpenClaw's plugin hook architecture.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.logging.Log

/**
 * Hook points for subagent lifecycle events.
 * Aligned with OpenClaw PluginHookName subagent-related hooks.
 */
enum class SubagentHookPoint {
    /** Fired before a subagent session is created; can allow or deny spawn */
    SUBAGENT_SPAWNING,
    /** Fired after the subagent run is registered and agent call succeeds */
    SUBAGENT_SPAWNED,
    /** Fired when a subagent run finishes (any reason) */
    SUBAGENT_ENDED,
    /** Fired to resolve where completion messages should be delivered */
    SUBAGENT_DELIVERY_TARGET,
}

/**
 * Event data for SUBAGENT_SPAWNING hook.
 * Aligned with OpenClaw PluginHookSubagentSpawningEvent.
 */
data class SubagentSpawningEvent(
    val childSessionKey: String,
    val label: String?,
    val mode: SpawnMode,
    val requesterSessionKey: String,
    val threadRequested: Boolean = false,
)

/**
 * Result of SUBAGENT_SPAWNING hook.
 * Aligned with OpenClaw PluginHookSubagentSpawningResult.
 * Spawning hooks use deny-wins semantics.
 */
sealed class SubagentSpawningResult {
    data class Ok(val threadBindingReady: Boolean = false) : SubagentSpawningResult()
    data class Error(val error: String) : SubagentSpawningResult()
}

/**
 * Event data for SUBAGENT_SPAWNED hook.
 * Aligned with OpenClaw PluginHookSubagentSpawnedEvent.
 */
data class SubagentSpawnedEvent(
    val runId: String,
    val childSessionKey: String,
    val label: String?,
    val mode: SpawnMode,
    val requesterSessionKey: String,
)

/**
 * Event data for SUBAGENT_ENDED hook.
 * Aligned with OpenClaw PluginHookSubagentEndedEvent.
 */
data class SubagentEndedEvent(
    val targetSessionKey: String,
    val targetKind: SubagentLifecycleTargetKind,
    val reason: String,
    val sendFarewell: Boolean = false,
    val runId: String? = null,
    val endedAt: Long? = null,
    val outcome: SubagentLifecycleEndedOutcome? = null,
    val error: String? = null,
)

/**
 * Event data for SUBAGENT_DELIVERY_TARGET hook.
 * Aligned with OpenClaw PluginHookSubagentDeliveryTargetEvent.
 */
data class SubagentDeliveryTargetEvent(
    val childSessionKey: String,
    val requesterSessionKey: String,
    val childRunId: String? = null,
    val spawnMode: SpawnMode? = null,
    val expectsCompletionMessage: Boolean = true,
)

/**
 * Result of SUBAGENT_DELIVERY_TARGET hook. First-origin-wins semantics.
 * Aligned with OpenClaw PluginHookSubagentDeliveryTargetResult.
 */
data class SubagentDeliveryTargetResult(
    val channel: String? = null,
    val accountId: String? = null,
    val to: String? = null,
    val threadId: String? = null,
)

/**
 * Handler interface for subagent lifecycle hooks.
 * Aligned with OpenClaw PluginHookHandlerMap subagent entries.
 */
interface SubagentHookHandler {
    /** Priority — higher values run first. Default 0. */
    val priority: Int get() = 0

    /**
     * Called before a subagent is spawned. Can deny the spawn.
     * Returning null means "no opinion" (allow).
     */
    suspend fun onSpawning(event: SubagentSpawningEvent): SubagentSpawningResult? = null

    /** Called after a subagent is successfully spawned. Fire-and-forget. */
    suspend fun onSpawned(event: SubagentSpawnedEvent) {}

    /** Called when a subagent run ends. Fire-and-forget. */
    suspend fun onEnded(event: SubagentEndedEvent) {}

    /** Called to resolve delivery target for completion messages. */
    suspend fun onDeliveryTarget(event: SubagentDeliveryTargetEvent): SubagentDeliveryTargetResult? = null
}

/**
 * Hook runner for subagent lifecycle events.
 * Aligned with OpenClaw HookRunner + mergeSubagentSpawningResult + mergeSubagentDeliveryTargetResult.
 */
class SubagentHooks {

    companion object {
        private const val TAG = "SubagentHooks"
    }

    private val handlers = mutableListOf<SubagentHookHandler>()

    /** Register a hook handler. */
    fun register(handler: SubagentHookHandler) {
        handlers.add(handler)
        handlers.sortByDescending { it.priority }
    }

    /** Unregister a hook handler. */
    fun unregister(handler: SubagentHookHandler) {
        handlers.remove(handler)
    }

    /**
     * Run SUBAGENT_SPAWNING hooks. Uses deny-wins merge semantics.
     * Aligned with OpenClaw mergeSubagentSpawningResult.
     * Returns Ok if all handlers allow (or have no opinion), Error if any denies.
     */
    suspend fun runSpawning(event: SubagentSpawningEvent): SubagentSpawningResult {
        var accumulated: SubagentSpawningResult = SubagentSpawningResult.Ok()

        for (handler in handlers) {
            // Short-circuit on accumulated error (deny-wins)
            if (accumulated is SubagentSpawningResult.Error) break

            try {
                val result = handler.onSpawning(event)
                if (result != null) {
                    accumulated = mergeSpawningResult(accumulated, result)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hook handler error in onSpawning: ${e.message}")
            }
        }

        return accumulated
    }

    /**
     * Run SUBAGENT_SPAWNED hooks. Fire-and-forget (errors swallowed).
     */
    suspend fun runSpawned(event: SubagentSpawnedEvent) {
        for (handler in handlers) {
            try {
                handler.onSpawned(event)
            } catch (e: Exception) {
                Log.w(TAG, "Hook handler error in onSpawned: ${e.message}")
            }
        }
    }

    /**
     * Run SUBAGENT_ENDED hooks. Fire-and-forget (errors swallowed).
     */
    suspend fun runEnded(event: SubagentEndedEvent) {
        for (handler in handlers) {
            try {
                handler.onEnded(event)
            } catch (e: Exception) {
                Log.w(TAG, "Hook handler error in onEnded: ${e.message}")
            }
        }
    }

    /**
     * Run SUBAGENT_DELIVERY_TARGET hooks. First-origin-wins merge semantics.
     * Aligned with OpenClaw mergeSubagentDeliveryTargetResult.
     */
    suspend fun runDeliveryTarget(event: SubagentDeliveryTargetEvent): SubagentDeliveryTargetResult? {
        var result: SubagentDeliveryTargetResult? = null

        for (handler in handlers) {
            try {
                val handlerResult = handler.onDeliveryTarget(event)
                if (handlerResult != null && result == null) {
                    // First-origin-wins
                    result = handlerResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hook handler error in onDeliveryTarget: ${e.message}")
            }
        }

        return result
    }

    /**
     * Merge two spawning results with deny-wins semantics.
     * Aligned with OpenClaw mergeSubagentSpawningResult.
     */
    private fun mergeSpawningResult(
        acc: SubagentSpawningResult,
        next: SubagentSpawningResult,
    ): SubagentSpawningResult {
        // If accumulated is already an error, it wins (deny-wins)
        if (acc is SubagentSpawningResult.Error) return acc
        // If next is error, it wins
        if (next is SubagentSpawningResult.Error) return next
        // Both Ok — merge threadBindingReady
        val accOk = acc as SubagentSpawningResult.Ok
        val nextOk = next as SubagentSpawningResult.Ok
        return SubagentSpawningResult.Ok(
            threadBindingReady = accOk.threadBindingReady || nextOk.threadBindingReady
        )
    }
}
