/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/service.ts
 * - ../openclaw/src/cron/service/timer.ts
 * - ../openclaw/src/cron/service/ops.ts
 * - ../openclaw/src/cron/service/jobs.ts
 *
 * Hermes adaptation: cron scheduling.
 */
package com.xiaomo.hermes.cron

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CronService(private val context: Context, private val config: CronConfig) {
    companion object {
        private const val TAG = "CronService"
        private const val MIN_TIMER_MS = 1000L

        /**
         * Maximum delay between timer ticks. Aligned with OpenClaw timer.ts
         * MAX_TIMER_DELAY_MS. Ensures the scheduler wakes at least once per
         * minute to avoid schedule drift and recover from wall-clock jumps.
         */
        const val MAX_TIMER_DELAY_MS = 60_000L

        /**
         * Minimum gap between consecutive fires. Safety net that prevents
         * spin-loops when computeNextRunAtMs returns a value within the same
         * second as the just-completed run. Aligned with OpenClaw timer.ts
         * MIN_REFIRE_GAP_MS. (See OpenClaw #17821)
         */
        const val MIN_REFIRE_GAP_MS = 2_000L

        /** Maximum one-shot jobs to execute immediately on startup. */
        const val DEFAULT_MAX_MISSED_JOBS_PER_RESTART = 5

        /** Stagger delay for deferred missed jobs on startup. */
        const val DEFAULT_MISSED_JOB_STAGGER_MS = 5_000L

        /** Maximum consecutive schedule errors before auto-disabling. */
        const val MAX_SCHEDULE_ERRORS = 3

        /** Stuck running marker threshold: 2 hours. */
        const val STUCK_RUN_MS = 2 * 60 * 60 * 1000L

        // Default backoff schedule (aligned with OpenClaw DEFAULT_BACKOFF_SCHEDULE_MS)
        val DEFAULT_BACKOFF_SCHEDULE_MS = listOf(
            30_000L,        // 1st error → 30 s
            60_000L,        // 2nd error → 1 min
            300_000L,       // 3rd error → 5 min
            900_000L,       // 4th error → 15 min
            3_600_000L      // 5th+ error → 60 min
        )

        /** Default max retries for one-shot jobs on transient errors. */
        const val DEFAULT_MAX_TRANSIENT_RETRIES = 3
    }

    private val store = CronStore(config.storePath)
    private val runLog = CronRunLog(
        "${config.storePath.substringBeforeLast("/")}/runs",
        config.runLog
    )

    private val lock = ReentrantLock()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var jobs = mutableListOf<CronJob>()
    private var isStarted = false
    private var timerRunnable: Runnable? = null

    /**
     * Running flag — prevents concurrent tick execution.
     * Aligned with OpenClaw timer.ts state.running.
     */
    @Volatile
    private var running = false

    private var concurrentRuns = 0

    var onEvent: ((CronEvent) -> Unit)? = null

    // ── Lifecycle ────────────────────────────────────────────────

    fun start() {
        lock.withLock {
            if (isStarted) return
            Log.d(TAG, "Starting CronService...")

            val storeFile = store.load()
            jobs = storeFile.jobs.toMutableList()

            // Clear stale running markers on startup
            jobs.forEach { it.state.runningAtMs = null }

            // Recompute all next-run times
            recomputeAllNextRuns()
            persist()

            isStarted = true
            Log.d(TAG, "CronService started with ${jobs.size} jobs")
        }

        // Startup catch-up: run missed jobs (outside lock, aligned with OpenClaw runMissedJobs)
        runMissedJobs()

        // Arm the timer after catch-up
        armTimer()
    }

    fun stop() {
        lock.withLock {
            if (!isStarted) return
            timerRunnable?.let { handler.removeCallbacks(it) }
            timerRunnable = null
            scope.coroutineContext.cancelChildren()
            isStarted = false
            running = false
            Log.d(TAG, "CronService stopped")
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────

    fun add(job: CronJob): CronJob {
        return lock.withLock {
            val newJob = job.copy(
                id = UUID.randomUUID().toString(),
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
            newJob.state.nextRunAtMs = computeJobNextRun(newJob)

            jobs.add(newJob)
            persist()
            armTimer()

            emitEvent(CronEvent(jobId = newJob.id, action = "added"))
            newJob
        }
    }

    fun update(jobId: String, patch: (CronJob) -> CronJob): CronJob? {
        return lock.withLock {
            val idx = jobs.indexOfFirst { it.id == jobId }
            if (idx == -1) return null

            val updated = patch(jobs[idx]).copy(updatedAtMs = System.currentTimeMillis())
            updated.state.nextRunAtMs = computeJobNextRun(updated)

            jobs[idx] = updated
            persist()
            armTimer()

            emitEvent(CronEvent(jobId = updated.id, action = "updated"))
            updated
        }
    }

    fun remove(jobId: String): Boolean {
        return lock.withLock {
            val removed = jobs.removeIf { it.id == jobId }
            if (removed) {
                persist()
                runLog.delete(jobId)
                armTimer()
                emitEvent(CronEvent(jobId = jobId, action = "removed"))
            }
            removed
        }
    }

    fun list(includeDisabled: Boolean = true, enabled: Boolean? = null): List<CronJob> {
        return lock.withLock {
            jobs.filter {
                when {
                    enabled == true -> it.enabled
                    enabled == false -> !it.enabled
                    !includeDisabled -> it.enabled
                    else -> true
                }
            }
        }
    }

    fun get(jobId: String): CronJob? = lock.withLock { jobs.find { it.id == jobId } }

    fun queryRuns(jobId: String, limit: Int = 100, status: RunStatus? = null): List<CronRunLogEntry> {
        return runLog.query(jobId, limit, status)
    }

    fun run(jobId: String, force: Boolean = false): Boolean {
        val job = get(jobId) ?: return false
        if (!force && job.state.nextRunAtMs?.let { it > System.currentTimeMillis() } == true) {
            return false
        }
        scope.launch { executeJob(job) }
        return true
    }

    fun status(): Map<String, Any?> {
        return lock.withLock {
            mapOf(
                "enabled" to config.enabled,
                "jobs" to jobs.size,
                "nextWakeAtMs" to (jobs.mapNotNull { it.state.nextRunAtMs }.minOrNull() as Any?),
                "isStarted" to isStarted,
                "concurrentRuns" to concurrentRuns
            )
        }
    }

    // ── Timer ────────────────────────────────────────────────────

    /**
     * Arm the timer for the next due job.
     * Aligned with OpenClaw timer.ts armTimer.
     */
    private fun armTimer() {
        if (!isStarted) return
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null

        val nowMs = System.currentTimeMillis()
        val nextRunAtMs = jobs.filter { it.enabled }
            .mapNotNull { it.state.nextRunAtMs }
            .filter { it > nowMs }
            .minOrNull() ?: return

        val delay = (nextRunAtMs - nowMs).coerceAtLeast(0)

        // Floor: when delay=0, use MIN_REFIRE_GAP_MS to prevent tight loop
        // (aligned with OpenClaw timer.ts flooredDelay)
        val flooredDelay = if (delay == 0L) MIN_REFIRE_GAP_MS else delay

        // Clamp to MAX_TIMER_DELAY_MS to avoid schedule drift
        // (aligned with OpenClaw timer.ts clampedDelay)
        val clampedDelay = flooredDelay.coerceAtMost(MAX_TIMER_DELAY_MS)

        timerRunnable = Runnable { onTimerTick() }
        handler.postDelayed(timerRunnable!!, clampedDelay)
    }

    /**
     * Re-arm timer at MAX_TIMER_DELAY_MS while a tick is running.
     * Aligned with OpenClaw timer.ts armRunningRecheckTimer.
     * Without this, a long-running job would leave no timer set after the
     * early return, silently killing the scheduler. (See OpenClaw #12025)
     */
    private fun armRunningRecheckTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = Runnable { onTimerTick() }
        handler.postDelayed(timerRunnable!!, MAX_TIMER_DELAY_MS)
    }

    /**
     * Timer tick handler.
     * Aligned with OpenClaw timer.ts onTimer.
     */
    private fun onTimerTick() {
        if (running) {
            // Re-arm to keep ticking while a job is executing
            armRunningRecheckTimer()
            return
        }
        running = true
        // Keep a watchdog timer armed while tick executes
        armRunningRecheckTimer()

        scope.launch {
            try {
                val nowMs = System.currentTimeMillis()
                val dueJobs = lock.withLock {
                    jobs.filter { job ->
                        isRunnableJob(job, nowMs)
                    }.also { due ->
                        // Mark running
                        due.forEach { job ->
                            job.state.runningAtMs = nowMs
                            job.state.lastError = null
                        }
                        if (due.isNotEmpty()) persist()
                    }
                }

                // Execute due jobs (respecting maxConcurrentRuns)
                for (job in dueJobs) {
                    executeJob(job)
                }

                // Maintenance recompute: only repair missing nextRunAtMs,
                // don't advance past-due values without execution
                // (aligned with OpenClaw recomputeNextRunsForMaintenance)
                lock.withLock {
                    var changed = false
                    for (job in jobs) {
                        changed = normalizeJobTickState(job, nowMs) || changed
                        if (job.enabled && job.state.nextRunAtMs == null) {
                            job.state.nextRunAtMs = computeJobNextRun(job)
                            changed = true
                        }
                    }
                    if (changed) persist()
                }
            } finally {
                // Session reaper: sweep expired sessions (aligned with OpenClaw timer.ts)
                sweepSessions()

                running = false
                armTimer()
            }
        }
    }

    // ── Job Execution ────────────────────────────────────────────

    /**
     * Check if a job is runnable right now.
     * Aligned with OpenClaw timer.ts isRunnableJob.
     */
    private fun isRunnableJob(job: CronJob, nowMs: Long): Boolean {
        if (!job.enabled) return false
        if (job.state.runningAtMs != null) return false

        val next = job.state.nextRunAtMs
        if (next != null && next <= nowMs) return true

        // Check for missed cron slots (aligned with OpenClaw allowCronMissedRunByLastRun)
        if (job.schedule is CronSchedule.Cron) {
            val previousRunAtMs = CronScheduleParser.computeStaggeredCronPreviousRun(
                job.schedule, job.id, nowMs
            )
            if (previousRunAtMs != null) {
                val lastRunAtMs = job.state.lastRunAtMs
                if (lastRunAtMs != null && previousRunAtMs > lastRunAtMs) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Normalize job tick state: handle disabled jobs and stuck markers.
     * Aligned with OpenClaw jobs.ts normalizeJobTickState.
     * @return true if state was changed
     */
    private fun normalizeJobTickState(job: CronJob, nowMs: Long): Boolean {
        var changed = false

        if (!job.enabled) {
            if (job.state.nextRunAtMs != null) { job.state.nextRunAtMs = null; changed = true }
            if (job.state.runningAtMs != null) { job.state.runningAtMs = null; changed = true }
            return changed
        }

        // Clear stuck running markers
        val runningAt = job.state.runningAtMs
        if (runningAt != null && nowMs - runningAt > STUCK_RUN_MS) {
            Log.w(TAG, "Clearing stuck running marker for job ${job.id}")
            job.state.runningAtMs = null
            changed = true
        }

        return changed
    }

    /**
     * Execute a job with full lifecycle handling.
     * Aligned with OpenClaw timer.ts executeJobCoreWithTimeout + applyOutcomeToStoredJob.
     */
    private suspend fun executeJob(job: CronJob) {
        if (concurrentRuns >= config.maxConcurrentRuns) return

        lock.withLock {
            if (job.state.runningAtMs != null) return
            job.state.runningAtMs = System.currentTimeMillis()
            concurrentRuns++
        }

        val startMs = System.currentTimeMillis()
        emitEvent(CronEvent(job.id, "started", runAtMs = startMs))

        var result: CronRunResult

        try {
            // Execute with timeout support (aligned with OpenClaw executeJobCoreWithTimeout)
            val timeoutMs = resolveJobTimeoutMs(job)
            result = if (timeoutMs != null && timeoutMs > 0) {
                withTimeoutOrNull(timeoutMs) {
                    when (job.sessionTarget) {
                        SessionTarget.MAIN -> executeMainJob(job)
                        SessionTarget.ISOLATED -> executeIsolatedJob(job)
                    }
                } ?: CronRunResult(RunStatus.ERROR, "cron: job execution timed out")
            } else {
                when (job.sessionTarget) {
                    SessionTarget.MAIN -> executeMainJob(job)
                    SessionTarget.ISOLATED -> executeIsolatedJob(job)
                }
            }
        } catch (e: Exception) {
            result = CronRunResult(RunStatus.ERROR, e.message ?: "Unknown error")
        }

        val endedMs = System.currentTimeMillis()

        // Apply result with full lifecycle handling
        val shouldDelete = lock.withLock {
            val delete = applyJobResult(job, result, startMs, endedMs)
            concurrentRuns--
            persist()
            delete
        }

        // Log the run
        runLog.append(CronRunLogEntry(
            ts = endedMs,
            jobId = job.id,
            status = result.status,
            summary = result.summary,
            runAtMs = startMs,
            durationMs = endedMs - startMs,
            nextRunAtMs = job.state.nextRunAtMs
        ))

        emitEvent(CronEvent(
            job.id, "finished",
            runAtMs = startMs,
            durationMs = endedMs - startMs,
            status = result.status,
            summary = result.summary
        ))

        // Delete one-shot job if needed (aligned with OpenClaw shouldDelete)
        if (shouldDelete) {
            lock.withLock {
                jobs.removeIf { it.id == job.id }
                persist()
                emitEvent(CronEvent(jobId = job.id, action = "removed"))
            }
        }

        armTimer()
    }

    /**
     * Apply the result of a job execution to the job's state.
     * Aligned with OpenClaw timer.ts applyJobResult.
     *
     * Handles:
     * - Consecutive error tracking and exponential backoff
     * - One-shot (at) job lifecycle: disable on success, retry/disable on error
     * - nextRunAtMs computation with MIN_REFIRE_GAP_MS safety net for cron
     *
     * @return true if the job should be deleted (at + deleteAfterRun + OK)
     */
    private fun applyJobResult(
        job: CronJob,
        result: CronRunResult,
        startedAt: Long,
        endedAt: Long
    ): Boolean {
        // Clear running marker
        job.state.runningAtMs = null
        job.state.lastRunAtMs = startedAt
        job.state.lastRunStatus = result.status
        job.state.lastDurationMs = maxOf(0L, endedAt - startedAt)
        job.state.lastDeliveryStatus = result.deliveryStatus
        job.state.lastDeliveryError = result.deliveryError
        job.state.lastDelivered = result.delivered
        job.updatedAtMs = endedAt

        // Consecutive error tracking (aligned with OpenClaw)
        if (result.status == RunStatus.ERROR) {
            job.state.consecutiveErrors = (job.state.consecutiveErrors) + 1
        } else {
            job.state.consecutiveErrors = 0
        }

        val shouldDelete =
            job.schedule is CronSchedule.At && job.deleteAfterRun == true && result.status == RunStatus.OK

        if (!shouldDelete) {
            if (job.schedule is CronSchedule.At) {
                // One-shot job lifecycle (aligned with OpenClaw at-type handling)
                if (result.status == RunStatus.OK || result.status == RunStatus.SKIPPED) {
                    // Done or skipped: disable to prevent tight-loop (#11452)
                    job.enabled = false
                    job.state.nextRunAtMs = null
                } else if (result.status == RunStatus.ERROR) {
                    val transient = isTransientError(result.summary)
                    val consecutive = job.state.consecutiveErrors
                    if (transient && consecutive <= DEFAULT_MAX_TRANSIENT_RETRIES) {
                        // Schedule retry with backoff (#24355)
                        val backoff = CronScheduleParser.errorBackoffMs(consecutive, config.retry.backoffMs)
                        job.state.nextRunAtMs = endedAt + backoff
                        Log.i(TAG, "Scheduling one-shot retry for ${job.id} after ${backoff}ms " +
                            "(consecutive=$consecutive)")
                    } else {
                        // Permanent error or max retries exhausted: disable
                        job.enabled = false
                        job.state.nextRunAtMs = null
                        Log.w(TAG, "Disabling one-shot job ${job.id} after error " +
                            "(transient=$transient, consecutive=$consecutive)")
                    }
                }
            } else if (result.status == RunStatus.ERROR && job.enabled) {
                // Periodic job error: apply exponential backoff
                val backoff = CronScheduleParser.errorBackoffMs(
                    maxOf(1, job.state.consecutiveErrors), config.retry.backoffMs
                )
                val normalNext = computeJobNextRun(job, endedAt)
                val backoffNext = endedAt + backoff
                job.state.nextRunAtMs =
                    if (normalNext != null) maxOf(normalNext, backoffNext) else backoffNext
                Log.i(TAG, "Applying error backoff for ${job.id}: ${backoff}ms " +
                    "(consecutive=${job.state.consecutiveErrors})")
            } else if (job.enabled) {
                // Success or skipped: compute next run
                val naturalNext = computeJobNextRun(job, endedAt)
                if (job.schedule is CronSchedule.Cron) {
                    // Safety net: ensure next fire is at least MIN_REFIRE_GAP_MS
                    // after current run ended. Prevents spin-loops (#17821).
                    val minNext = endedAt + MIN_REFIRE_GAP_MS
                    job.state.nextRunAtMs =
                        if (naturalNext != null) maxOf(naturalNext, minNext) else minNext
                } else {
                    job.state.nextRunAtMs = naturalNext
                }
            } else {
                job.state.nextRunAtMs = null
            }
        }

        return shouldDelete
    }

    // ── Job Execution (core) ─────────────────────────────────────

    private suspend fun executeMainJob(job: CronJob): CronRunResult {
        return when (val payload = job.payload) {
            is CronPayload.SystemEvent -> {
                // System events: log and return OK (aligned with OpenClaw
                // executeMainSessionCronJob which enqueues the system event)
                CronRunResult(RunStatus.OK, "System event: ${payload.text}")
            }
            is CronPayload.AgentTurn -> {
                val delivery = CronDeliveryResolver.resolveDeliveryPlan(job)
                CronAgentTurnExecutor.execute(
                    context = context,
                    sessionId = "cron_${job.name}",
                    userMessage = payload.message,
                    model = payload.model,
                    channel = delivery.channel,
                    to = delivery.to,
                    isolated = false
                )
            }
        }
    }

    private suspend fun executeIsolatedJob(job: CronJob): CronRunResult {
        return when (val payload = job.payload) {
            is CronPayload.AgentTurn -> {
                val delivery = CronDeliveryResolver.resolveDeliveryPlan(job)
                CronAgentTurnExecutor.execute(
                    context = context,
                    sessionId = "cron_isolated_${job.id}",
                    userMessage = payload.message,
                    model = payload.model,
                    channel = delivery.channel,
                    to = delivery.to,
                    isolated = true
                )
            }
            is CronPayload.SystemEvent -> CronRunResult(RunStatus.ERROR, "Invalid payload for isolated session")
        }
    }

    // ── Startup Catch-up ─────────────────────────────────────────

    /**
     * Run missed jobs on startup.
     * Aligned with OpenClaw timer.ts runMissedJobs + planStartupCatchup.
     *
     * Scans all enabled jobs with nextRunAtMs <= now.
     * - Up to maxMissedJobsPerRestart are executed immediately
     * - Remaining are staggered (each delayed by 5 seconds)
     */
    private fun runMissedJobs() {
        val nowMs = System.currentTimeMillis()

        val missed = lock.withLock {
            jobs.filter { job ->
                job.enabled &&
                job.state.runningAtMs == null &&
                job.state.nextRunAtMs?.let { it <= nowMs } == true
            }.sortedBy { it.state.nextRunAtMs ?: 0 }
        }

        if (missed.isEmpty()) return

        val maxImmediate = config.maxConcurrentRuns.coerceAtLeast(DEFAULT_MAX_MISSED_JOBS_PER_RESTART)
        val immediate = missed.take(maxImmediate)
        val deferred = missed.drop(maxImmediate)

        if (deferred.isNotEmpty()) {
            Log.i(TAG, "Staggering ${deferred.size} missed jobs " +
                "(${immediate.size} immediate, ${deferred.size} deferred)")
        }

        if (immediate.isNotEmpty()) {
            Log.i(TAG, "Running ${immediate.size} missed jobs after restart")

            // Mark as running
            lock.withLock {
                immediate.forEach { job ->
                    job.state.runningAtMs = nowMs
                    job.state.lastError = null
                }
                persist()
            }

            // Execute immediately (synchronous-ish via runBlocking scope)
            runBlocking {
                for (job in immediate) {
                    executeJob(job)
                }
            }
        }

        // Schedule deferred jobs with staggered delays
        if (deferred.isNotEmpty()) {
            val baseNow = System.currentTimeMillis()
            lock.withLock {
                var offset = DEFAULT_MISSED_JOB_STAGGER_MS
                for (job in deferred) {
                    if (!job.enabled) continue
                    job.state.nextRunAtMs = baseNow + offset
                    offset += DEFAULT_MISSED_JOB_STAGGER_MS
                }
                persist()
            }
        }
    }

    // ── Session Reaper ───────────────────────────────────────────

    /**
     * Sweep expired cron run sessions.
     * Aligned with OpenClaw timer.ts session reaper block.
     * Self-throttled via CronSessionReaper.
     */
    private fun sweepSessions() {
        try {
            val sessionsDir = java.io.File(
                config.storePath.substringBeforeLast("/"), "sessions"
            )
            val retentionMs = CronSessionReaper.resolveRetentionMs(config.sessionRetention)
                ?: return
            CronSessionReaper.sweep(sessionsDir, retentionMs)
        } catch (e: Exception) {
            Log.w(TAG, "Session reaper sweep failed: ${e.message}")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun computeJobNextRun(job: CronJob, nowMs: Long = System.currentTimeMillis()): Long? {
        if (!job.enabled) return null

        // Use stagger-aware computation for cron schedules
        if (job.schedule is CronSchedule.Cron) {
            return CronScheduleParser.computeStaggeredCronNextRun(job.schedule, job.id, nowMs)
        }

        // For "every" schedules: compute from lastRunAtMs if available
        if (job.schedule is CronSchedule.Every) {
            val lastRun = job.state.lastRunAtMs
            if (lastRun != null) {
                val nextFromLast = lastRun + maxOf(1, job.schedule.everyMs)
                if (nextFromLast > nowMs) return nextFromLast
            }
        }

        // For "at" schedules: stay due until successfully finished
        if (job.schedule is CronSchedule.At) {
            if (job.state.lastRunStatus == RunStatus.OK && job.state.lastRunAtMs != null) {
                // Already ran successfully
                return null
            }
        }

        return CronScheduleParser.computeNextRunAtMs(job.schedule, nowMs)
    }

    private fun recomputeAllNextRuns() {
        val nowMs = System.currentTimeMillis()
        jobs.forEach { job ->
            job.state.nextRunAtMs = computeJobNextRun(job, nowMs)
        }
    }

    /**
     * Resolve job timeout in milliseconds.
     * Aligned with OpenClaw resolveCronJobTimeoutMs.
     */
    private fun resolveJobTimeoutMs(job: CronJob): Long? {
        val payload = job.payload
        if (payload is CronPayload.AgentTurn && payload.timeoutSeconds != null) {
            return payload.timeoutSeconds * 1000L
        }
        return null
    }

    /**
     * Check if an error string indicates a transient error.
     * Aligned with OpenClaw timer.ts isTransientCronError.
     */
    private fun isTransientError(error: String?): Boolean {
        if (error.isNullOrBlank()) return false
        val patterns = listOf(
            Regex("(rate[_ ]limit|too many requests|429|resource has been exhausted|cloudflare|tokens per day)", RegexOption.IGNORE_CASE),
            Regex("\\b529\\b|\\boverloaded(?:_error)?\\b|high demand|temporar(?:ily|y) overloaded|capacity exceeded", RegexOption.IGNORE_CASE),
            Regex("(network|econnreset|econnrefused|fetch failed|socket)", RegexOption.IGNORE_CASE),
            Regex("(timeout|etimedout)", RegexOption.IGNORE_CASE),
            Regex("\\b5\\d{2}\\b")
        )
        return patterns.any { it.containsMatchIn(error) }
    }

    private fun persist() {
        store.save(CronStoreFile(jobs = jobs))
    }

    private fun emitEvent(event: CronEvent) {
        onEvent?.invoke(event)
    }
}
