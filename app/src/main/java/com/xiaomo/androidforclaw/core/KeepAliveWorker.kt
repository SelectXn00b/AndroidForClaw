package com.xiaomo.androidforclaw.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.xiaomo.androidforclaw.gateway.GatewayServer

/**
 * Keep-Alive Worker — 定期检查 ForegroundService 是否存活，死了就拉起来
 *
 * 使用 WorkManager 的 periodic work，即使 App 被杀也能被系统调度（best-effort）。
 * 间隔：15 分钟（WorkManager 最小周期）。
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "androidforclaw_keep_alive"

        /**
         * 调度周期性保活检查
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresStorageNotLow(false)
                // 不要求网络（即使断网也要保活）
                .build()

            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(15, java.util.concurrent.TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "📅 KeepAliveWorker 已调度（每 15 分钟检查一次）")
        }

        /**
         * 取消保活调度
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "❌ KeepAliveWorker 已取消")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (!isForegroundServiceRunning()) {
                Log.w(TAG, "⚠️ ForegroundService 未运行，尝试拉起...")
                restartForegroundService()
            } else {
                Log.d(TAG, "✅ ForegroundService 运行正常")
            }
            // 同时检查 GatewayServer 是否存活
            if (!isGatewayServerRunning()) {
                Log.w(TAG, "⚠️ GatewayServer 未运行，尝试拉起...")
                restartGatewayServer()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ KeepAliveWorker 执行失败: ${e.message}")
            Result.retry()
        }
    }

    private fun isForegroundServiceRunning(): Boolean {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (info in am.getRunningServices(Integer.MAX_VALUE)) {
            if (ForegroundService::class.java.name == info.service.className) {
                return true
            }
        }
        return false
    }

    private fun isGatewayServerRunning(): Boolean {
        // 简单检查：尝试连接 Gateway 端口
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 19789), 1000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun restartForegroundService() {
        val intent = Intent(applicationContext, ForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.i(TAG, "🔄 ForegroundService 已重新启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重启 ForegroundService 失败: ${e.message}")
        }
    }

    private fun restartGatewayServer() {
        try {
            val gateway = GatewayServer(applicationContext, port = 19789)
            gateway.start()
            Log.i(TAG, "🔄 GatewayServer 已重新启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重启 GatewayServer 失败: ${e.message}")
        }
    }
}
