package com.ai.assistance.operit.core.cron

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ai.assistance.operit.util.AppLogger

/**
 * TC-CRON-EXACT (bugfix to R-AGENT-031): AlarmManager-backed exact-alarm
 * scheduler for "once" cron jobs whose delta-to-fire is < 15 minutes.
 *
 * **Why it exists**: the project's only OS-level cron trigger today is the
 * 15-minute `PeriodicWorkRequest` tick (`CronTickWorker`). For short-delay
 * once-jobs (typical: "remind me in 5 minutes"), the 15-min poll arrives
 * too late — observed 15-20+ minute delays — so we wire AlarmManager as
 * a side path to fire precisely at the requested wall-clock time.
 *
 * **Why `setExactAndAllowWhileIdle`**:
 *  - `set` / `setRepeating` are inexact and Android may delay them
 *    arbitrarily for batching.
 *  - `setExact` is precise but blocked by doze mode (Android 6+).
 *  - `setExactAndAllowWhileIdle` is the only API that fires precisely
 *    even on idle/doze devices, which is exactly the user's "remind me
 *    while my phone is on the desk" scenario.
 *  - Android 12+ requires `SCHEDULE_EXACT_ALARM` permission (already
 *    declared at `AndroidManifest.xml:59`).
 *
 * **Why `RTC_WAKEUP`**:
 *  - User-perceived schedules are wall-clock ("in 5 minutes" → fire at
 *    wall-clock time T+5min), so RTC (real-time-clock) is correct;
 *    `ELAPSED_REALTIME` would drift if the user changes the system clock.
 *  - `_WAKEUP` ensures we wake the device from sleep — without it the
 *    alarm fires only when the device is next woken for some other
 *    reason, defeating the precision win on idle devices.
 *
 * **Idempotency**: `AlarmManager.setExactAndAllowWhileIdle(...)` replaces
 * any prior alarm whose `PendingIntent` matches by `requestCode + Intent
 * action + Intent data`. We key by `jobId` (mapped to `requestCode` via
 * stable hash + carried in the Intent extras), so re-scheduling the same
 * job — e.g. on schedule update — silently overwrites the prior alarm.
 *
 * **No retry / failure surface**: if `AlarmManager.canScheduleExactAlarms()`
 * returns false (Android 12+ user denied permission), we log and skip.
 * `CronTickWorker` will eventually pick up the job on the next 15-min
 * tick — late, but not lost.
 */
object CronExactAlarmScheduler {

    private const val TAG = "CronExactAlarm"

    /**
     * Intent action for the alarm broadcast. Receiver matches on this.
     * Namespaced to avoid clash with any other intent action in the app.
     */
    const val ACTION_FIRE = "com.ai.assistance.operit.core.cron.action.FIRE_EXACT_ALARM"

    /** Intent extra key carrying the persisted job id. */
    const val EXTRA_JOB_ID = "job_id"

    /**
     * Stamp an exact alarm for `jobId` to fire at `runAtMillis` (epoch wall-clock ms).
     *
     * Safe to call from any thread. Non-blocking.
     */
    fun schedule(context: Context, jobId: String, runAtMillis: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            AppLogger.w(TAG, "AlarmManager unavailable; skipping schedule for jobId=$jobId")
            return
        }

        // Android 12+ (S) gates exact alarms behind SCHEDULE_EXACT_ALARM
        // user-grantable permission. If the user revoked it, fall back silently.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            AppLogger.w(
                TAG,
                "canScheduleExactAlarms()=false (user revoked SCHEDULE_EXACT_ALARM); " +
                    "jobId=$jobId will fall back to next CronTickWorker tick (up to 15min late)."
            )
            return
        }

        val pendingIntent = buildPendingIntent(appContext, jobId)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                runAtMillis,
                pendingIntent
            )
            val deltaMs = runAtMillis - System.currentTimeMillis()
            AppLogger.d(
                TAG,
                "scheduled exact alarm for jobId=$jobId " +
                    "runAt=$runAtMillis (deltaMs=$deltaMs)"
            )
        } catch (e: SecurityException) {
            // Older OEMs sometimes throw SecurityException even when the
            // public canScheduleExactAlarms() check returned true (rom quirk).
            // Log and bail; the periodic tick fallback still applies.
            AppLogger.e(TAG, "SecurityException scheduling exact alarm for jobId=$jobId", e)
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Failed to schedule exact alarm for jobId=$jobId", e)
        }
    }

    /** Cancel a previously-stamped alarm (used when the job is removed). */
    fun cancel(context: Context, jobId: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pendingIntent = buildPendingIntent(appContext, jobId)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Build the `PendingIntent` keyed by jobId. Two requirements:
     *  - same jobId → same PendingIntent (so AlarmManager.set replaces it)
     *  - different jobIds → different PendingIntent (so they don't clobber)
     *
     * We achieve this with `requestCode = jobId.hashCode()` plus the jobId
     * embedded as an extra (so the receiver can re-resolve the job record
     * even if the pending intent is recreated by the system).
     *
     * Flags:
     *  - `FLAG_UPDATE_CURRENT`: replace extras of any existing PendingIntent
     *    with the new ones (handles schedule updates cleanly).
     *  - `FLAG_IMMUTABLE`: required on Android 12+; we never mutate the
     *    intent after creation anyway.
     */
    private fun buildPendingIntent(appContext: Context, jobId: String): PendingIntent {
        val intent = Intent(appContext, CronExactAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_JOB_ID, jobId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            appContext,
            jobId.hashCode(),
            intent,
            flags
        )
    }
}
