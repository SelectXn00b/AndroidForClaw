package com.xiaomo.androidforclaw.hermes

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hermes 日志系统
 * 1:1 对齐 hermes-agent/hermes_logging.py
 *
 * 提供 Python logging 模块的 Kotlin 等价实现。
 * 使用 Android Log + 文件日志双通道。
 */

// ── 日志级别 ──────────────────────────────────────────────────────────────
enum class LogLevel(val value: Int, val tag: String) {
    DEBUG(10, "DEBUG"),
    INFO(20, "INFO"),
    WARNING(30, "WARN"),
    ERROR(40, "ERROR"),
    CRITICAL(50, "CRIT"),
}

// ── 日志条目 ──────────────────────────────────────────────────────────────
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val loggerName: String,
    val message: String,
    val exception: String? = null,
)

// ── 日志系统 ──────────────────────────────────────────────────────────────
object HermesLogging {

    private const val TAG = "Hermes"
    private var _logLevel: LogLevel = LogLevel.INFO
    private var _logFile: File? = null
    private val _logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val _maxBufferSize = 1000
    private var _isInitialized = false

    /**
     * 初始化日志系统
     * Python: setup_logging(level="INFO")
     */
    fun initialize(
        level: LogLevel = LogLevel.INFO,
        logFile: File? = null,
        bufferEnabled: Boolean = true,
    ) {
        _logLevel = level
        _logFile = logFile
        _isInitialized = true

        // 创建日志目录
        logFile?.parentFile?.mkdirs()

        Log.i(TAG, "Hermes logging initialized (level=${level.tag})")
    }

    /**
     * 获取当前日志级别
     */
    fun getLogLevel(): LogLevel = _logLevel

    /**
     * 设置日志级别
     */
    fun setLogLevel(level: LogLevel) {
        _logLevel = level
    }

    /**
     * 记录日志
     */
    fun log(level: LogLevel, loggerName: String, message: String, exception: Throwable? = null) {
        if (level.value < _logLevel.value) return

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val exceptionStr = exception?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        // 写入 Android Log
        val androidLevel = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARNING -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            LogLevel.CRITICAL -> Log.ERROR
        }
        Log.println(androidLevel, "$TAG.$loggerName", message)
        if (exception != null) {
            Log.e("$TAG.$loggerName", exceptionStr ?: exception.toString())
        }

        // 写入文件
        _logFile?.let { file ->
            try {
                val entry = "[$timestamp] [${level.tag}] [$loggerName] $message"
                file.appendText(entry + "\n")
                if (exceptionStr != null) {
                    file.appendText(exceptionStr + "\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file: ${e.message}")
            }
        }

        // 缓存日志条目
        val logEntry = LogEntry(timestamp, level, loggerName, message, exceptionStr)
        _logBuffer.add(logEntry)
        while (_logBuffer.size > _maxBufferSize) {
            _logBuffer.poll()
        }
    }

    /**
     * 获取日志缓冲区中的条目
     */
    fun getLogBuffer(): List<LogEntry> = _logBuffer.toList()

    /**
     * 清空日志缓冲区
     */
    fun clearBuffer() {
        _logBuffer.clear()
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFile(): File? = _logFile

    /**
     * 清理旧日志文件
     */
    fun cleanupOldLogs(maxAgeDays: Int = 30) {
        val logDir = _logFile?.parentFile ?: return
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 3600 * 1000L)
        logDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach {
            try {
                it.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old log file: ${it.name}")
            }
        }
    }
}

/**
 * Logger 类（Python logging.Logger 的 Kotlin 等价）
 */
class Logger(private val name: String) {

    fun debug(message: String) {
        HermesLogging.log(LogLevel.DEBUG, name, message)
    }

    fun debug(message: String, exception: Throwable) {
        HermesLogging.log(LogLevel.DEBUG, name, message, exception)
    }

    fun info(message: String) {
        HermesLogging.log(LogLevel.INFO, name, message)
    }

    fun info(message: String, exception: Throwable) {
        HermesLogging.log(LogLevel.INFO, name, message, exception)
    }

    fun warning(message: String) {
        HermesLogging.log(LogLevel.WARNING, name, message)
    }

    fun warning(message: String, exception: Throwable) {
        HermesLogging.log(LogLevel.WARNING, name, message, exception)
    }

    fun error(message: String) {
        HermesLogging.log(LogLevel.ERROR, name, message)
    }

    fun error(message: String, exception: Throwable) {
        HermesLogging.log(LogLevel.ERROR, name, message, exception)
    }

    fun critical(message: String) {
        HermesLogging.log(LogLevel.CRITICAL, name, message)
    }

    fun critical(message: String, exception: Throwable) {
        HermesLogging.log(LogLevel.CRITICAL, name, message, exception)
    }



    fun filter(record: Any?): Boolean {
        return false
    }
    fun _chmodIfManaged(): Any? {
        return null
    }
    fun _open(): Any? {
        return null
    }
    fun doRollover(): Any? {
        return null
    }

}
