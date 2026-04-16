package com.xiaomo.hermes.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.CompletableDeferred

/**
 * 透明 Activity，用于在后台 Skill 调用时弹出相机权限请求。
 *
 * 流程:
 * 1. EyeSkill 检测到没有 CAMERA 权限
 * 2. 启动 CameraPermissionActivity（透明、无UI）
 * 3. 弹出系统权限弹窗
 * 4. 如果用户之前拒绝过且选了"不再询问"，弹 Toast 引导去设置页
 * 5. 结果通过 CompletableDeferred 回传给 EyeSkill
 */
class CameraPermissionActivity : Activity() {

    companion object {
        private const val TAG = "CameraPermission"
        private const val REQUEST_CODE_CAMERA = 1001
        private const val REQUEST_CODE_SETTINGS = 1002

        // 用于等待权限结果的 deferred
        @Volatile
        var pendingResult: CompletableDeferred<Boolean>? = null

        /**
         * 从后台请求相机权限
         * @return true=已授权, false=用户拒绝
         */
        suspend fun requestPermission(context: Context): Boolean {
            // 已经有权限
            if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }

            val deferred = CompletableDeferred<Boolean>()
            pendingResult = deferred

            try {
                val intent = Intent(context, CameraPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return deferred.await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch permission activity", e)
                pendingResult = null
                return false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否已有权限（可能在 Activity 启动前就授了）
        if (checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Already have CAMERA permission")
            completeAndFinish(true)
            return
        }

        // 检查是否可以弹系统权限弹窗
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // 用户之前拒绝过但没选"不再询问"，可以再次弹窗
            Log.d(TAG, "Requesting CAMERA permission (rationale shown)")
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        } else {
            // 两种情况：
            // 1. 首次请求 → 弹系统弹窗
            // 2. 用户选了"不再询问" → 系统不弹窗，需引导去设置
            // 先尝试弹窗，如果回调里是 DENIED 再跳设置
            Log.d(TAG, "Requesting CAMERA permission (first time or denied permanently)")
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_CAMERA) return

        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "CAMERA permission granted via dialog")
            completeAndFinish(true)
        } else {
            // 被拒绝了
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.CAMERA
                )
            ) {
                // 用户选了"不再询问"，引导去设置页
                Log.d(TAG, "CAMERA permission permanently denied, opening settings")
                Toast.makeText(
                    this,
                    "请在设置中手动开启相机权限",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
            } else {
                // 用户只是点了"拒绝"
                Log.d(TAG, "CAMERA permission denied by user")
                completeAndFinish(false)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SETTINGS) {
            // 从设置页返回，检查权限是否已授予
            val granted = checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Returned from settings, granted=$granted")
            completeAndFinish(granted)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivityForResult(intent, REQUEST_CODE_SETTINGS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            completeAndFinish(false)
        }
    }

    private fun completeAndFinish(granted: Boolean) {
        pendingResult?.complete(granted)
        pendingResult = null
        finish()
    }
}
