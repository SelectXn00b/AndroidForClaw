/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.service

import android.content.Context
import android.provider.Settings
import com.xiaomo.hermes.logging.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest

/**
 * ClawIME 管理器
 * 提供对 ClawIME 的直接调用接口,避免使用广播
 *
 * 工作原理:
 * - ClawIME 是同进程的 InputMethodService
 * - 通过单例模式让 ClawIME 注册自己的实例
 * - 其他组件通过此 Manager 直接调用 ClawIME 的方法
 */
object ClawIMEManager {
    private const val TAG = "ClawIMEManager"

    // ClawIME 实例引用
    private var clawImeInstance: ClawIME? = null

    /**
     * 注册 ClawIME 实例 (由 ClawIME.onCreateInputView 调用)
     */
    fun registerInstance(instance: ClawIME) {
        clawImeInstance = instance
        Log.d(TAG, "✓ ClawIME instance registered")
    }

    /**
     * 注销 ClawIME 实例 (由 ClawIME.onDestroy 调用)
     */
    fun unregisterInstance() {
        clawImeInstance = null
        Log.d(TAG, "✓ ClawIME instance unregistered")
    }

    /**
     * 检查 ClawIME 是否为当前启用的输入法
     */
    fun isClawImeEnabled(context: Context): Boolean {
        return try {
            val currentIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val clawImeName = "${context.packageName}/com.xiaomo.hermes.service.ClawIME"
            val isEnabled = currentIme == clawImeName
            Log.d(TAG, "Current IME: $currentIme, ClawIME enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check IME status", e)
            false
        }
    }

    /**
     * 检查 ClawIME 是否已连接
     * 只要实例存在就认为已连接（currentInputConnection 只在编辑会话中才非 null，
     * 但 IME 服务本身是活的，tap 输入框后 connection 会自动就绪）
     */
    fun isConnected(): Boolean {
        val hasInstance = clawImeInstance != null
        val hasIc = clawImeInstance?.currentInputConnection != null
        Log.d(TAG, "isConnected: instance=$hasInstance, inputConnection=$hasIc")
        return hasInstance
    }

    /**
     * 检查当前是否有活跃的输入连接（输入框有焦点且键盘已弹出）
     */
    fun hasActiveInputConnection(): Boolean {
        return clawImeInstance?.currentInputConnection != null
    }

    /**
     * 输入文本（带重试，等待 InputConnection 就绪）
     */
    fun inputText(text: String): Boolean {
        val ime = clawImeInstance
        if (ime == null) {
            Log.e(TAG, "ClawIME instance not available")
            return false
        }

        // 等待 InputConnection 就绪（tap 后可能需要一点时间）
        var ic = ime.currentInputConnection
        if (ic == null) {
            Log.d(TAG, "InputConnection not ready, waiting...")
            for (i in 1..10) {
                try { Thread.sleep(100) } catch (_: InterruptedException) {}
                ic = ime.currentInputConnection
                if (ic != null) {
                    Log.d(TAG, "InputConnection ready after ${i * 100}ms")
                    break
                }
            }
        }

        if (ic == null) {
            Log.e(TAG, "No input connection available after waiting 1s")
            return false
        }

        return try {
            ic.beginBatchEdit()
            ic.commitText(text, 1)
            ic.endBatchEdit()
            Log.d(TAG, "✓ Input text: ${text.take(50)}${if (text.length > 50) "..." else ""}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to input text", e)
            false
        }
    }

    /**
     * 清空输入框
     */
    fun clearText(): Boolean {
        val ic = waitForInputConnection() ?: return false

        return try {
            // REF: stackoverflow/33082004 author: Maxime Epain
            val curPos = ic.getExtractedText(ExtractedTextRequest(), 0)?.text
            if (curPos != null) {
                val beforePos = ic.getTextBeforeCursor(curPos.length, 0)
                val afterPos = ic.getTextAfterCursor(curPos.length, 0)
                ic.deleteSurroundingText(beforePos?.length ?: 0, afterPos?.length ?: 0)
            }
            Log.d(TAG, "✓ Cleared text")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear text", e)
            false
        }
    }

    /**
     * 发送消息 (执行编辑器动作或回车)
     */
    fun sendMessage(): Boolean {
        val ic = waitForInputConnection() ?: return false

        return try {
            // 先尝试 IME_ACTION_SEND
            var sent = ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
            Log.d(TAG, "performEditorAction IME_ACTION_SEND: $sent")

            // 如果失败,再尝试 IME_ACTION_GO
            if (!sent) {
                sent = ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                Log.d(TAG, "performEditorAction IME_ACTION_GO: $sent")
            }

            // 如果还是失败,尝试发送回车键
            if (!sent) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                Log.d(TAG, "sendKeyEvent KEYCODE_ENTER as fallback")
                sent = true
            }

            sent
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            false
        }
    }

    /**
     * 等待 InputConnection 就绪（最多 1 秒）
     */
    private fun waitForInputConnection(): android.view.inputmethod.InputConnection? {
        val ime = clawImeInstance
        if (ime == null) {
            Log.e(TAG, "ClawIME instance not available")
            return null
        }
        var ic = ime.currentInputConnection
        if (ic == null) {
            Log.d(TAG, "InputConnection not ready, waiting...")
            for (i in 1..10) {
                try { Thread.sleep(100) } catch (_: InterruptedException) {}
                ic = ime.currentInputConnection
                if (ic != null) {
                    Log.d(TAG, "InputConnection ready after ${i * 100}ms")
                    break
                }
            }
        }
        if (ic == null) {
            Log.e(TAG, "No input connection available after waiting 1s")
        }
        return ic
    }

    /**
     * 发送按键事件
     */
    fun sendKey(keyCode: Int): Boolean {
        val ic = waitForInputConnection() ?: return false

        return try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Log.d(TAG, "✓ Sent key code: $keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send key", e)
            false
        }
    }

    /**
     * 获取当前输入框中的文本（调试用）
     */
    fun getCurrentText(): String? {
        val ic = clawImeInstance?.currentInputConnection ?: return null
        return try {
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            extracted?.text?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current text", e)
            null
        }
    }
}
