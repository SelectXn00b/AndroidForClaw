/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.float

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.xiaomo.androidforclaw.logging.Log
import android.view.Gravity
import android.widget.TextView
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.tencent.mmkv.MMKV

/**
 * Session info floating window manager
 *
 * Features:
 * - Only shown when app is in background (no activity visible)
 * - Uses ActivityLifecycleCallbacks to track foreground state
 * - Float window never shows on any app screen (main, chat, settings, etc.)
 * - Disabled by default, controlled by in-app switch
 */
object SessionFloatWindow {
    private const val TAG = "SessionFloatWindow"
    private const val FLOAT_TAG = "session_float"

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var isEnabled = false
    private var foregroundActivityCount = 0
    private var sessionInfoTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var latestMessage: String = ""
    private var latestContext: Context? = null
    private var lifecycleRegistered = false

    /**
     * Initialize floating window configuration and register lifecycle callbacks.
     * Call this from MyApplication.onCreate().
     */
    fun init(context: Context) {
        latestContext = context.applicationContext

        // Read switch status from MMKV
        val mmkv = MMKV.defaultMMKV()
        isEnabled = mmkv.decodeBool(MMKVKeys.FLOAT_WINDOW_ENABLED.key, false)

        // Register lifecycle callbacks once
        if (!lifecycleRegistered) {
            val app = context.applicationContext as? Application
            if (app != null) {
                app.registerActivityLifecycleCallbacks(lifecycleCallback)
                lifecycleRegistered = true
                Log.d(TAG, "ActivityLifecycleCallbacks registered")
            } else {
                Log.w(TAG, "Context is not Application, cannot register lifecycle callbacks")
            }
        }

        Log.d(TAG, "SessionFloatWindow initialized, enabled=$isEnabled")
    }

    /**
     * Lifecycle callbacks: track foreground/background state
     */
    private val lifecycleCallback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            foregroundActivityCount++
            if (foregroundActivityCount == 1) {
                Log.d(TAG, "App went to foreground, dismissing float window")
                dismissFloatWindow()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            foregroundActivityCount--
            if (foregroundActivityCount <= 0) {
                foregroundActivityCount = 0
                Log.d(TAG, "App went to background, checking if float window should show")
                if (isEnabled) {
                    latestContext?.let { createFloatWindow(it) }
                }
            }
        }

        // Unused lifecycle callbacks
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /**
     * Set floating window switch status
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        latestContext = context.applicationContext

        // Save to MMKV
        val mmkv = MMKV.defaultMMKV()
        mmkv.encode(MMKVKeys.FLOAT_WINDOW_ENABLED.key, enabled)

        Log.d(TAG, "Float window enabled=$enabled")

        if (enabled) {
            // Only show if app is in background
            if (foregroundActivityCount == 0) {
                createFloatWindow(context)
            }
        } else {
            dismissFloatWindow()
        }
    }

    /**
     * Get floating window switch status
     */
    fun isEnabled(): Boolean {
        return isEnabled
    }

    /**
     * Update session info
     */
    @SuppressLint("SetTextI18n")
    fun updateSessionInfo(title: String, content: String) {
        latestMessage = content
        mainHandler.post {
            titleTextView?.text = "🤖 $title"
            sessionInfoTextView?.text = content.take(100)
        }
        Log.d(TAG, "Updated session info: $title — ${content.take(30)}")
    }

    /**
     * Update with latest chat message (called from AgentLoop/ChatViewModel on IO thread)
     */
    fun updateLatestMessage(message: String) {
        latestMessage = message
        mainHandler.post {
            sessionInfoTextView?.text = message.take(100)
        }
    }

    /**
     * Create floating window
     */
    @SuppressLint("InflateParams")
    private fun createFloatWindow(context: Context) {
        if (EasyFloat.isShow(FLOAT_TAG)) {
            Log.d(TAG, "Float window already exists")
            return
        }

        try {
            EasyFloat.with(context)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_session_float) { view ->
                    titleTextView = view.findViewById(R.id.tv_float_title)
                    sessionInfoTextView = view.findViewById(R.id.tv_session_info)
                    if (latestMessage.isNotEmpty()) {
                        sessionInfoTextView?.text = latestMessage.take(100)
                    }
                }
                .setGravity(Gravity.END or Gravity.TOP, -16, 120)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.RESULT_SIDE)
                .setDragEnable(true)
                .show()

            Log.d(TAG, "Float window created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create float window", e)
        }
    }

    /**
     * Destroy floating window
     */
    private fun dismissFloatWindow() {
        try {
            if (EasyFloat.isShow(FLOAT_TAG)) {
                EasyFloat.dismiss(FLOAT_TAG)
                sessionInfoTextView = null
                Log.d(TAG, "Float window dismissed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss float window", e)
        }
    }
}
