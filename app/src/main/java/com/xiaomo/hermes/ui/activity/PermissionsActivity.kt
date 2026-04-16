/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.ui.activity

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 权限页代理：直接跳转到合并进主 app 的 observer 权限页，避免用户看到两层权限页。
 */
class PermissionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.xiaomo.hermes",
                    "com.xiaomo.hermes.accessibility.PermissionActivity"
                )
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } finally {
            finish()
        }
    }
}
