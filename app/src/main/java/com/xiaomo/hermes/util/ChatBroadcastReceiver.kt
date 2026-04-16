/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.hermes.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.core.MyApplication

/**
 * Chat Broadcast Receiver - ADB 测试接口
 *
 * 用途：方便通过 ADB 直接发送消息到聊天界面进行测试
 *
 * 使用方法:
 * adb shell am broadcast -a CLAW_SEND_MESSAGE --es message "你的消息内容"
 *
 * 示例:
 * adb shell am broadcast -a CLAW_SEND_MESSAGE --es message "使用browser搜索openclaw"
 */
class ChatBroadcastReceiver() : BroadcastReceiver() {

    // 可选的回调,用于动态注册时
    private var onMessageReceived: ((String) -> Unit)? = null

    // 提供带回调的构造函数用于动态注册
    constructor(onMessageReceived: (String) -> Unit) : this() {
        this.onMessageReceived = onMessageReceived
    }

    companion object {
        private const val TAG = "ChatBroadcastReceiver"
        const val ACTION_SEND_MESSAGE = "CLAW_SEND_MESSAGE"
        const val EXTRA_MESSAGE = "message"

        /**
         * 创建 IntentFilter
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter(ACTION_SEND_MESSAGE)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "📨 onReceive 被调用 - action: ${intent?.action}")
        if (intent?.action == ACTION_SEND_MESSAGE) {
            val message = intent.getStringExtra(EXTRA_MESSAGE)
            Log.d(TAG, "📨 消息内容: $message")
            if (message != null && message.isNotBlank()) {
                Log.d(TAG, "✅ 收到 ADB 消息: $message")

                // 优先使用回调
                if (onMessageReceived != null) {
                    onMessageReceived?.invoke(message)
                } else {
                    // 通过全局方式发送消息
                    Log.d(TAG, "⚙️ 通过 MyApplication 发送消息")
                    MyApplication.handleChatBroadcast(message)
                }
            } else {
                Log.w(TAG, "⚠️ 收到空消息")
            }
        } else {
            Log.w(TAG, "⚠️ 未知 action: ${intent?.action}")
        }
    }
}
