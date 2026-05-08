package com.ai.assistance.operit.services.skillrecorder

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import com.ai.assistance.operit.core.skillrecorder.FrameCapture
import com.ai.assistance.operit.core.skillrecorder.SkillSummarizer
import com.ai.assistance.operit.core.tools.system.action.ActionListener
import com.ai.assistance.operit.core.tools.system.action.ActionManager
import com.ai.assistance.operit.data.model.skillrecorder.BuilderStep
import com.ai.assistance.operit.data.model.skillrecorder.RecordingFrame
import com.ai.assistance.operit.data.model.skillrecorder.RecordingSession
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Skill Recorder 前台服务。
 * 通过无障碍事件回调录制用户操作，支持分步录制模式。
 * 每次仅在 STEP_RECORDING 时运行前台服务。
 */
class SkillRecorderService : Service() {

    companion object {
        private const val TAG = "SkillRecorderService"
        private const val CALLBACK_ID = "skill_recorder"
        private const val POLL_INTERVAL_MS = 800L

        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState = _recordingState.asStateFlow()

        private val _currentSession = MutableStateFlow<RecordingSession?>(null)
        val currentSession = _currentSession.asStateFlow()

        /** 当前步骤录制的帧数 */
        private val _stepFrameCount = MutableStateFlow(0)
        val stepFrameCount = _stepFrameCount.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        /** Model config ID selected by user for AI summarization */
        @Volatile var selectedModelConfigId: String? = null

        /** Job tracking the current summarization coroutine, for cancellation */
        private var summarizationJob: Job? = null

        /** Shared scope for summarization work (avoids creating new scopes each time) */
        private val summarizationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /** 当前步骤的帧缓冲（线程安全） */
        private val _stepFrameBuffer = java.util.Collections.synchronizedList(mutableListOf<RecordingFrame>())

        /** 悬浮窗管理器（静态引用，生存周期独立于 Service 实例） */
        private var _overlayManager: SkillRecorderOverlayManager? = null

        // ──── 构建器管理方法 ────

        /** 开始一个构建会话（不启动前台服务） */
        fun startBuildSession(draftText: String?) {
            val session = RecordingSession(draftText = draftText)
            _currentSession.value = session
            _recordingState.value = RecordingState.BUILDING
        }

        /** 启动前台服务开始录制一个步骤 */
        fun start(context: Context) {
            val intent = Intent(context, SkillRecorderService::class.java)
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, SkillRecorderService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }

        /** 将帧缓冲提交为一个 BuilderStep.Record */
        fun commitStepFrames(): BuilderStep.Record? {
            val session = _currentSession.value ?: run {
                AppLogger.w(TAG, "commitStepFrames: no session")
                return null
            }
            val framesCopy: List<RecordingFrame>
            synchronized(_stepFrameBuffer) {
                if (_stepFrameBuffer.isEmpty()) {
                    AppLogger.w(TAG, "commitStepFrames: buffer empty, nothing to commit")
                    return null
                }
                AppLogger.i(TAG, "commitStepFrames: committing ${_stepFrameBuffer.size} frames as step #${session.steps.size}")
                framesCopy = ArrayList(_stepFrameBuffer)
                _stepFrameBuffer.clear()
            }
            val step = BuilderStep.Record(
                orderIndex = session.steps.size,
                frames = framesCopy,
                startTime = framesCopy.first().timestamp,
                endTime = framesCopy.last().timestamp
            )
            // 创建新的 steps 列表再赋值，避免先修改原 session 导致 StateFlow equals() 去重不触发更新
            val newSteps = ArrayList(session.steps).apply { add(step) }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
            _stepFrameCount.value = 0
            AppLogger.i(TAG, "commitStepFrames: session now has ${newSteps.size} steps")
            return step
        }

        /** 添加一个思考步骤 */
        fun addThinkStep(content: String) {
            val session = _currentSession.value ?: return
            val step = BuilderStep.Think(
                orderIndex = session.steps.size,
                content = content
            )
            val newSteps = ArrayList(session.steps).apply { add(step) }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /** 更新思考步骤内容 */
        fun updateThinkStep(stepId: String, newContent: String) {
            val session = _currentSession.value ?: return
            val idx = session.steps.indexOfFirst { it.id == stepId }
            if (idx < 0) return
            val old = session.steps[idx]
            if (old is BuilderStep.Think) {
                val newSteps = ArrayList(session.steps)
                newSteps[idx] = old.copy(content = newContent)
                _currentSession.value = session.copy(steps = newSteps.toMutableList())
            }
        }

        /** 将帧缓冲提交为带描述的 BuilderStep.Record（用户输入 label 后调用） */
        fun commitStepWithLabel(label: String) {
            val session = _currentSession.value ?: run {
                AppLogger.w(TAG, "commitStepWithLabel: no session")
                _recordingState.value = RecordingState.BUILDING
                _overlayManager?.remove()
                return
            }
            val framesCopy: List<RecordingFrame>
            synchronized(_stepFrameBuffer) {
                framesCopy = ArrayList(_stepFrameBuffer)
                _stepFrameBuffer.clear()
            }
            if (framesCopy.isNotEmpty()) {
                val step = BuilderStep.Record(
                    orderIndex = session.steps.size,
                    label = label.trim(),
                    frames = framesCopy,
                    startTime = framesCopy.first().timestamp,
                    endTime = framesCopy.last().timestamp
                )
                val newSteps = ArrayList(session.steps).apply { add(step) }
                _currentSession.value = session.copy(steps = newSteps.toMutableList())
                AppLogger.i(TAG, "commitStepWithLabel: label=\"$label\", ${framesCopy.size} frames")
            }
            _stepFrameCount.value = 0
            _recordingState.value = RecordingState.BUILDING
            _overlayManager?.showBuildingBall()
        }

        /** 更新录制步骤的 label */
        fun updateRecordLabel(stepId: String, newLabel: String) {
            val session = _currentSession.value ?: return
            val idx = session.steps.indexOfFirst { it.id == stepId }
            if (idx < 0) return
            val old = session.steps[idx]
            if (old is BuilderStep.Record) {
                val newSteps = ArrayList(session.steps)
                newSteps[idx] = old.copy(label = newLabel.trim())
                _currentSession.value = session.copy(steps = newSteps.toMutableList())
            }
        }

        /** 丢弃当前步骤帧缓冲（从 STEP_LABELING 回到 BUILDING） */
        fun discardStepBuffer() {
            _stepFrameBuffer.clear()
            _stepFrameCount.value = 0
            _recordingState.value = RecordingState.BUILDING
            _overlayManager?.showBuildingBall()
            AppLogger.i(TAG, "discardStepBuffer: 帧缓冲已丢弃")
        }

        /** 删除一个步骤 */
        fun removeStep(stepId: String) {
            val session = _currentSession.value ?: return
            val newSteps = ArrayList(session.steps)
            newSteps.removeAll { it.id == stepId }
            // 重新编号
            newSteps.forEachIndexed { i, step ->
                when (step) {
                    is BuilderStep.Record -> newSteps[i] = step.copy(orderIndex = i)
                    is BuilderStep.Think -> newSteps[i] = step.copy(orderIndex = i)
                }
            }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /** 移动步骤位置 */
        fun moveStep(fromIndex: Int, toIndex: Int) {
            val session = _currentSession.value ?: return
            if (fromIndex < 0 || fromIndex >= session.steps.size ||
                toIndex < 0 || toIndex >= session.steps.size) return
            val newSteps = ArrayList(session.steps)
            val step = newSteps.removeAt(fromIndex)
            newSteps.add(toIndex, step)
            // 重新编号
            newSteps.forEachIndexed { i, s ->
                when (s) {
                    is BuilderStep.Record -> newSteps[i] = s.copy(orderIndex = i)
                    is BuilderStep.Think -> newSteps[i] = s.copy(orderIndex = i)
                }
            }
            _currentSession.value = session.copy(steps = newSteps.toMutableList())
        }

        /**
         * 从 BUILDING 状态生成 SKILL.md。
         * 默认直接格式化（无 AI），录制数据本身就是完整的操作指令。
         */
        fun startSummarization(context: Context, configId: String?) {
            val session = _currentSession.value ?: return
            if (_recordingState.value != RecordingState.BUILDING) return
            if (session.steps.isEmpty()) return
            session.endTime = System.currentTimeMillis()
            selectedModelConfigId = configId

            // 直接格式化生成，不需要 AI 推理
            val summarizer = SkillSummarizer(context.applicationContext)
            val skillMd = summarizer.generateDirectSkillMd(session)
            session.generatedSkillMd = skillMd
            _currentSession.value = session.copy()
            _recordingState.value = RecordingState.REVIEW
            AppLogger.i(TAG, "直接生成 SKILL.md 完成")
        }

        /**
         * 使用 AI 重新生成/优化 SKILL.md（可选功能，用户主动触发）。
         */
        fun regenerateSummary(context: Context) {
            val session = _currentSession.value ?: return
            if (_recordingState.value != RecordingState.REVIEW) return
            _recordingState.value = RecordingState.SUMMARIZING
            summarizationJob?.cancel()
            summarizationJob = summarizationScope.launch {
                val summarizer = SkillSummarizer(context.applicationContext)
                val skillMd = summarizer.summarize(session, selectedModelConfigId)
                if (_recordingState.value == RecordingState.SUMMARIZING) {
                    session.generatedSkillMd = skillMd
                    _currentSession.value = session.copy()
                    _recordingState.value = RecordingState.REVIEW
                    AppLogger.i(TAG, "AI 优化 SKILL.md 完成")
                }
            }
        }

        /**
         * Reset state to IDLE.
         */
        fun resetToIdle() {
            _currentSession.value = null
            _stepFrameCount.value = 0
            _stepFrameBuffer.clear()
            _overlayManager?.remove()
            _recordingState.value = RecordingState.IDLE
        }

        /**
         * 取消 AI 优化，回退到直接格式化结果。
         */
        fun skipSummarization(context: Context) {
            if (_recordingState.value != RecordingState.SUMMARIZING) return
            summarizationJob?.cancel()
            summarizationJob = null
            val session = _currentSession.value ?: return
            val summarizer = SkillSummarizer(context.applicationContext)
            val skillMd = summarizer.generateDirectSkillMd(session)
            session.generatedSkillMd = skillMd
            _currentSession.value = session.copy()
            _recordingState.value = RecordingState.REVIEW
            AppLogger.i(TAG, "取消AI优化，使用直接格式化")
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var frameCapture: FrameCapture
    private var timerJob: Job? = null
    private var pollingJob: Job? = null
    private var startTimeMs = 0L
    private var pausedDurationMs = 0L
    private var pauseStartMs = 0L
    private var lastActivityName: String? = null
    private var lastUiHierarchyHash: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        frameCapture = FrameCapture(this)
        SkillRecorderNotification.createChannel(this)
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SkillRecorderNotification.ACTION_PAUSE -> pauseRecording()
            SkillRecorderNotification.ACTION_RESUME -> resumeRecording()
            SkillRecorderNotification.ACTION_STOP -> stopRecording()
            SkillRecorderNotification.ACTION_DISCARD -> discardRecording()
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_recordingState.value == RecordingState.STEP_RECORDING) return

        // 清空步骤帧缓冲
        _stepFrameBuffer.clear()
        _stepFrameCount.value = 0
        _recordingState.value = RecordingState.STEP_RECORDING
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        frameCapture.reset()

        // 启动前台通知
        startForeground(
            SkillRecorderNotification.NOTIFICATION_ID,
            SkillRecorderNotification.buildRecordingNotification(this, 0, 0, false)
        )

        // 显示悬浮录制控制球
        if (Settings.canDrawOverlays(this)) {
            if (_overlayManager == null) {
                _overlayManager = SkillRecorderOverlayManager(this.applicationContext)
            }
            _overlayManager?.showRecordingBall()
        }

        // 注册 ActionManager 事件回调
        val actionManager = ActionManager.getInstance(this)
        actionManager.registerEventCallback(CALLBACK_ID) { event ->
            onActionEvent(event)
        }

        // 启动 UI 轮询
        pollingJob = serviceScope.launch {
            lastActivityName = try {
                UIHierarchyManager.getCurrentActivityName(this@SkillRecorderService)
            } catch (_: Exception) { null }
            lastUiHierarchyHash = try {
                UIHierarchyManager.getUIHierarchy(this@SkillRecorderService)?.hashCode() ?: 0
            } catch (_: Exception) { 0 }

            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_recordingState.value != RecordingState.STEP_RECORDING) continue
                pollUiChanges()
            }
        }

        // 启动定时器更新通知
        startNotificationTimer()

        AppLogger.i(TAG, "步骤录制已开始（UI轮询模式）")
    }

    /**
     * Poll UIHierarchyManager for Activity / UI hierarchy changes.
     */
    private suspend fun pollUiChanges() {
        try {
            val ctx = this@SkillRecorderService

            val currentActivity = try {
                UIHierarchyManager.getCurrentActivityName(ctx)
            } catch (_: Exception) { null }

            val currentUiHash = try {
                UIHierarchyManager.getUIHierarchy(ctx)?.hashCode() ?: 0
            } catch (_: Exception) { 0 }

            val now = System.currentTimeMillis()

            // Activity switch → SCREEN_CHANGE
            if (currentActivity != null && currentActivity != lastActivityName) {
                val event = ActionListener.ActionEvent(
                    timestamp = now,
                    actionType = ActionListener.ActionType.SCREEN_CHANGE,
                    elementInfo = ActionListener.ElementInfo(
                        className = currentActivity,
                        packageName = currentActivity.substringBeforeLast(".", "")
                    )
                )
                lastActivityName = currentActivity
                lastUiHierarchyHash = currentUiHash
                onActionEvent(event)
                return
            }

            // Same activity but UI content changed → likely user interaction (click, scroll, etc.)
            // Generate a CLICK event so FrameCapture captures the UI hierarchy at this moment.
            // The AI summarizer will infer what was clicked from the before/after UI context.
            if (currentUiHash != 0 && currentUiHash != lastUiHierarchyHash) {
                val event = ActionListener.ActionEvent(
                    timestamp = now,
                    actionType = ActionListener.ActionType.CLICK,
                    elementInfo = ActionListener.ElementInfo(
                        packageName = currentActivity?.substringBeforeLast(".", "")
                    ),
                    additionalData = mapOf("source" to "ui_change_polling")
                )
                lastUiHierarchyHash = currentUiHash
                onActionEvent(event)
            }

            lastActivityName = currentActivity
        } catch (e: Exception) {
            AppLogger.w(TAG, "UI轮询失败: ${e.message}")
        }
    }

    private fun pauseRecording() {
        if (_recordingState.value != RecordingState.STEP_RECORDING) return
        _recordingState.value = RecordingState.STEP_PAUSED
        pauseStartMs = System.currentTimeMillis()
        updateNotification()
        AppLogger.i(TAG, "步骤录制已暂停")
    }

    private fun resumeRecording() {
        if (_recordingState.value != RecordingState.STEP_PAUSED) return
        pausedDurationMs += System.currentTimeMillis() - pauseStartMs
        _recordingState.value = RecordingState.STEP_RECORDING
        updateNotification()
        AppLogger.i(TAG, "步骤录制已恢复")
    }

    private fun stopRecording() {
        if (_recordingState.value != RecordingState.STEP_RECORDING &&
            _recordingState.value != RecordingState.STEP_PAUSED
        ) return

        // 先将状态改为非 STEP_RECORDING，防止 onActionEvent 再产生新帧
        _recordingState.value = RecordingState.STEP_PAUSED

        // 停止轮询和事件回调（不再产生新事件）
        pollingJob?.cancel()
        pollingJob = null
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()

        // 进入标注状态，等待用户输入描述后再提交
        _recordingState.value = RecordingState.STEP_LABELING

        // 切换悬浮窗为描述输入面板
        _overlayManager?.showLabelingPanel()

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "步骤录制完成，等待用户添加描述")
    }

    private fun discardRecording() {
        pollingJob?.cancel()
        pollingJob = null
        ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        timerJob?.cancel()

        // 清空帧缓冲但不丢弃 session
        _stepFrameBuffer.clear()
        _stepFrameCount.value = 0

        // 移除悬浮窗
        _overlayManager?.remove()

        _recordingState.value = RecordingState.BUILDING
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.i(TAG, "步骤录制已丢弃")
    }

    private fun onActionEvent(event: ActionListener.ActionEvent) {
        if (_recordingState.value != RecordingState.STEP_RECORDING) return

        serviceScope.launch {
            val frame = frameCapture.processEvent(event, _stepFrameBuffer)
            if (frame != null) {
                _stepFrameCount.value = _stepFrameBuffer.size
            }
        }
    }

    private fun startNotificationTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        val elapsed = getElapsedSeconds()
        val isPaused = _recordingState.value == RecordingState.STEP_PAUSED
        val notification = SkillRecorderNotification.buildRecordingNotification(
            this, _stepFrameCount.value, elapsed, isPaused
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(SkillRecorderNotification.NOTIFICATION_ID, notification)
    }

    private fun getElapsedSeconds(): Long {
        val now = System.currentTimeMillis()
        val totalPaused = pausedDurationMs +
            if (_recordingState.value == RecordingState.STEP_PAUSED) (now - pauseStartMs) else 0L
        return ((now - startTimeMs - totalPaused) / 1000).coerceAtLeast(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        pollingJob?.cancel()
        pollingJob = null
        try {
            ActionManager.getInstance(this).unregisterEventCallback(CALLBACK_ID)
        } catch (_: Exception) { }
        serviceScope.cancel()
        _isServiceRunning.value = false
        if (_recordingState.value != RecordingState.REVIEW &&
            _recordingState.value != RecordingState.BUILDING &&
            _recordingState.value != RecordingState.STEP_LABELING) {
            _recordingState.value = RecordingState.IDLE
        }
    }
}
