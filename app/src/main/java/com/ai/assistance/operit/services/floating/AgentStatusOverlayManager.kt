package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.ui.features.floating.agentstatus.AgentStatusBall
import com.ai.assistance.operit.ui.features.floating.agentstatus.AgentStatusPanel
import com.ai.assistance.operit.ui.features.floating.agentstatus.AgentStatusUiState

/**
 * 悬浮窗管理器：拖拽小球 + 点击展开状态面板。
 *
 * 结构参考 [UIDebuggerWindowManager]：用 [WindowManager] 直接添加一个 [ComposeView]，
 * 通过切换 [WindowManager.LayoutParams.width]/[WindowManager.LayoutParams.height] 在
 * "圆球" 与 "面板" 两种尺寸之间切换，避免反复 addView / removeView。
 *
 * 状态由外部 [setStatus] 注入；本类不订阅任何业务事件，纯粹是 view container。
 *
 * 拖拽位置通过 SharedPreferences 持久化，下次启动恢复（P1 改进）。
 *
 * @param context 必须是有 WindowManager 服务的 context（Service 即可）；
 *                关闭按钮被点击时会调用 [onCloseRequested] 让 Service 停止自身。
 */
class AgentStatusOverlayManager(
    private val context: Context,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val lifecycleOwner: LifecycleOwner,
    private val onCloseRequested: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    // 状态
    private val isExpanded = mutableStateOf(false)
    private val ballX = mutableStateOf(prefs.getFloat(KEY_BALL_X, DEFAULT_BALL_X))
    private val ballY = mutableStateOf(prefs.getFloat(KEY_BALL_Y, DEFAULT_BALL_Y))
    private val uiState = mutableStateOf(
        AgentStatusUiState(
            platform = "",
            chatIdShort = "",
            statusText = "准备中…",
            elapsedMs = 0L,
            activeChatCount = 0,
        ),
    )

    /** 由 Service 调用：刷新当前展示状态。 */
    fun setStatus(state: AgentStatusUiState) {
        uiState.value = state
    }

    /** 悬浮球是否已经显示。 */
    fun isShowing(): Boolean = composeView != null

    /** 在屏幕上添加悬浮球。已经显示时不重复添加。 */
    fun show() {
        if (composeView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            // FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL：不抢焦点、不拦截外部 touch。
            // FLAG_LAYOUT_IN_SCREEN：允许超出 status bar 区域定位。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballX.value.toInt()
            y = ballY.value.toInt()
        }

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setContent {
                if (isExpanded.value) {
                    AgentStatusPanel(
                        state = uiState.value,
                        onClose = {
                            // 用户主动关 → Service 自己 stop（gateway 仍在跑，下次新 agent
                            // 启动时 Service 会被 GatewayForegroundService 重新拉起）。
                            onCloseRequested()
                        },
                        onCollapse = {
                            isExpanded.value = false
                            updateWindowLayout()
                        },
                    )
                } else {
                    AgentStatusBall(
                        onTap = {
                            isExpanded.value = true
                            updateWindowLayout()
                        },
                        onDrag = { dx, dy ->
                            ballX.value += dx
                            ballY.value += dy
                            updateWindowPosition()
                        },
                        onDragEnd = { persistBallPosition() },
                        errorFlash = uiState.value.errorFlash,
                    )
                }
            }
        }
        windowManager.addView(composeView, params)
    }

    private fun updateWindowPosition() {
        val layoutParams = params ?: return
        val view = composeView ?: return
        layoutParams.x = ballX.value.toInt()
        layoutParams.y = ballY.value.toInt()
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun updateWindowLayout() {
        val layoutParams = params ?: return
        val view = composeView ?: return
        if (isExpanded.value) {
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.x = ballX.value.toInt()
            layoutParams.y = ballY.value.toInt()
        }
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun persistBallPosition() {
        prefs.edit()
            .putFloat(KEY_BALL_X, ballX.value)
            .putFloat(KEY_BALL_Y, ballY.value)
            .apply()
    }

    /** 从屏幕上移除悬浮球。已经移除时无操作。 */
    fun hide() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        composeView = null
        params = null
        isExpanded.value = false
    }

    companion object {
        private const val PREFS_NAME = "agent_status_overlay"
        private const val KEY_BALL_X = "ball_x"
        private const val KEY_BALL_Y = "ball_y"
        private const val DEFAULT_BALL_X = 40f
        private const val DEFAULT_BALL_Y = 240f
    }
}
