package com.ai.assistance.operit.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.hermes.gateway.AgentEventBus
import com.ai.assistance.operit.hermes.gateway.AgentTokenBus
import com.ai.assistance.operit.hermes.gateway.GatewayChatEventBus
import com.ai.assistance.operit.services.floating.AgentStatusOverlayManager
import com.ai.assistance.operit.ui.features.floating.agentstatus.AgentStatusUiState
import com.ai.assistance.operit.util.AppLogger
import com.xiaomo.hermes.hermes.AgentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 前台 Service：在 Hermes Gateway agent 运行期间显示系统级悬浮球。
 *
 * 生命周期由 [com.ai.assistance.operit.services.gateway.GatewayForegroundService]
 * 联动 —— gateway 启动时启动本 Service，gateway 停止时停止。
 *
 * 内部职责：
 * 1. 订阅 [GatewayChatEventBus] 维护 activeChats 表（chatId → 启动时刻 + 运行时统计）
 * 2. 订阅 [AgentEventBus] 拿 turn 数 / 当前工具名（fine-grained 状态）
 * 3. 订阅 [AgentTokenBus] 累计 input/output token
 * 4. 订阅 GATEWAY 槽位的 `inputProcessingStateByChatId` 作为 fallback 状态文字源
 * 5. 每 500ms 重新计算 UI 状态并推给 [AgentStatusOverlayManager]
 * 6. activeChats 为空时自动隐藏小球；新事件到来时再显示
 * 7. ProcessingFailed 时短暂保持小球可见 [ERROR_FLASH_MS] 毫秒变红，然后 hide
 *
 * 无悬浮窗权限 → onCreate 立即 stopSelf（仅打日志，不崩溃）。
 */
class AgentStatusOverlayService : Service(), ViewModelStoreOwner {

    private val TAG = "AgentStatusOverlay"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    private var overlayManager: AgentStatusOverlayManager? = null

    override val viewModelStore: ViewModelStore = ViewModelStore()
    override fun onBind(intent: Intent?): IBinder? = null

    /** chatId → 运行时统计。 */
    private data class ChatActiveInfo(
        val startedAtMs: Long,
        var turn: Int = 0,
        var lastToolName: String? = null,
        var tokensIn: Int = 0,
        var tokensOut: Int = 0,
    )
    private val activeChats = LinkedHashMap<String, ChatActiveInfo>()

    /** 失败闪烁标记 + 自动 hide 任务。 */
    private var errorFlash = false
    private var errorFlashJob: Job? = null

    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // 没有悬浮窗权限：仅日志，直接停服。让用户去设置页授权后下次 gateway 启动再生效。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)) {
            AppLogger.w(TAG, "No SYSTEM_ALERT_WINDOW permission — overlay disabled")
            // 必须先 startForeground 再 stopSelf 避免 startForegroundService 5s 超时崩溃
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification("无悬浮窗权限"))
            stopSelf()
            return
        }

        lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Agent 状态显示已就绪"))

        overlayManager = AgentStatusOverlayManager(
            context = this,
            viewModelStoreOwner = this,
            lifecycleOwner = lifecycleOwner,
            onCloseRequested = {
                // 用户点 X：清空 activeChats 并停服。下次 ProcessingStarted 事件由
                // GatewayForegroundService 的 startForegroundService 再次拉起本服务时重建。
                Handler(Looper.getMainLooper()).post { stopSelf() }
            },
        )

        observeGatewayLifecycle()
        observeAgentEvents()
        observeTokenUsage()
        startTicker()

        AppLogger.i(TAG, "AgentStatusOverlayService created")
        instance = this
        isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: 进程被杀后系统尝试重启（gateway 还在跑时仍能恢复悬浮球显示）
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "AgentStatusOverlayService destroyed")
        tickerJob?.cancel()
        errorFlashJob?.cancel()
        serviceScope.cancel()
        overlayManager?.hide()
        overlayManager = null
        if (::lifecycleOwner.isInitialized) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStore.clear()
        if (instance === this) instance = null
        isServiceRunning.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    // ---------------- Bus 订阅 ----------------

    /** Gateway lifecycle: 何时显示 / 隐藏小球。 */
    private fun observeGatewayLifecycle() {
        serviceScope.launch {
            // 用 collect 而不是 collectLatest：collectLatest 会取消上一个 emit 的处理，
            // 而我们这里每个 event 都需要原子地更新 activeChats 状态。
            GatewayChatEventBus.events.collect { event ->
                when (event) {
                    is GatewayChatEventBus.Event.ProcessingStarted -> {
                        activeChats[event.chatId] = ChatActiveInfo(System.currentTimeMillis())
                        showIfNeeded()
                        refreshUi()
                    }
                    is GatewayChatEventBus.Event.ProcessingCompleted -> {
                        activeChats.remove(event.chatId)
                        if (activeChats.isEmpty()) {
                            overlayManager?.hide()
                            updateNotification("Agent 已空闲")
                        } else {
                            refreshUi()
                        }
                    }
                    is GatewayChatEventBus.Event.ProcessingFailed -> {
                        activeChats.remove(event.chatId)
                        if (activeChats.isEmpty()) {
                            // 失败闪烁：变红保持 ERROR_FLASH_MS 后再 hide
                            triggerErrorFlash()
                        } else {
                            refreshUi()
                        }
                    }
                    is GatewayChatEventBus.Event.StreamingUpdate,
                    is GatewayChatEventBus.Event.UserMessagePersisted -> {
                        refreshUi()
                    }
                }
            }
        }
    }

    /** AgentEvent: 拿 turn / 当前工具名。 */
    private fun observeAgentEvents() {
        serviceScope.launch {
            AgentEventBus.events.collect { tagged ->
                val info = activeChats[tagged.chatId] ?: return@collect
                when (val event = tagged.event) {
                    is AgentEvent.Thinking -> {
                        info.turn = maxOf(info.turn, event.turn)
                    }
                    is AgentEvent.AssistantDelta -> {
                        info.turn = maxOf(info.turn, event.turn)
                    }
                    is AgentEvent.ToolCallStart -> {
                        info.turn = maxOf(info.turn, event.turn)
                        info.lastToolName = event.name
                    }
                    is AgentEvent.ToolCallEnd -> {
                        info.turn = maxOf(info.turn, event.turn)
                        // 工具结束后清掉名字，回到通用状态文字
                        if (info.lastToolName == event.name) {
                            info.lastToolName = null
                        }
                    }
                    is AgentEvent.Final -> {
                        info.turn = maxOf(info.turn, event.turnsUsed)
                        info.lastToolName = null
                    }
                    is AgentEvent.Error -> {
                        info.turn = maxOf(info.turn, event.turn)
                    }
                }
                refreshUi()
            }
        }
    }

    /** Token usage: 累加 onTurnComplete 的 input/output。 */
    private fun observeTokenUsage() {
        serviceScope.launch {
            AgentTokenBus.usage.collect { usage ->
                val info = activeChats[usage.chatId] ?: return@collect
                if (usage.turnComplete) {
                    // onTurnComplete: 累加这一轮的 token
                    info.tokensIn += usage.input
                    info.tokensOut += usage.output
                    refreshUi()
                }
                // onTokensUpdated 阶段（turnComplete=false）的值是本次请求的累计量，
                // 还在变化中；不直接累加避免重复。等 onTurnComplete 一次入账。
            }
        }
    }

    /** 每 500ms 触发一次 refreshUi，让"已运行 Xs"持续滚动。 */
    private fun startTicker() {
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                if (activeChats.isNotEmpty() || errorFlash) refreshUi()
            }
        }
    }

    private fun showIfNeeded() {
        // 启动新 chat 取消任何残留的红色闪烁
        errorFlashJob?.cancel()
        errorFlash = false
        overlayManager?.let { mgr ->
            if (!mgr.isShowing()) {
                mgr.show()
                updateNotification("Agent 运行中")
            }
        }
    }

    private fun triggerErrorFlash() {
        val mgr = overlayManager ?: return
        if (!mgr.isShowing()) return
        errorFlash = true
        refreshUi()
        errorFlashJob?.cancel()
        errorFlashJob = serviceScope.launch {
            delay(ERROR_FLASH_MS)
            errorFlash = false
            // 失败闪烁结束后，如仍无活跃 chat，则 hide
            if (activeChats.isEmpty()) {
                mgr.hide()
                updateNotification("Agent 已空闲")
            } else {
                refreshUi()
            }
        }
    }

    // ---------------- UI 计算 ----------------

    private fun refreshUi() {
        val mgr = overlayManager ?: return
        if (activeChats.isEmpty() && !errorFlash) return

        // 取最近一次启动的 chat 作为主显示项（LinkedHashMap 末尾就是最近插入的）
        val (chatId, info) = activeChats.entries.lastOrNull() ?: run {
            // 失败闪烁但 activeChats 空：显示通用错误文字
            mgr.setStatus(
                AgentStatusUiState(
                    platform = "",
                    chatIdShort = "",
                    statusText = "处理失败",
                    elapsedMs = 0L,
                    activeChatCount = 0,
                    errorFlash = true,
                ),
            )
            return
        }
        val now = System.currentTimeMillis()

        // 平台 / chatId 解析。historyChatId 形如 "gw:feishu:<chat>" 或 "gw:feishu:<chat>:<user>:..."
        val platform = parsePlatform(chatId)
        val shortChat = parseShortChatId(chatId)

        // 状态文字优先级：lastToolName > InputProcessingState > "运行中…"
        val statusText = if (info.lastToolName != null) {
            "调用工具: ${info.lastToolName}"
        } else {
            val stateMap = runCatching {
                ChatRuntimeHolder.getInstance(applicationContext)
                    .getCore(ChatRuntimeSlot.GATEWAY)
                    .inputProcessingStateByChatId
                    .value
            }.getOrDefault(emptyMap())
            mapStateToText(stateMap[chatId])
        }

        mgr.setStatus(
            AgentStatusUiState(
                platform = platform,
                chatIdShort = shortChat,
                statusText = statusText,
                elapsedMs = (now - info.startedAtMs).coerceAtLeast(0L),
                activeChatCount = activeChats.size,
                turn = info.turn,
                tokensIn = info.tokensIn,
                tokensOut = info.tokensOut,
                errorFlash = errorFlash,
            ),
        )
    }

    private fun parsePlatform(historyChatId: String): String {
        // "gw:feishu:xxx" → "feishu"
        val parts = historyChatId.split(":")
        return parts.getOrNull(1)?.ifEmpty { null } ?: "gateway"
    }

    private fun parseShortChatId(historyChatId: String): String {
        // 取 ":" 之后的部分，截前 12 个字符
        val tail = historyChatId.substringAfter(':', historyChatId)
            .substringAfter(':', historyChatId)
        val token = tail.take(12)
        return if (tail.length > 12) "$token…" else token
    }

    private fun mapStateToText(state: InputProcessingState?): String = when (state) {
        null -> "准备中…"
        is InputProcessingState.Idle -> "准备中…"
        is InputProcessingState.Connecting -> "连接中…"
        is InputProcessingState.Processing -> state.message.ifBlank { "处理中…" }
        is InputProcessingState.Receiving -> "等待响应…"
        is InputProcessingState.ExecutingTool -> "调用工具: ${state.toolName}"
        is InputProcessingState.ToolProgress -> "工具运行: ${state.toolName}"
        is InputProcessingState.ProcessingToolResult -> "处理结果: ${state.toolName}"
        is InputProcessingState.Summarizing -> "生成总结…"
        is InputProcessingState.ExecutingPlan -> "执行计划…"
        is InputProcessingState.Completed -> "已完成"
        is InputProcessingState.Error -> "错误: ${state.message}"
    }

    // ---------------- UI 工具临时隐藏支持 ----------------

    /**
     * 当前小球是否在屏幕上显示。仅"加进 WindowManager"算 true；activeChats 空 / errorFlash
     * 中的中间态都算 false（因为 manager.show() 才会让 composeView 非空）。
     *
     * 给 ToolRegistration.executeUiToolWithVisibility 用：UI 工具执行前先 setOverlayVisible(false)，
     * 完成后 setOverlayVisible(true) 复原，避免悬浮球挡住 agent 要点击的 UI。
     */
    val isOverlayShowing: Boolean
        get() = overlayManager?.isShowing() == true

    /**
     * 临时隐藏 / 复原悬浮球。注意：
     * - hide(false) 只是把 view 从 WindowManager 里 remove，不动 activeChats，所以下次自然刷新会再 show
     * - show(true) 只在还有活跃 chat 时才生效，避免在 agent 空闲时把已隐藏的小球错误恢复
     */
    fun setOverlayVisible(visible: Boolean) {
        val mgr = overlayManager ?: return
        Handler(Looper.getMainLooper()).post {
            if (visible) {
                if (activeChats.isNotEmpty() && !mgr.isShowing()) mgr.show()
            } else {
                if (mgr.isShowing()) mgr.hide()
            }
        }
    }

    // ---------------- Foreground Notification ----------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent 运行状态",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "显示 Hermes Gateway agent 运行时的悬浮球"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agent 状态")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun updateNotification(status: String) {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(NOTIFICATION_ID, buildNotification(status))
    }

    companion object {
        private const val CHANNEL_ID = "hermes_agent_status_overlay"
        private const val NOTIFICATION_ID = 71_643
        private const val TICK_INTERVAL_MS = 500L
        private const val ERROR_FLASH_MS = 2_500L

        val isServiceRunning = MutableStateFlow(false)

        @Volatile
        private var instance: AgentStatusOverlayService? = null

        /** 给 ToolRegistration 等需要"运行期同步访问"的调用方用。无服务运行时返回 null。 */
        fun getInstance(): AgentStatusOverlayService? = instance

        fun start(context: Context) {
            val intent = Intent(context, AgentStatusOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentStatusOverlayService::class.java))
        }
    }
}
