package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import com.ai.assistance.operit.R

/**
 * 基于无障碍服务的UI操作监听器 实现ACCESSIBILITY权限级别的操作监听
 * 通过UIHierarchyManager与系统的无障碍服务进行通信，轮询UI层级变化来检测用户操作。
 */
class AccessibilityActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "AccessibilityActionListener"
        /** 轮询间隔 (ms) — 平衡检测灵敏度和系统负担 */
        private const val POLL_INTERVAL_MS = 800L
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null
    private var pollingScope: CoroutineScope? = null
    private var lastActivityName: String? = null
    private var lastUiHierarchyHash: Int = 0

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ACCESSIBILITY

    override suspend fun isAvailable(): Boolean {
        return try {
            UIHierarchyManager.isAccessibilityServiceEnabled(context)
        } catch (e: Exception) {
            AppLogger.w(TAG, "检查无障碍服务可用性失败: ${e.message}")
            false
        }
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        return try {
            if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
                ActionListener.PermissionStatus.granted()
            } else {
                ActionListener.PermissionStatus.denied(context.getString(R.string.a11y_service_not_enabled))
            }
        } catch (e: Exception) {
            ActionListener.PermissionStatus.denied(context.getString(R.string.a11y_service_not_enabled))
        }
    }

    override fun initialize() {
        AppLogger.d(TAG, "无障碍UI操作监听器已初始化")
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        if (isAvailable()) {
            onResult(true)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onResult(false)
        } catch (e: Exception) {
            AppLogger.e(TAG, "打开无障碍设置失败", e)
            onResult(false)
        }
    }

    override fun isListening(): Boolean = isListening.get()

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ActionListener.ListeningResult.failure(permStatus.reason)
                }

                if (!isListening.compareAndSet(false, true)) {
                    AppLogger.w(TAG, "启动监听失败：已在监听中")
                    return@withContext ActionListener.ListeningResult.failure(context.getString(R.string.admin_already_listening))
                }

                actionCallback = onAction

                // 初始化上一次状态快照
                lastActivityName = try {
                    UIHierarchyManager.getCurrentActivityName(context)
                } catch (_: Exception) { null }
                lastUiHierarchyHash = try {
                    UIHierarchyManager.getUIHierarchy(context)?.hashCode() ?: 0
                } catch (_: Exception) { 0 }

                // 启动轮询协程，定期检查 UI 变化
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                pollingScope = scope
                scope.launch { pollForChanges() }

                AppLogger.d(TAG, "无障碍UI操作监听已启动（轮询模式）")
                ActionListener.ListeningResult.success(context.getString(R.string.a11y_ui_listener_started))
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动无障碍UI操作监听失败", e)
                isListening.set(false)
                actionCallback = null
                ActionListener.ListeningResult.failure(context.getString(R.string.admin_start_failed, e.message))
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.compareAndSet(true, false)) {
                AppLogger.d(TAG, "监听器未在运行，无需停止")
                return@withContext true
            }

            pollingScope?.cancel()
            pollingScope = null
            actionCallback = null
            lastActivityName = null
            lastUiHierarchyHash = 0
            AppLogger.d(TAG, "无障碍UI操作监听已停止")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止无障碍UI操作监听失败", e)
            actionCallback = null
            false
        }
    }

    /**
     * 轮询 UIHierarchyManager 检测 Activity 切换和 UI 层级变化。
     * Activity 变化 → SCREEN_CHANGE 事件
     * UI 层级内容变化（同一 Activity 内）→ CLICK 事件（用户很可能进行了点击操作）
     */
    private suspend fun pollForChanges() {
        while (isListening.get()) {
            try {
                delay(POLL_INTERVAL_MS)
                if (!isListening.get()) break

                val callback = actionCallback ?: continue

                // 获取当前 Activity
                val currentActivity = try {
                    UIHierarchyManager.getCurrentActivityName(context)
                } catch (_: Exception) { null }

                // 获取当前 UI 层级 hash
                val currentUiHash = try {
                    UIHierarchyManager.getUIHierarchy(context)?.hashCode() ?: 0
                } catch (_: Exception) { 0 }

                val now = System.currentTimeMillis()

                // Activity 切换 → SCREEN_CHANGE
                if (currentActivity != null && currentActivity != lastActivityName) {
                    val event = ActionListener.ActionEvent(
                        timestamp = now,
                        actionType = ActionListener.ActionType.SCREEN_CHANGE,
                        elementInfo = ActionListener.ElementInfo(
                            className = currentActivity,
                            packageName = currentActivity.substringBeforeLast(".", "")
                        ),
                        additionalData = mapOf("source" to "accessibility_polling")
                    )
                    lastActivityName = currentActivity
                    lastUiHierarchyHash = currentUiHash
                    try { callback.invoke(event) } catch (e: Exception) {
                        AppLogger.w(TAG, "事件回调失败: ${e.message}")
                    }
                    continue
                }

                // 同一 Activity 内 UI 内容变化 → CLICK（推断用户进行了交互）
                if (currentUiHash != 0 && currentUiHash != lastUiHierarchyHash) {
                    val event = ActionListener.ActionEvent(
                        timestamp = now,
                        actionType = ActionListener.ActionType.CLICK,
                        elementInfo = ActionListener.ElementInfo(
                            className = currentActivity,
                            packageName = currentActivity?.substringBeforeLast(".", "")
                        ),
                        additionalData = mapOf("source" to "accessibility_polling")
                    )
                    lastUiHierarchyHash = currentUiHash
                    try { callback.invoke(event) } catch (e: Exception) {
                        AppLogger.w(TAG, "事件回调失败: ${e.message}")
                    }
                }

                lastActivityName = currentActivity
            } catch (e: Exception) {
                if (!isListening.get()) break
                AppLogger.w(TAG, "轮询UI变化失败: ${e.message}")
                delay(POLL_INTERVAL_MS) // 出错时也等一下再继续
            }
        }
    }
}
