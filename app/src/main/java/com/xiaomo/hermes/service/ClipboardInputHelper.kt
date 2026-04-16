/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.xiaomo.hermes.accessibility.AccessibilityProxy
import com.xiaomo.hermes.logging.Log

/**
 * 剪切板输入助手
 *
 * 通过剪切板实现文本输入，避免 ClawIME 键盘的各种问题。
 * 流程：写入剪切板 → 找到焦点输入框 → 执行粘贴操作
 *
 * 优势：
 * - 不需要切换输入法
 * - 支持所有字符（中文、emoji 等）
 * - 比 ClawIME 更稳定
 *
 * 限制：
 * - Android 10+ 后台应用访问剪切板受限
 * - 需要无障碍服务来执行粘贴操作
 */
object ClipboardInputHelper {
    private const val TAG = "ClipboardInputHelper"

    /**
     * 通过剪切板 + 无障碍粘贴输入文本
     *
     * @param context 应用 Context
     * @param text 要输入的文本
     * @return 是否成功
     */
    fun inputTextViaClipboard(context: Context, text: String): Boolean {
        try {
            // 1. 获取 ClipboardManager
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager == null) {
                Log.e(TAG, "ClipboardManager not available")
                return false
            }

            // 保存旧剪切板内容，操作完后恢复
            val oldClip = try {
                clipboardManager.primaryClip
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read old clipboard (expected on Android 10+): ${e.message}")
                null
            }

            // 2. 写入新文本到剪切板
            val clip = ClipData.newPlainText("claw_input", text)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "✓ Clipboard set: ${text.take(50)}${if (text.length > 50) "..." else ""}")

            // 3. 通过无障碍服务找焦点节点执行粘贴
            val pasted = performPasteViaAccessibility()
            if (!pasted) {
                Log.e(TAG, "Paste via accessibility failed")
                // 尝试恢复旧剪切板
                restoreClipboard(clipboardManager, oldClip)
                return false
            }

            // 4. 短暂延迟后恢复旧剪切板内容
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                restoreClipboard(clipboardManager, oldClip)
            }, 500)

            Log.d(TAG, "✓ Text input via clipboard successful")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard input failed", e)
            return false
        }
    }

    /**
     * 通过无障碍服务执行粘贴操作
     */
    private fun performPasteViaAccessibility(): Boolean {
        val service = com.xiaomo.hermes.accessibility.service.AccessibilityBinderService.serviceInstance
        if (service == null) {
            Log.e(TAG, "Accessibility service not available")
            return false
        }

        val root = service.rootInActiveWindow
        if (root == null) {
            Log.e(TAG, "No root window available")
            return false
        }

        // 找到当前焦点的可编辑节点
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode == null) {
            Log.e(TAG, "No focused input node found")
            return false
        }

        if (!focusedNode.isEditable) {
            Log.e(TAG, "Focused node is not editable")
            return false
        }

        // 执行粘贴操作
        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.d(TAG, "ACTION_PASTE result: $success")
        return success
    }

    /**
     * 恢复旧的剪切板内容
     */
    private fun restoreClipboard(clipboardManager: ClipboardManager, oldClip: ClipData?) {
        try {
            if (oldClip != null) {
                clipboardManager.setPrimaryClip(oldClip)
                Log.d(TAG, "Old clipboard restored")
            } else {
                // 清空剪切板，避免泄露输入内容
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                Log.d(TAG, "Clipboard cleared")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore clipboard: ${e.message}")
        }
    }

    /**
     * 检查剪切板是否可用
     * Android 10+ 限制后台应用访问剪切板，但我们的 App 通常在前台或有无障碍服务
     */
    fun isClipboardAvailable(context: Context): Boolean {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager == null) {
                false
            } else {
                // 尝试写入测试内容
                val testClip = ClipData.newPlainText("claw_test", "test")
                clipboardManager.setPrimaryClip(testClip)
                // 清理
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard not available: ${e.message}")
            false
        }
    }

    /**
     * 检查无障碍粘贴是否可用（需要无障碍服务）
     */
    fun isPasteAvailable(): Boolean {
        val service = com.xiaomo.hermes.accessibility.service.AccessibilityBinderService.serviceInstance
        return service != null
    }
}
