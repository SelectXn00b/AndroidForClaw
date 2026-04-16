/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-orphan-recovery.ts
 *
 * Hermes adaptation: orphan recovery for subagent sessions.
 * After a process restart or crash, scans for active subagent runs that have no
 * corresponding Job and sends synthetic resume messages to restart them.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orphan recovery for subagent sessions.
 * Aligned with OpenClaw subagent-orphan-recovery.ts.
 *
 * On app restart, active subagent run records may exist on disk but their
 * coroutine Jobs are gone. This module detects and recovers those orphans.
 */
object SubagentOrphanRecovery {

    private const val TAG = "SubagentOrphanRecovery"

    /** Initial delay before first recovery attempt (ms). Aligned with OpenClaw 5s default. */
    private const val INITIAL_DELAY_MS = 5_000L
    /** Maximum number of retry attempts. Aligned with OpenClaw maxRetries=3. */
    private const val MAX_RETRIES = 3
    /** Backoff multiplier. Aligned with OpenClaw exponential ×2. */
    private const val BACKOFF_MULTIPLIER = 2
    /** Maximum task text included in resume message. Aligned with OpenClaw 2000 char truncation. */
    private const val TASK_TRUNCATE_CHARS = 2000

    /**
     * Build a resume message for an orphaned subagent.
     * Aligned with OpenClaw buildResumeMessage (includes lastHumanMessage + configChangeHint).
     *
     * On Android, "gateway reload" is replaced with "process restart" as the equivalent event.
     */
    fun buildResumeMessage(
        task: String,
        lastHumanMessage: String? = null,
        configChangeHint: String? = null,
    ): String {
        val truncatedTask = if (task.length > TASK_TRUNCATE_CHARS) {
            "${task.take(TASK_TRUNCATE_CHARS)}..."
        } else task

        return buildString {
            append("[System] Your previous turn was interrupted by a process restart. ")
            appendLine("Your original task was:")
            appendLine()
            appendLine(truncatedTask)
            appendLine()
            if (!lastHumanMessage.isNullOrBlank()) {
                appendLine("The last message from the user before the interruption was:")
                appendLine()
                appendLine(lastHumanMessage)
                appendLine()
            }
            append("Please continue where you left off.")
            if (!configChangeHint.isNullOrBlank()) {
                appendLine()
                append(configChangeHint)
            }
        }.trimEnd()
    }

    /**
     * Scan for orphaned subagent runs and attempt to recover them.
     * An orphan is a run that is marked active in the registry but has no Job.
     *
     * @return Triple of (recovered, failed, skipped) counts
     */
    fun recoverOrphanedSubagentSessions(
        registry: SubagentRegistry,
        spawner: SubagentSpawner,
    ): Triple<Int, Int, Int> {
        val allRuns = registry.getRunsSnapshot()
        val orphans = allRuns.values.filter { record ->
            record.isActive && registry.getJob(record.runId) == null
        }

        if (orphans.isEmpty()) {
            Log.i(TAG, "No orphaned subagent runs found")
            return Triple(0, 0, 0)
        }

        Log.i(TAG, "Found ${orphans.size} orphaned subagent run(s)")

        var recovered = 0
        var failed = 0
        var skipped = 0

        for (record in orphans) {
            try {
                // Mark orphan as error — it cannot be resumed in-process
                // (unlike OpenClaw which can re-dispatch to gateway)
                val outcome = SubagentRunOutcome(
                    SubagentRunStatus.ERROR,
                    "Orphaned after process restart"
                )
                registry.markCompleted(
                    record.runId,
                    outcome,
                    SubagentLifecycleEndedReason.SUBAGENT_ERROR,
                    frozenResult = null,
                )
                Log.i(TAG, "Marked orphaned run as error: ${record.runId} (${record.label})")
                recovered++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recover orphan ${record.runId}: ${e.message}")
                failed++
            }
        }

        Log.i(TAG, "Orphan recovery complete: recovered=$recovered failed=$failed skipped=$skipped")
        return Triple(recovered, failed, skipped)
    }

    /**
     * Schedule orphan recovery with exponential backoff retries.
     * Aligned with OpenClaw scheduleOrphanRecovery.
     *
     * @param scope CoroutineScope for launching recovery
     * @param registry SubagentRegistry to scan
     * @param spawner SubagentSpawner for potential re-spawning
     * @param delayMs Initial delay before first attempt
     * @param maxRetries Maximum retry attempts
     */
    fun scheduleOrphanRecovery(
        scope: CoroutineScope,
        registry: SubagentRegistry,
        spawner: SubagentSpawner,
        delayMs: Long = INITIAL_DELAY_MS,
        maxRetries: Int = MAX_RETRIES,
    ) {
        scope.launch {
            var currentDelay = delayMs
            val resumedRunIds = mutableSetOf<String>()

            for (attempt in 0..maxRetries) {
                delay(currentDelay)

                Log.i(TAG, "Orphan recovery attempt ${attempt + 1}/${maxRetries + 1}")

                val (recovered, failed, _) = recoverOrphanedSubagentSessions(registry, spawner)

                if (failed == 0) {
                    Log.i(TAG, "Orphan recovery completed successfully (attempt ${attempt + 1})")
                    return@launch
                }

                if (attempt < maxRetries) {
                    currentDelay *= BACKOFF_MULTIPLIER
                    Log.w(TAG, "Orphan recovery had $failed failure(s), retrying in ${currentDelay}ms")
                } else {
                    Log.e(TAG, "Orphan recovery exhausted retries with $failed remaining failure(s)")
                }
            }
        }
    }
}
