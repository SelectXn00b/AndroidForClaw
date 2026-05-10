package com.ai.assistance.operit.util

import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.view.Choreographer
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostics installer for the app's main thread.
 *
 * Two lightweight signals:
 *
 * 1. **Frame-skip logger** — a `Choreographer.FrameCallback` records the gap
 *    between consecutive `doFrame` callbacks. A normal frame is ~16 ms (60 Hz)
 *    or ~11 ms (90 Hz). A gap of ≥ [FRAME_SKIP_THRESHOLD_MS] indicates the
 *    main thread was blocked. Logged via [AppLogger.w] under tag `frame-skip`,
 *    so it ends up in `operit.log` and reaches the feedback server.
 *
 * 2. **StrictMode penaltyLog** — detects disk reads / writes / network on the
 *    main thread. Violations are logged to logcat (so they also enter
 *    `operit.log` if `AppLogger` mirrors them — currently it doesn't, so
 *    StrictMode here is most useful in `adb logcat`).
 *
 * Both signals are cheap. Frame-skip costs one Long compare per frame.
 */
object MainThreadHealthInstaller {

    /** Threshold for "skipped frame" warning. ~3 frames at 60 Hz. */
    private const val FRAME_SKIP_THRESHOLD_MS = 50L

    /** Throttle frame-skip warnings to at most one per [WARN_THROTTLE_MS]. */
    private const val WARN_THROTTLE_MS = 500L

    private const val TAG = "frame-skip"

    private val installed = AtomicLong(0L)

    fun install() {
        if (!installed.compareAndSet(0L, SystemClock.uptimeMillis())) return

        installFrameSkipLogger()
        installStrictMode()
    }

    private fun installFrameSkipLogger() {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            private var lastFrameNs: Long = 0L
            private var lastWarnUptimeMs: Long = 0L

            override fun doFrame(frameTimeNanos: Long) {
                val prev = lastFrameNs
                lastFrameNs = frameTimeNanos
                if (prev != 0L) {
                    val gapMs = (frameTimeNanos - prev) / 1_000_000
                    if (gapMs >= FRAME_SKIP_THRESHOLD_MS) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastWarnUptimeMs >= WARN_THROTTLE_MS) {
                            lastWarnUptimeMs = now
                            val activity = try {
                                ActivityLifecycleManager.getCurrentActivity()
                                    ?.javaClass?.simpleName
                            } catch (_: Throwable) {
                                null
                            }
                            AppLogger.w(
                                TAG,
                                "Skipped frames gapMs=$gapMs (~${gapMs / 16}f) topActivity=$activity uptimeMs=$now"
                            )
                        }
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
    }

    private fun installStrictMode() {
        // In debug builds, log violations to logcat (penaltyLog).
        // In release we don't want to risk crashes from latent violations,
        // so only debug gets StrictMode — diagnostics, not enforcement.
        if (!BuildConfig.DEBUG) return
        try {
            val threadPolicy = StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
            StrictMode.setThreadPolicy(threadPolicy)

            val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("NewApi")
                vmPolicyBuilder.detectContentUriWithoutPermission()
            }
            StrictMode.setVmPolicy(vmPolicyBuilder.build())
        } catch (e: Throwable) {
            AppLogger.w(TAG, "Failed to install StrictMode: ${e.message}")
        }
    }
}
