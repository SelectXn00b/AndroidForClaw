package com.ai.assistance.operit.core.cron

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.hermes.gateway.CronFileLogger
import com.ai.assistance.operit.util.AppLogger
import com.xiaomo.hermes.hermes.cron.advanceNextRun
import com.xiaomo.hermes.hermes.cron.getJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TC-CRON-EXACT (bugfix to R-AGENT-031): receives the exact-alarm broadcast
 * from `AlarmManager` (stamped by `CronExactAlarmScheduler.schedule`) and
 * dispatches the job through `CronAgentRunner.run` — the same path used by
 * `CronTickWorker.doWork` for the regular 15-min tick, so all R-AGENT-031
 * invariants ([CRON CONTEXT] prompt prefix, persistence-layer write-back,
 * `markJobRun` accounting) are preserved.
 *
 * **Why we don't dispatch the agent directly here**: the bilingual
 * `[CRON CONTEXT]` prompt prefix lives inside `CronAgentRunner` — bypassing
 * Runner would reintroduce the recursive-cronjob risk that R-AGENT-031
 * path 3 explicitly guards against.
 *
 * **Manifest registration** (see `AndroidManifest.xml`):
 *  - `android:exported="false"` — only AlarmManager (system_server) needs
 *    to deliver to it; an exported receiver could be invoked by any app
 *    on-device to fake-trigger a cron job. CLAUDE.md prompt-injection
 *    discipline.
 *  - no `<intent-filter>` needed; the PendingIntent constructed in
 *    `CronExactAlarmScheduler.buildPendingIntent` targets this class
 *    explicitly, so the implicit-intent matching path is bypassed.
 *
 * **goAsync vs scope**: BroadcastReceiver.onReceive must finish quickly
 * (~10s budget). We hand the job off to a long-lived `SupervisorJob`
 * scope (mirrors `_immediateTriggerScope` in CronjobTools.kt) and call
 * `goAsync()` so the system keeps the process around until the agent
 * loop completes. `PendingResult.finish()` is called when the launched
 * coroutine finishes — must not be skipped or the system thinks the
 * receiver leaked.
 */
class CronExactAlarmReceiver : BroadcastReceiver() {

    /**
     * Long-lived scope for headless cron-job dispatch. SupervisorJob isolates
     * one bad job from cancelling the whole scope (mirror CronjobTools.kt's
     * `_immediateTriggerScope`).
     */
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        CronFileLogger.i(
            TAG,
            "alarm fired jobId=${intent.getStringExtra(CronExactAlarmScheduler.EXTRA_JOB_ID).orEmpty()} " +
                "action=${intent.action.orEmpty()}"
        )
        if (intent.action != CronExactAlarmScheduler.ACTION_FIRE) {
            // Defensive: ignore any spurious intent we didn't construct.
            // (Should never happen given exported=false + explicit-intent
            // PendingIntent target, but cheap to assert.)
            AppLogger.w(TAG, "ignoring unexpected intent action=${intent.action}")
            return
        }

        val jobId = intent.getStringExtra(CronExactAlarmScheduler.EXTRA_JOB_ID)
        if (jobId.isNullOrBlank()) {
            AppLogger.w(TAG, "ignoring intent without jobId extra")
            return
        }

        // goAsync() to extend the receiver lifetime across the agent loop.
        // BroadcastReceivers normally have ~10s before the system kills
        // the process; agent dispatch can take 30–60s. PendingResult.finish()
        // releases the wakelock — must always be called.
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        dispatchScope.launch {
            try {
                val job = getJob(jobId)
                if (job == null) {
                    AppLogger.w(
                        TAG,
                        "alarm fired for jobId=$jobId but no record found in jobs.json " +
                            "(removed between schedule and fire?)"
                    )
                    return@launch
                }

                // Mirror CronTickWorker.doWork: bump next_run_at before dispatch
                // so a slow-running agent doesn't cause double-firing if the
                // 15-min tick happens during the run.
                advanceNextRun(jobId)

                AppLogger.d(TAG, "dispatching jobId=$jobId via CronAgentRunner")
                CronFileLogger.i(TAG, "alarm dispatched jobId=$jobId — entering CronAgentRunner.run")
                CronAgentRunner.run(appContext, job)
            } catch (e: Throwable) {
                AppLogger.e(TAG, "dispatch failed for jobId=$jobId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CronExactAlarmReceiver"
    }
}
