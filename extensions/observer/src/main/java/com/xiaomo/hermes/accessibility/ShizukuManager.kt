/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 *
 * Shizuku 授权管理器。提供:
 * 1. Shizuku 连接状态检测
 * 2. 用户授权请求
 * 3. 权限门控 (未授权时禁止前台操作)
 * 4. Shizuku Shell 命令执行 (特权级)
 */
package com.xiaomo.hermes.accessibility

import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

object ShizukuManager {
    private const val TAG = "ShizukuManager"

    // Shizuku 连接状态
    private val _isShizukuAvailable = MutableLiveData(false)
    val isShizukuAvailable: LiveData<Boolean> = _isShizukuAvailable

    // Shizuku 授权状态
    private val _isShizukuAuthorized = MutableLiveData(false)
    val isShizukuAuthorized: LiveData<Boolean> = _isShizukuAuthorized

    // 综合状态：Shizuku 可用 + 已授权
    val isReady: Boolean
        get() = _isShizukuAvailable.value == true && _isShizukuAuthorized.value == true

    private var listenerRegistered = false

    // ===== 生命周期管理 =====

    /**
     * 初始化 Shizuku 状态检测。应在 Application.onCreate() 调用。
     */
    fun init() {
        if (listenerRegistered) return
        listenerRegistered = true

        try {
            // 检查 Shizuku 是否安装
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "Shizuku 未运行或未安装")
                _isShizukuAvailable.postValue(false)
                _isShizukuAuthorized.postValue(false)
            } else {
                Log.d(TAG, "Shizuku 运行中, 版本: ${Shizuku.getVersion()}")
                _isShizukuAvailable.postValue(true)
                _isShizukuAuthorized.postValue(
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                )
            }

            // 监听 Shizuku 退出
            Shizuku.addBinderReceivedListenerSticky {
                Log.d(TAG, "Shizuku binder received, version: ${Shizuku.getVersion()}")
                _isShizukuAvailable.postValue(true)

                // 检查已有授权
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Log.d(TAG, "Shizuku 需要显示授权说明")
                }
                _isShizukuAuthorized.postValue(
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                )
            }

            Shizuku.addBinderDeadListener {
                Log.w(TAG, "Shizuku binder dead")
                _isShizukuAvailable.postValue(false)
                _isShizukuAuthorized.postValue(false)
            }

            // 监听授权结果
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                Log.d(TAG, "Shizuku permission result: requestCode=$requestCode, grant=$grantResult")
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                _isShizukuAuthorized.postValue(granted)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Shizuku init failed (可能未安装 Shizuku)", e)
            _isShizukuAvailable.postValue(false)
            _isShizukuAuthorized.postValue(false)
        }
    }

    /**
     * 刷新 Shizuku 状态。可在权限页 onResume 时调用。
     */
    fun refreshStatus() {
        try {
            val available = Shizuku.pingBinder()
            _isShizukuAvailable.postValue(available)

            if (available) {
                val authorized = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                _isShizukuAuthorized.postValue(authorized)
                Log.d(TAG, "refreshStatus: available=true, authorized=$authorized")
            } else {
                _isShizukuAuthorized.postValue(false)
                Log.d(TAG, "refreshStatus: Shizuku not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshStatus failed", e)
            _isShizukuAvailable.postValue(false)
            _isShizukuAuthorized.postValue(false)
        }
    }

    /**
     * 清理。在 Application.onTerminate() 调用。
     */
    fun destroy() {
        listenerRegistered = false
    }

    // ===== 授权请求 =====

    private const val REQUEST_CODE_SHIZUKU_PERMISSION = 10088

    /**
     * 请求 Shizuku 授权。弹出系统授权对话框。
     */
    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.d(TAG, "需要向用户说明 Shizuku 用途")
            }
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "请求 Shizuku 授权失败", e)
        }
    }

    // ===== Shell 命令执行 =====

    /**
     * 通过 Shizuku 执行 shell 命令（特权级）。
     * Shizuku API 13 将 newProcess 标记为 private，需要通过反射调用。
     * @return Pair(stdout, exitCode)
     */
    fun exec(cmd: String): Pair<String, Int> {
        if (!isReady) return Pair("Shizuku 未就绪", -1)
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            val output = if (stderr.isNotBlank()) "$stdout\n$stderr".trim() else stdout.trim()
            Log.d(TAG, "exec: $cmd → exit=$exitCode")
            Pair(output, exitCode)
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: $cmd", e)
            Pair("执行失败: ${e.message}", -1)
        }
    }

    /**
     * 通过 Shizuku 启动 Activity（后台，不拉起前台）。
     * @param action Intent action (如 android.intent.action.SET_ALARM)
     * @param extras 额外参数列表，格式: listOf("--es key value", "--ei key value")
     * @param packageName 目标包名（可选）
     */
    fun startActivityViaShell(
        action: String,
        extras: List<String> = emptyList(),
        packageName: String? = null
    ): Pair<String, Int> {
        val cmd = buildString {
            append("am start -a $action --user 0")
            if (packageName != null) append(" -p $packageName")
            extras.forEach { append(" $it") }
        }
        return exec(cmd)
    }

    /**
     * 通过 Shizuku 启动指定 App。
     * @param packageName 应用包名
     * @param activity 完整 Activity 类名（可选，不填则用 monkey 启动）
     */
    fun startAppViaShell(packageName: String, activity: String? = null): Pair<String, Int> {
        val cmd = if (activity != null) {
            "am start -n $packageName/$activity --user 0"
        } else {
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        }
        return exec(cmd)
    }

    // ===== 状态描述 =====

    /**
     * 获取人类可读的状态描述。
     */
    fun getStatusDescription(): String {
        val available = _isShizukuAvailable.value == true
        val authorized = _isShizukuAuthorized.value == true

        return when {
            !available -> "Shizuku 未安装或未运行"
            !authorized -> "Shizuku 未授权"
            else -> "Shizuku 已就绪"
        }
    }
}
