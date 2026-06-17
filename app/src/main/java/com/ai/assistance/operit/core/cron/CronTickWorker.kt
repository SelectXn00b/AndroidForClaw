package com.ai.assistance.operit.core.cron

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ai.assistance.operit.util.AppLogger
import com.xiaomo.hermes.hermes.cron.advanceNextRun
import com.xiaomo.hermes.hermes.cron.getDueJobs
import java.util.concurrent.TimeUnit

/**
 * R-AGENT-031: WorkManager-driven cron tick.
 *
 * Fires every 15 minutes (the WorkManager periodic-work minimum) and
 * dispatches due jobs returned by `Jobs.getDueJobs()` to [CronAgentRunner].
 *
 * Architecture (1+3+4 per user decision):
 * - hermes-android module owns only the data layer (`Jobs.kt` CRUD).
 * - This worker (in app module) is the platform-specific scheduler that
 *   replaces Python upstream's `Scheduler.runJob()` daemon loop.
 * - Each due job is advanced (`advanceNextRun`) before invocation so a
 *   single run never repeats inside the same tick window.
 */
class CronTickWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // R-AGENT-044: record tick start so the cron health probe can answer
        // "when did the worker last fire". Written before the work itself so
        // it reflects "the worker actually got scheduled" rather than success.
        lastTickAt = System.currentTimeMillis()
        return try {
            val due = getDueJobs()
            if (due.isEmpty()) {
                AppLogger.d(TAG, "tick: no due jobs")
                return Result.success()
            }
            AppLogger.d(TAG, "tick: ${due.size} due job(s)")
            for (job in due) {
                val jobId = (job["id"] as? String) ?: continue
                try {
                    advanceNextRun(jobId)
                    CronAgentRunner.run(applicationContext, job)
                } catch (e: Exception) {
                    // Isolate per-job failures so one bad job doesn't poison the tick.
                    AppLogger.e(TAG, "cron job '$jobId' failed: ${e.message}", e)
                }
            }
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "CronTickWorker fatal failure", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CronTickWorker"
        const val UNIQUE_NAME = "hermes_cron_tick"
        const val INTERVAL_MINUTES: Long = 15L

        /**
         * R-AGENT-044: most recent exception message from `enqueue` catch
         * block (or null if the last enqueue succeeded / hasn't run yet).
         * Read by the cron health probe so the agent can answer "why isn't
         * cron working" without grepping logcat.
         */
        @Volatile
        var lastEnqueueError: String? = null

        /**
         * R-AGENT-044: epoch-millis of the most recent `doWork` start, or
         * `0L` if the worker has never ticked. Read by the cron health probe.
         */
        @Volatile
        var lastTickAt: Long = 0L

        /**
         * Idempotent enqueue. Safe to call from `OperitApplication.onCreate()`
         * on every cold start.
         *
         * R-AGENT-031 bugfix (2026-06-18):
         *  - switched from KEEP to UPDATE so a broken/cancelled unique work
         *    record left over from a previous install or crash gets replaced
         *    rather than preserved indefinitely. KEEP could leave the worker
         *    permanently un-registered if any prior enqueue ever raised.
         *  - re-throw on failure so `OperitApplication.onCreate` (or any
         *    other caller) can surface the problem instead of letting cron
         *    silently die. The caller is expected to wrap the call in a
         *    log-only catch so onCreate doesn't crash the app, while still
         *    leaving an actionable error in the log.
         */
        fun enqueue(context: Context) {
            val request: PeriodicWorkRequest =
                PeriodicWorkRequestBuilder<CronTickWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .build()
            try {
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
                AppLogger.d(TAG, "enqueued PeriodicWork '$UNIQUE_NAME' at ${INTERVAL_MINUTES}m")
                lastEnqueueError = null
            } catch (e: Exception) {
                lastEnqueueError = e.message ?: e.javaClass.simpleName
                AppLogger.e(TAG, "failed to enqueue CronTickWorker", e)
                throw e
            }
        }
    }
}
