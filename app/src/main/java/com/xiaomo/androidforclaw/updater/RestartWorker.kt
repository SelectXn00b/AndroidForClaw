/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.updater

import android.content.Context
import android.content.Intent
import com.xiaomo.androidforclaw.logging.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker that relaunches the app after process death.
 */
class RestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("RestartWorker", "Relaunching app...")
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            applicationContext.startActivity(intent)
        }
        return Result.success()
    }
}
