package com.ai.assistance.operit.services.skillrecorder

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder.SkillRecorderOverlayContent
import com.ai.assistance.operit.util.AppLogger

/**
 * 管理 Skill Recorder 的悬浮窗（录制控制球 + 描述输入面板）。
 * 使用 WindowManager + ComposeView，参考 UIDebuggerWindowManager 的模式。
 */
class SkillRecorderOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "SkillRecorderOverlay"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private val lifecycleOwner = ServiceLifecycleOwner()

    // Overlay 模式
    enum class Mode { RECORDING_BALL, LABELING_PANEL, BUILDING_BALL }

    private var currentMode = mutableStateOf(Mode.RECORDING_BALL)
    var isExpanded = mutableStateOf(false)
        private set

    // 悬浮球位置
    var ballX = mutableStateOf(100f)
        private set
    var ballY = mutableStateOf(300f)
        private set

    /**
     * 显示录制控制球（STEP_RECORDING / STEP_PAUSED 状态）。
     */
    fun showRecordingBall() {
        currentMode.value = Mode.RECORDING_BALL
        isExpanded.value = false
        if (composeView == null) {
            createOverlay()
        } else {
            switchToBallLayout()
        }
    }

    /**
     * 切换为描述输入面板（STEP_LABELING 状态）。
     */
    fun showLabelingPanel() {
        currentMode.value = Mode.LABELING_PANEL
        isExpanded.value = false
        if (composeView == null) {
            createOverlay()
            switchToLabelingLayout()
        } else {
            switchToLabelingLayout()
        }
    }

    /**
     * 切换为构建模式悬浮球（BUILDING 状态），允许用户直接开始下一段录制。
     */
    fun showBuildingBall() {
        currentMode.value = Mode.BUILDING_BALL
        isExpanded.value = false
        if (composeView == null) {
            createOverlay()
        } else {
            switchToBallLayout()
        }
    }

    /**
     * 移除悬浮窗。
     */
    fun remove() {
        try {
            composeView?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "remove overlay failed: ${e.message}")
        }
        composeView = null
        params = null
        isExpanded.value = false
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onBallDrag(deltaX: Float, deltaY: Float) {
        ballX.value += deltaX
        ballY.value += deltaY
        updateWindowPosition()
    }

    fun toggleExpand() {
        isExpanded.value = !isExpanded.value
        if (isExpanded.value) {
            switchToExpandedBallLayout()
        } else {
            switchToBallLayout()
        }
    }

    private fun createOverlay() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballX.value.toInt()
            y = ballY.value.toInt()
        }

        composeView = ComposeView(context).apply {
            // 防止 overlay 被无障碍服务捕获，避免影响 UI hierarchy hash
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                SkillRecorderOverlayContent(
                    mode = currentMode.value,
                    recordingState = SkillRecorderService.recordingState,
                    stepFrameCount = SkillRecorderService.stepFrameCount,
                    isExpanded = isExpanded.value,
                    onToggleExpand = { toggleExpand() },
                    onBallDrag = { dx, dy -> onBallDrag(dx, dy) },
                    onPause = {
                        SkillRecorderService.sendAction(context, SkillRecorderNotification.ACTION_PAUSE)
                    },
                    onResume = {
                        SkillRecorderService.sendAction(context, SkillRecorderNotification.ACTION_RESUME)
                    },
                    onStop = {
                        SkillRecorderService.sendAction(context, SkillRecorderNotification.ACTION_STOP)
                    },
                    onDiscard = {
                        SkillRecorderService.sendAction(context, SkillRecorderNotification.ACTION_DISCARD)
                    },
                    onConfirmLabel = { label ->
                        SkillRecorderService.commitStepWithLabel(label)
                    },
                    onDiscardLabel = {
                        SkillRecorderService.discardStepBuffer()
                    },
                    onStartRecording = {
                        SkillRecorderService.start(context)
                    },
                    onFinishBuilding = {
                        remove()
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun switchToBallLayout() {
        params?.let { p ->
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            p.x = ballX.value.toInt()
            p.y = ballY.value.toInt()
            composeView?.let { windowManager.updateViewLayout(it, p) }
        }
    }

    private fun switchToExpandedBallLayout() {
        params?.let { p ->
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            p.x = ballX.value.toInt()
            p.y = ballY.value.toInt()
            composeView?.let { windowManager.updateViewLayout(it, p) }
        }
    }

    private fun switchToLabelingLayout() {
        params?.let { p ->
            // 输入面板需要 focusable 以接收键盘输入
            p.width = WindowManager.LayoutParams.MATCH_PARENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            p.x = 0
            p.y = 0
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            composeView?.let { windowManager.updateViewLayout(it, p) }
        }
    }

    private fun updateWindowPosition() {
        if (currentMode.value == Mode.LABELING_PANEL) return
        params?.let { p ->
            p.x = ballX.value.toInt()
            p.y = ballY.value.toInt()
            composeView?.let { windowManager.updateViewLayout(it, p) }
        }
    }
}
