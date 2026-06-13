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
         * Idempotent enqueue. Safe to call from `OperitApplication.onCreate()`
         * on every cold start — KEEP policy preserves the existing schedule
         * if one is already pending.
         */
        fun enqueue(context: Context) {
            try {
                val request: PeriodicWorkRequest =
                    PeriodicWorkRequestBuilder<CronTickWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
                AppLogger.d(TAG, "enqueued PeriodicWork '$UNIQUE_NAME' at ${INTERVAL_MINUTES}m")
            } catch (e: Exception) {
                AppLogger.e(TAG, "failed to enqueue CronTickWorker", e)
            }
        }
    }
}
