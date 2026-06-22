package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-EXACT-* wiring guards.
 *
 * Bugfix (not a new R doc entry — bug = code didn't satisfy the existing
 * R-AGENT-031 requirement that cron jobs fire at their declared time):
 * the only OS-level cron trigger today is the 15-minute PeriodicWork tick
 * (`CronTickWorker`). For "once" jobs whose delta-to-fire is < 15 minutes
 * (e.g. user says "remind me in 5 minutes"), the job sits in jobs.json
 * waiting for the next 15-min tick — observed delay 15-20+ minutes,
 * effectively breaking the timer for short-delay reminders.
 *
 * Fix path:
 *  - hermes-android: new top-level injection variable
 *    `cronShortDelayScheduler: ((jobId: String, runAtMillis: Long) -> Unit)?`
 *    in `cron/Scheduler.kt`, mirroring `cronImmediateRunner` /
 *    `cronOutboundDispatcher`. Module dependency is one-way (app → hermes-
 *    android), so we cannot import AlarmManager into hermes-android — the
 *    Android-specific scheduler lives in app and is injected at startup.
 *  - hermes-android: `CronjobTools._createCronJob` / `_updateCronJob`
 *    invoke the injected scheduler when `schedule.kind == "once"` and
 *    delta-to-fire < 15 minutes; otherwise the existing CronTickWorker
 *    path keeps handling the job (preserves Python 1:1 alignment for the
 *    common case).
 *  - app: `CronExactAlarmScheduler` wraps
 *    `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, runAtMillis,
 *    pendingIntent)` so even doze mode still fires.
 *  - app: `CronExactAlarmReceiver` (BroadcastReceiver) extracts jobId from
 *    intent extras and dispatches via `CronAgentRunner` (preserves
 *    [CRON CONTEXT] prompt prefix from R-AGENT-031).
 *  - app: `OperitApplication.onCreate` injects the scheduler lambda.
 *  - manifest: `<receiver android:name=".core.cron.CronExactAlarmReceiver"
 *    android:exported="false" />` (must be unexported — external apps
 *    must not be able to fake-trigger our cron jobs).
 *
 * All checks are source-scan style — exact-alarm dispatch requires
 * Robolectric + AlarmManager fixture which is overkill here; behaviour
 * verified by §3 E2E + manual ("remind me in 5 minutes" → reminder lands
 * within 5-6 minutes, not 15+).
 */
class CronExactAlarmSchedulerWiringTest {

    // ---------- TC-CRON-EXACT-a: Scheduler.kt injection point ----------

    @Test
    fun `TC-CRON-EXACT-a Scheduler exposes cronShortDelayScheduler injection point`() {
        val source = File(schedulerPath()).readText()
        // Top-level var must exist (mirror cronImmediateRunner/cronOutboundDispatcher).
        assertTrue(
            "TC-CRON-EXACT-a: Scheduler.kt must declare top-level " +
                "`var cronShortDelayScheduler` so the app module can inject the " +
                "AlarmManager-backed implementation at startup.",
            Regex("""\bvar\s+cronShortDelayScheduler\b""").containsMatchIn(source)
        )
        // Type signature must take jobId (String) + runAtMillis (Long) — pin both
        // to catch silent signature drift.
        val declRange = Regex("""var\s+cronShortDelayScheduler[\s\S]{0,300}""")
            .find(source)?.value
            ?: error("cronShortDelayScheduler declaration not found")
        assertTrue(
            "TC-CRON-EXACT-a: cronShortDelayScheduler signature must accept " +
                "`jobId` parameter (String).",
            declRange.contains("jobId")
        )
        assertTrue(
            "TC-CRON-EXACT-a: cronShortDelayScheduler signature must accept " +
                "`runAtMillis: Long` parameter (epoch millis to fire at).",
            declRange.contains("runAtMillis") && declRange.contains("Long")
        )
    }

    // ---------- TC-CRON-EXACT-b: CronjobTools dispatches once+short-delay ----------

    @Test
    fun `TC-CRON-EXACT-b CronjobTools dispatches once-short-delay jobs to scheduler`() {
        val source = File(cronjobToolsPath()).readText()
        // Must reference the injection point at least once (would be 0 if dispatch
        // logic was forgotten or moved out of this file).
        assertTrue(
            "TC-CRON-EXACT-b: CronjobTools.kt must reference `cronShortDelayScheduler` " +
                "(the once+short-delay route into AlarmManager).",
            source.contains("cronShortDelayScheduler")
        )
        // Routing decision lives near a `kind == "once"` check.
        assertTrue(
            "TC-CRON-EXACT-b: CronjobTools.kt must check `kind == \"once\"` to gate " +
                "the AlarmManager path (interval-type jobs continue to ride the " +
                "15-min PeriodicWork tick; only once-type benefits from exact alarms).",
            Regex(""""once"""").containsMatchIn(source) &&
                source.contains("kind")
        )
        // Routing decision must compare against the 15-minute boundary (literal `15`).
        assertTrue(
            "TC-CRON-EXACT-b: CronjobTools.kt must compare delta against `15` " +
                "minutes — only jobs firing < 15 min away need the bypass; the " +
                "15-min PeriodicWork tick handles longer delays just fine.",
            source.contains("15")
        )
    }

    // ---------- TC-CRON-EXACT-c: CronExactAlarmScheduler uses setExactAndAllowWhileIdle ----------

    @Test
    fun `TC-CRON-EXACT-c CronExactAlarmScheduler uses setExactAndAllowWhileIdle with RTC_WAKEUP`() {
        val source = File(exactSchedulerPath()).readText()
        // Class declaration must exist.
        assertTrue(
            "TC-CRON-EXACT-c: CronExactAlarmScheduler.kt must declare a class or " +
                "object named `CronExactAlarmScheduler`.",
            Regex("""(class|object)\s+CronExactAlarmScheduler\b""")
                .containsMatchIn(source)
        )
        // Must use AlarmManager.
        assertTrue(
            "TC-CRON-EXACT-c: CronExactAlarmScheduler must reference `AlarmManager` " +
                "(this is the entire point of the bypass — bypass WorkManager's 15-min floor).",
            source.contains("AlarmManager")
        )
        // Must use setExactAndAllowWhileIdle (the only API that fires precisely
        // even in doze mode, which Android 6+ enforces aggressively).
        assertTrue(
            "TC-CRON-EXACT-c: must use `setExactAndAllowWhileIdle` — `setExact` is " +
                "blocked by doze, `set` is inexact. Only `setExactAndAllowWhileIdle` " +
                "honors short-delay user-facing reminders on idle devices.",
            source.contains("setExactAndAllowWhileIdle")
        )
        // RTC_WAKEUP — wall-clock + wake the device. Required because:
        //  - the user-perceived schedule is wall-clock ("in 5 minutes")
        //  - device may be asleep; without _WAKEUP, alarm fires when next woken,
        //    defeating the precision win.
        assertTrue(
            "TC-CRON-EXACT-c: must use `RTC_WAKEUP` (wall-clock + wake device) — " +
                "`ELAPSED_REALTIME` is wrong scale, missing _WAKEUP loses precision " +
                "on idle devices.",
            source.contains("RTC_WAKEUP")
        )
        // Must build a PendingIntent pointing at our Receiver.
        assertTrue(
            "TC-CRON-EXACT-c: must construct a `PendingIntent`.",
            source.contains("PendingIntent")
        )
        assertTrue(
            "TC-CRON-EXACT-c: PendingIntent target must be `CronExactAlarmReceiver`.",
            source.contains("CronExactAlarmReceiver")
        )
    }

    // ---------- TC-CRON-EXACT-d: Receiver dispatches via CronAgentRunner ----------

    @Test
    fun `TC-CRON-EXACT-d CronExactAlarmReceiver dispatches via CronAgentRunner`() {
        val source = File(exactReceiverPath()).readText()
        assertTrue(
            "TC-CRON-EXACT-d: CronExactAlarmReceiver.kt must declare " +
                "`class CronExactAlarmReceiver : BroadcastReceiver`.",
            Regex("""class\s+CronExactAlarmReceiver\s*:\s*BroadcastReceiver""")
                .containsMatchIn(source)
        )
        assertTrue(
            "TC-CRON-EXACT-d: must override `onReceive`.",
            Regex("""\bfun\s+onReceive\b""").containsMatchIn(source)
        )
        // Must extract jobId from intent extras (this is what links the alarm
        // back to the persisted job record).
        assertTrue(
            "TC-CRON-EXACT-d: onReceive must extract a `jobId` from the Intent " +
                "(the alarm is keyed by jobId so we can re-resolve the job record).",
            source.contains("jobId")
        )
        // Must route through CronAgentRunner — NOT directly into ExternalChat,
        // because CronAgentRunner is what prepends the [CRON CONTEXT] / [CRON 上下文]
        // prompt prefix from R-AGENT-031 that the agent relies on to recognize
        // it's running inside a cron-triggered turn.
        assertTrue(
            "TC-CRON-EXACT-d: must dispatch through `CronAgentRunner` — bypassing " +
                "Runner would lose the [CRON CONTEXT] prompt prefix from R-AGENT-031 " +
                "and break the recursive-cronjob soft-prevention.",
            source.contains("CronAgentRunner")
        )
    }

    // ---------- TC-CRON-EXACT-e: OperitApplication injects on startup ----------

    @Test
    fun `TC-CRON-EXACT-e OperitApplication injects cronShortDelayScheduler on startup`() {
        val source = File(operitApplicationPath()).readText()
        // Must assign the injection slot during onCreate (or equivalent startup hook).
        // Reuse the same pattern as the existing `cronImmediateRunner = { ... }`
        // injection so future maintainers can see the symmetry.
        assertTrue(
            "TC-CRON-EXACT-e: OperitApplication must assign `cronShortDelayScheduler = ...` " +
                "during onCreate so that hermes-android's CronjobTools can route once-short-delay " +
                "jobs to AlarmManager. Without injection, the slot is null and the existing " +
                "15-min PeriodicWork path is used (the bug we're fixing).",
            Regex("""cronShortDelayScheduler\s*=""").containsMatchIn(source)
        )
        // Lambda body should reference the AlarmManager-backed scheduler — pin
        // the bridge between injection slot and concrete impl.
        assertTrue(
            "TC-CRON-EXACT-e: injection lambda must invoke `CronExactAlarmScheduler` " +
                "(the concrete AlarmManager-backed implementation).",
            source.contains("CronExactAlarmScheduler")
        )
    }

    // ---------- TC-CRON-EXACT-f: Manifest registers Receiver, exported=false ----------

    @Test
    fun `TC-CRON-EXACT-f AndroidManifest registers CronExactAlarmReceiver with exported=false`() {
        val source = File(manifestPath()).readText()
        // Receiver node must exist with the `.core.cron.CronExactAlarmReceiver`
        // shorthand (relative to package).
        assertTrue(
            "TC-CRON-EXACT-f: AndroidManifest.xml must register " +
                "`<receiver android:name=\".core.cron.CronExactAlarmReceiver\" ...>`. " +
                "Without registration the alarm will fire but BroadcastReceiver won't run.",
            Regex(
                """<receiver[^>]*android:name\s*=\s*"\.core\.cron\.CronExactAlarmReceiver""""
            ).containsMatchIn(source)
        )
        // Must be exported=false — only AlarmManager (system_server) needs to
        // deliver to it; an exported receiver could be invoked by any app to
        // fake-trigger a cron job.
        val receiverBlock = Regex(
            """<receiver[^>]*android:name\s*=\s*"\.core\.cron\.CronExactAlarmReceiver"[^>]*"""
        ).find(source)?.value ?: ""
        assertTrue(
            "TC-CRON-EXACT-f: CronExactAlarmReceiver must declare " +
                "`android:exported=\"false\"` — exporting it would let any app " +
                "fake-trigger our cron jobs by sending the matching broadcast.",
            receiverBlock.contains("""android:exported="false"""")
        )
        // Permission was already declared by R-AGENT-031 work; assert it's still
        // there so a future cleanup doesn't accidentally remove it.
        assertTrue(
            "TC-CRON-EXACT-f: AndroidManifest must declare the " +
                "`SCHEDULE_EXACT_ALARM` permission (Android 12+ enforces it for " +
                "setExactAndAllowWhileIdle). It was added by earlier work; do not remove.",
            source.contains("SCHEDULE_EXACT_ALARM")
        )
        // Sanity: don't accidentally have two receivers for the same class.
        val matches = Regex(
            """<receiver[^>]*\.core\.cron\.CronExactAlarmReceiver"""
        ).findAll(source).count()
        assertFalse(
            "TC-CRON-EXACT-f: CronExactAlarmReceiver registered more than once " +
                "($matches occurrences) — duplicate registration is a manifest-merger " +
                "smell (likely a copy-paste bug).",
            matches > 1
        )
    }

    // ---------- helpers ----------

    private fun appSrcMainRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit"),
            File("app/src/main/java/com/ai/assistance/operit"),
            File("HermesApp/app/src/main/java/com/ai/assistance/operit"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate app/src/main java root — cwd=${File(".").absolutePath}")
    }

    private fun hermesAndroidSrcMainRoot(): File {
        val candidates = listOf(
            File("../hermes-android/src/main/java/com/xiaomo/hermes"),
            File("hermes-android/src/main/java/com/xiaomo/hermes"),
            File("HermesApp/hermes-android/src/main/java/com/xiaomo/hermes"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "Cannot locate hermes-android src main java root — " +
                    "cwd=${File(".").absolutePath}"
            )
    }

    private fun schedulerPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/cron/Scheduler.kt").path

    private fun cronjobToolsPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/tools/CronjobTools.kt").path

    private fun exactSchedulerPath(): String =
        File(appSrcMainRoot(), "core/cron/CronExactAlarmScheduler.kt").path

    private fun exactReceiverPath(): String =
        File(appSrcMainRoot(), "core/cron/CronExactAlarmReceiver.kt").path

    private fun operitApplicationPath(): String =
        File(appSrcMainRoot(), "core/application/OperitApplication.kt").path

    private fun manifestPath(): String {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("HermesApp/app/src/main/AndroidManifest.xml"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate AndroidManifest.xml — cwd=${File(".").absolutePath}")
    }
}
