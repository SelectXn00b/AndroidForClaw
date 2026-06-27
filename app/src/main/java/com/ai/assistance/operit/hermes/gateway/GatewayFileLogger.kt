package com.ai.assistance.operit.hermes.gateway

import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes gateway-specific diagnostic logs to an external-storage file
 * at `Downloads/Hermes/gateway_logs/gateway.log`.
 *
 * The file is human-readable and accessible via any file manager on the
 * device, so the user can inspect gateway behavior without adb/logcat.
 *
 * Log entries are timestamped and append-only.  A new session header is
 * written each time the gateway starts.  The file is capped at ~2 MB;
 * when exceeded the oldest half is truncated.
 *
 * **TC-GW-DIAG-002 (2026-06-27): per-run retention.**
 * Callers wrap each agent run with [startRun] / [endRun].  At every
 * [startRun] the file is trimmed so that at most the **immediately prior
 * run** remains — i.e. on disk we keep "previous run + current run = 2
 * runs".  Older runs are deleted in full.  This keeps `gateway.log` short
 * and focused for after-the-fact diagnosis: open the file → you always
 * see the most recent failure and one prior run for comparison.
 */
object GatewayFileLogger {

    private const val DIR_NAME = "gateway_logs"
    private const val FILE_NAME = "gateway.log"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L // 2 MB

    /** Run-boundary markers — also recognized by [trimToLastTwoRuns]
     *  when [startRun] decides where to cut. */
    private const val RUN_START_MARKER = "▶▶▶ RUN START"
    private const val RUN_END_MARKER = "◀◀◀ RUN END"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    private fun resolveLogFile(): File? {
        val existing = logFile
        if (existing != null) return existing
        return try {
            val dir = File(OperitPaths.operitRootDir(), DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            File(dir, FILE_NAME).also { logFile = it }
        } catch (_: Throwable) {
            null
        }
    }

    /** Write a single log line. */
    fun log(level: String, tag: String, msg: String) {
        val file = resolveLogFile() ?: return
        val time = dateFormat.format(Date())
        val line = "$time $level/$tag: $msg\n"
        try {
            trimIfNeeded(file)
            FileWriter(file, true).use { it.write(line) }
        } catch (_: Throwable) {
            // swallow — never crash the gateway for logging
        }
    }

    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)
    fun d(tag: String, msg: String) = log("D", tag, msg)

    /** Write a prominent session-start banner. */
    fun logSessionStart() {
        val file = resolveLogFile() ?: return
        val time = dateFormat.format(Date())
        val banner = "\n${"=".repeat(60)}\n" +
            "  Gateway Session Started — $time\n" +
            "${"=".repeat(60)}\n\n"
        try {
            trimIfNeeded(file)
            FileWriter(file, true).use { it.write(banner) }
        } catch (_: Throwable) {}
    }

    /** Returns the absolute path for display to the user. */
    fun getLogFilePath(): String {
        return resolveLogFile()?.absolutePath ?: "(unavailable)"
    }

    /**
     * TC-GW-DIAG-002: mark the beginning of an agent run.  Before writing
     * the start banner, trims the file so that at most the **immediately
     * prior run** remains.  Net effect: on disk we always retain "previous
     * run + the run that is about to begin".  Older runs are deleted.
     *
     * [label] is embedded in the banner so operators can correlate the run
     * to a chatId / jobId / sessionKey.
     */
    fun startRun(label: String) {
        val file = resolveLogFile() ?: return
        try {
            trimToLastTwoRuns(file)
        } catch (_: Throwable) {}
        val time = dateFormat.format(Date())
        val banner = "\n$RUN_START_MARKER [$time | $label] ▶▶▶\n"
        try {
            FileWriter(file, true).use { it.write(banner) }
        } catch (_: Throwable) {}
    }

    /** TC-GW-DIAG-002: mark the end of an agent run.  No trimming — the
     *  next [startRun] is responsible for dropping older content. */
    fun endRun(label: String) {
        val file = resolveLogFile() ?: return
        val time = dateFormat.format(Date())
        val banner = "$RUN_END_MARKER [$time | $label] ◀◀◀\n\n"
        try {
            FileWriter(file, true).use { it.write(banner) }
        } catch (_: Throwable) {}
    }

    private fun trimIfNeeded(file: File) {
        try {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val content = file.readText()
                // Keep the latter half
                val keepFrom = content.length / 2
                val newStart = content.indexOf('\n', keepFrom)
                if (newStart > 0) {
                    file.writeText("[...truncated...]\n" + content.substring(newStart + 1))
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * TC-GW-DIAG-002: when called from [startRun] (BEFORE the new run's
     * banner is written), keeps **only the immediately prior run** on disk
     * — i.e. the most recent `▶▶▶ RUN START` block.  Anything older is
     * deleted in full.
     *
     * Strategy: scan from the end of the file backward for `RUN_START_MARKER`
     * lines.  If we find at least one, truncate everything before the LAST
     * occurrence.  If we find zero (first run ever, or post-truncate state),
     * leave the file alone — content is already <= 1 run.
     *
     * After [startRun] appends the new banner the file then contains:
     *   "[prev RUN START]  ...  [prev RUN END]  ...  [new RUN START]"
     * which is exactly the "previous + current = 2 runs" invariant.
     */
    private fun trimToLastTwoRuns(file: File) {
        try {
            if (!file.exists() || file.length() == 0L) return
            val content = file.readText()
            val lastStart = content.lastIndexOf(RUN_START_MARKER)
            if (lastStart <= 0) return  // no prior run, or already at top — nothing to trim
            // Cut everything BEFORE the last RUN_START.  Keep that one
            // run intact (it becomes "previous run" after the new banner
            // is appended).
            file.writeText(content.substring(lastStart))
        } catch (_: Throwable) {}
    }
}
