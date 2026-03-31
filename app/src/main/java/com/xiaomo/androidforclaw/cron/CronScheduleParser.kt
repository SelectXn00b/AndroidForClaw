/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/parse.ts, schedule.ts
 *
 * AndroidForClaw adaptation: cron scheduling.
 */
package com.xiaomo.androidforclaw.cron

import com.xiaomo.androidforclaw.logging.Log
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

object CronScheduleParser {
    private const val TAG = "CronScheduleParser"

    fun computeNextRunAtMs(schedule: CronSchedule, nowMs: Long): Long? {
        return when (schedule) {
            is CronSchedule.At -> {
                val atMs = parseAbsoluteTimeMs(schedule.at)
                if (atMs > nowMs) atMs else null
            }
            is CronSchedule.Every -> {
                val everyMs = maxOf(1, schedule.everyMs)
                val anchor = maxOf(0, schedule.anchorMs ?: nowMs)
                if (nowMs < anchor) anchor
                else {
                    val elapsed = nowMs - anchor
                    val steps = maxOf(1, (elapsed + everyMs - 1) / everyMs)
                    anchor + steps * everyMs
                }
            }
            is CronSchedule.Cron -> computeSimpleCronNextRun(schedule.expr, nowMs)
        }
    }

    /**
     * Compute the previous (most recent past) run time for a schedule.
     * Aligned with OpenClaw schedule.ts computePreviousRunAtMs.
     * Only meaningful for cron-type schedules.
     */
    fun computePreviousRunAtMs(schedule: CronSchedule, nowMs: Long): Long? {
        if (schedule !is CronSchedule.Cron) return null
        return computeSimpleCronPreviousRun(schedule.expr, nowMs)
    }

    private fun parseAbsoluteTimeMs(at: String): Long {
        return try {
            if (at.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(at)?.time ?: 0
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(at)?.time ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse time: $at", e)
            0
        }
    }

    private fun computeSimpleCronNextRun(expr: String, nowMs: Long): Long? {
        try {
            val parts = expr.trim().split(Regex("\\s+"))
            if (parts.size != 5) return null

            val (minute, hour, _, _, _) = parts

            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            repeat(24 * 60) {
                calendar.add(Calendar.MINUTE, 1)
                val matchesMinute = minute == "*" || calendar.get(Calendar.MINUTE) == minute.toIntOrNull()
                val matchesHour = hour == "*" || calendar.get(Calendar.HOUR_OF_DAY) == hour.toIntOrNull()

                if (matchesMinute && matchesHour) {
                    val nextMs = calendar.timeInMillis
                    return if (nextMs > nowMs) nextMs else null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute cron: $expr", e)
            return null
        }
    }

    /**
     * Compute the previous (most recent past) cron match.
     * Walks backward minute-by-minute from nowMs to find the last matching slot.
     * Aligned with OpenClaw schedule.ts computePreviousRunAtMs (croner previousRuns).
     */
    private fun computeSimpleCronPreviousRun(expr: String, nowMs: Long): Long? {
        try {
            val parts = expr.trim().split(Regex("\\s+"))
            if (parts.size != 5) return null

            val (minute, hour, _, _, _) = parts

            val calendar = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Walk backward up to 48 hours
            repeat(48 * 60) {
                calendar.add(Calendar.MINUTE, -1)
                val matchesMinute = minute == "*" || calendar.get(Calendar.MINUTE) == minute.toIntOrNull()
                val matchesHour = hour == "*" || calendar.get(Calendar.HOUR_OF_DAY) == hour.toIntOrNull()

                if (matchesMinute && matchesHour) {
                    val prevMs = calendar.timeInMillis
                    return if (prevMs < nowMs) prevMs else null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute previous cron: $expr", e)
            return null
        }
    }

    /**
     * Compute a stable per-job stagger offset from the job ID.
     * Aligned with OpenClaw jobs.ts resolveStableCronOffsetMs.
     */
    fun resolveStableCronOffsetMs(jobId: String, staggerMs: Long): Long {
        if (staggerMs <= 1) return 0
        val digest = MessageDigest.getInstance("SHA-256").digest(jobId.toByteArray())
        // Read first 4 bytes as unsigned int, mod staggerMs
        val raw = ((digest[0].toLong() and 0xFF) shl 24) or
                  ((digest[1].toLong() and 0xFF) shl 16) or
                  ((digest[2].toLong() and 0xFF) shl 8) or
                  (digest[3].toLong() and 0xFF)
        return (raw and 0x7FFFFFFF) % staggerMs
    }

    /**
     * Compute next run for a cron schedule with stagger offset applied.
     * Aligned with OpenClaw jobs.ts computeStaggeredCronNextRunAtMs.
     */
    fun computeStaggeredCronNextRun(schedule: CronSchedule.Cron, jobId: String, nowMs: Long): Long? {
        val staggerMs = schedule.staggerMs ?: 0
        val offsetMs = resolveStableCronOffsetMs(jobId, staggerMs)
        if (offsetMs <= 0) return computeSimpleCronNextRun(schedule.expr, nowMs)

        // Shift cursor backward by offset to find base slot, then add offset back
        var cursorMs = maxOf(0L, nowMs - offsetMs)
        for (attempt in 0 until 4) {
            val baseNext = computeSimpleCronNextRun(schedule.expr, cursorMs) ?: return null
            val shifted = baseNext + offsetMs
            if (shifted > nowMs) return shifted
            cursorMs = maxOf(cursorMs + 1, baseNext + 1000)
        }
        return null
    }

    /**
     * Compute previous run for a cron schedule with stagger offset applied.
     * Aligned with OpenClaw jobs.ts computeStaggeredCronPreviousRunAtMs.
     */
    fun computeStaggeredCronPreviousRun(schedule: CronSchedule.Cron, jobId: String, nowMs: Long): Long? {
        val staggerMs = schedule.staggerMs ?: 0
        val offsetMs = resolveStableCronOffsetMs(jobId, staggerMs)
        if (offsetMs <= 0) return computeSimpleCronPreviousRun(schedule.expr, nowMs)

        var cursorMs = maxOf(0L, nowMs - offsetMs)
        for (attempt in 0 until 4) {
            val basePrevious = computeSimpleCronPreviousRun(schedule.expr, cursorMs) ?: return null
            val shifted = basePrevious + offsetMs
            if (shifted <= nowMs) return shifted
            cursorMs = maxOf(0L, basePrevious - 1000)
        }
        return null
    }

    fun errorBackoffMs(consecutiveErrors: Int, backoffSchedule: List<Long>): Long {
        val idx = minOf(consecutiveErrors - 1, backoffSchedule.size - 1)
        return backoffSchedule[maxOf(0, idx)]
    }
}
