/**
 * OpenClaw Source Reference:
 * - src/logger.ts
 */
package com.xiaomo.androidforclaw.logging

/**
 * Log 包装器：同时输出到 android.util.Log 和 AppLog 文件。
 *
 * 替换 import com.xiaomo.androidforclaw.logging.Log → import com.xiaomo.androidforclaw.logging.Log
 * 所有 Log.d/i/w/e/v 调用自动写入 app.log。
 */
object Log {
    fun v(tag: String, msg: String): Int {
        AppLog.v(tag, msg)
        return android.util.Log.v(tag, msg)
    }

    fun v(tag: String, msg: String, tr: Throwable): Int {
        AppLog.v(tag, "$msg\n${android.util.Log.getStackTraceString(tr)}")
        return android.util.Log.v(tag, msg, tr)
    }

    fun d(tag: String, msg: String): Int {
        AppLog.d(tag, msg)
        return android.util.Log.d(tag, msg)
    }

    fun d(tag: String, msg: String, tr: Throwable): Int {
        AppLog.d(tag, msg)
        return android.util.Log.d(tag, msg, tr)
    }

    fun i(tag: String, msg: String): Int {
        AppLog.i(tag, msg)
        return android.util.Log.i(tag, msg)
    }

    fun i(tag: String, msg: String, tr: Throwable): Int {
        AppLog.i(tag, msg)
        return android.util.Log.i(tag, msg, tr)
    }

    fun w(tag: String, msg: String): Int {
        AppLog.w(tag, msg)
        return android.util.Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable): Int {
        AppLog.w(tag, msg)
        return android.util.Log.w(tag, msg, tr)
    }

    fun w(tag: String, tr: Throwable): Int {
        AppLog.w(tag, tr.toString())
        return android.util.Log.w(tag, tr)
    }

    fun e(tag: String, msg: String): Int {
        AppLog.e(tag, msg)
        return android.util.Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable?): Int {
        AppLog.e(tag, msg, tr)
        return android.util.Log.e(tag, msg, tr)
    }

    fun isLoggable(tag: String, level: Int): Boolean {
        return android.util.Log.isLoggable(tag, level)
    }

    fun getStackTraceString(tr: Throwable?): String {
        return android.util.Log.getStackTraceString(tr)
    }

    fun println(priority: Int, tag: String, msg: String): Int {
        when (priority) {
            android.util.Log.VERBOSE -> AppLog.v(tag, msg)
            android.util.Log.DEBUG -> AppLog.d(tag, msg)
            android.util.Log.INFO -> AppLog.i(tag, msg)
            android.util.Log.WARN -> AppLog.w(tag, msg)
            android.util.Log.ERROR -> AppLog.e(tag, msg)
        }
        return android.util.Log.println(priority, tag, msg)
    }
}
