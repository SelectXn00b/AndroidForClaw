package com.ai.assistance.operit.hermes.gateway

import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * R-OBS-001: structured file logger for the **Weixin transport layer**
 * (`WeixinAdapter.connect` / `send` / `_runPollLoop` / `_handleInbound`).
 *
 * Sibling of [CronFileLogger]; both live under
 * `/sdcard/Download/Hermes/cron_logs/` (the cron dispatch chain
 * directory) but write to different files so a `grep -E 'send|errcode'`
 * over `weixin.log` is signal-dense.
 *
 * Adapter is wired via the [PlatformDiagSink] interface in the
 * `hermes-android` module (the `app` module wraps this object as a
 * `PlatformDiagSink` implementation and injects it through the
 * `HermesGatewayController`).  This sidesteps the layering rule
 * forbidding `hermes-android` from reverse-depending on `app/`.
 */
object WeixinFileLogger {

    private const val DIR_NAME = "cron_logs"
    private const val FILE_NAME = "weixin.log"
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L // 2 MB

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

    fun getLogFilePath(): String {
        return resolveLogFile()?.absolutePath ?: "(unavailable)"
    }

    private fun trimIfNeeded(file: File) {
        try {
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val content = file.readText()
                val keepFrom = content.length / 2
                val newStart = content.indexOf('\n', keepFrom)
                if (newStart > 0) {
                    file.writeText("[...truncated...]\n" + content.substring(newStart + 1))
                }
            }
        } catch (_: Throwable) {}
    }
}
