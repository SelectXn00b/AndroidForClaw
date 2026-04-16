/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.accessibility

import android.content.Context
import android.util.Log
import com.xiaomo.hermes.accessibility.service.AccessibilityBinderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccessibilityHealthMonitor(private val context: Context) {
    companion object {
        private const val TAG = "AccessibilityHealthMonitor"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val checkInterval = 5000L // 5 seconds

    fun startMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val available = AccessibilityBinderService.serviceInstance != null
                    if (!available) {
                        Log.d(TAG, "serviceInstance is null, waiting for observer to start")
                    } else if (!AccessibilityProxy.isServiceReady()) {
                        Log.w(TAG, "Service instance exists but not ready")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Health check failed", e)
                }

                delay(checkInterval)
            }
        }
    }

    fun stopMonitoring() {
        scope.cancel()
    }
}
