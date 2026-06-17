package com.ai.assistance.operit.core.cron

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * R-AGENT-044: cron self-diagnostic probe.
 *
 * Reads WorkManager state for the unique cron tick worker and merges it
 * with [CronTickWorker.lastEnqueueError] / [CronTickWorker.lastTickAt] so
 * the agent's `cronjob(action="health")` tool can answer "is my cron
 * subsystem alive".
 *
 * Returns a flat map with the keys consumed by the health branch in
 * `CronjobTools.kt::cronjob`:
 *  - `worker_registered` (Boolean)
 *  - `worker_state` (String — WorkInfo.State.name or "MISSING")
 *  - `last_enqueue_error` (String?)
 *  - `last_tick_at` (String? — ISO-8601)
 *  - `next_scheduled_at` (String? — ISO-8601, WorkManager's estimate)
 */
object CronHealthProbe {
    private const val TAG = "CronHealthProbe"

    suspend fun snapshot(context: Context): Map<String, Any?> = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val infos: List<WorkInfo> = try {
            awaitWorkInfos(workManager, CronTickWorker.UNIQUE_NAME)
        } catch (e: Exception) {
            AppLogger.e(TAG, "WorkManager probe failed", e)
            return@withContext mapOf(
                "worker_registered" to false,
                "worker_state" to "MISSING",
                "last_enqueue_error" to (CronTickWorker.lastEnqueueError ?: e.message),
                "last_tick_at" to formatLastTick(),
                "next_scheduled_at" to null,
            )
        }

        // Pick the most "alive" WorkInfo: any non-cancelled record counts as
        // registered. Prefer ENQUEUED / RUNNING when several exist.
        val alive = infos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        } ?: infos.firstOrNull { it.state != WorkInfo.State.CANCELLED }

        if (alive == null) {
            return@withContext mapOf(
                "worker_registered" to false,
                "worker_state" to if (infos.isEmpty()) "MISSING" else (infos.first().state.name),
                "last_enqueue_error" to CronTickWorker.lastEnqueueError,
                "last_tick_at" to formatLastTick(),
                "next_scheduled_at" to null,
            )
        }

        val nextScheduled: String? = try {
            // WorkManager exposes nextScheduleTimeMillis on PeriodicWork from API 31+.
            val method = alive.javaClass.getMethod("getNextScheduleTimeMillis")
            val millis = method.invoke(alive) as? Long ?: 0L
            if (millis > 0L) Instant.ofEpochMilli(millis).toString() else null
        } catch (_: Throwable) {
            null
        }

        mapOf(
            "worker_registered" to true,
            "worker_state" to alive.state.name,
            "last_enqueue_error" to CronTickWorker.lastEnqueueError,
            "last_tick_at" to formatLastTick(),
            "next_scheduled_at" to nextScheduled,
        )
    }

    private fun formatLastTick(): String? {
        val t = CronTickWorker.lastTickAt
        if (t <= 0L) return null
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(t))
    }

    /**
     * Suspend bridge for `ListenableFuture<List<WorkInfo>>` without pulling
     * in `kotlinx-coroutines-guava`. Mirrors the contract used by Workflow
     * scheduler elsewhere in the app.
     */
    private suspend fun awaitWorkInfos(
        workManager: WorkManager,
        uniqueName: String,
    ): List<WorkInfo> = suspendCancellableCoroutine { cont ->
        val future = workManager.getWorkInfosForUniqueWork(uniqueName)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, Executor { it.run() })
        cont.invokeOnCancellation { future.cancel(false) }
    }
}
