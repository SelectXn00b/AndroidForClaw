package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Gateway runner — orchestrates platform adapters, session management,
 * and message routing.
 *
 * Ported from gateway/run.py
 */

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.hermes.gateway.platforms.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gateway runner — the main entry point for the gateway.
 *
 * Manages the lifecycle of all platform adapters, the session store,
 * the delivery router, and the hook pipeline.
 */
class GatewayRunner(
    /** Application context. */
    private val context: Context,
    /** Gateway configuration. */
    private val config: GatewayConfig,
) {
    companion object {
        private const val TAG = "GatewayRunner"
    }

    /** Whether the gateway is running. */
    val isRunning = AtomicBoolean(false)

    /** Session store. */
    val sessionStore = SessionStore(
        persistDir = File(config.hermesHome, "sessions").takeIf { config.hermesHome.isNotEmpty() }
    )

    /** Delivery router. */
    val deliveryRouter = DeliveryRouter()

    /** Hook pipeline. */
    val hookPipeline = HookPipeline()

    /** Gateway status. */
    val status = GatewayStatus()

    /** Channel directory. */
    val channelDirectory = ChannelDirectory()

    /** Mirror bridge. */
    val mirrorBridge = MirrorBridge()

    /** Sticker cache. */
    val stickerCache = StickerCache(context)

    /** Display config registry. */
    val displayConfigRegistry = DisplayConfigRegistry()

    /** Pairing manager. */
    val pairingManager = PairingManager()

    /** All platform adapters (lazily created). */
    private val _adapters: ConcurrentHashMap<String, BasePlatformAdapter> = ConcurrentHashMap()

    /** Background scope for the gateway. */
    private val _scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Concurrent session limiter. */
    private val _sessionSemaphore = java.util.concurrent.Semaphore(config.maxConcurrentSessions)

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start the gateway.
     *
     * Connects all enabled platform adapters and starts processing
     * incoming messages.
     */
    suspend fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Gateway already running")
            return
        }

        Log.i(TAG, "Starting gateway...")

        // Load persisted sessions
        sessionStore.load()

        // Create and connect adapters
        for (platformConfig in config.enabledPlatforms) {
            try {
                val adapter = _createAdapter(platformConfig)
                if (adapter != null) {
                    _adapters[adapter.name] = adapter
                    deliveryRouter.register(adapter)
                    status.markConnected(adapter.name)
                    Log.i(TAG, "Platform ${adapter.name} connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect platform ${platformConfig.platform}: ${e.message}")
            }
        }

        // Run startup hooks
        _scope.launch {
            hookPipeline.run(HookEvent.ON_START)
        }

        Log.i(TAG, "Gateway started with ${_adapters.size} platform(s)")
    }

    /**
     * Stop the gateway.
     *
     * Disconnects all platform adapters and persists sessions.
     */
    suspend fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        Log.i(TAG, "Stopping gateway...")

        // Run shutdown hooks
        try {
            withTimeout(10_000) {
                hookPipeline.run(HookEvent.ON_STOP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shutdown hooks timed out: ${e.message}")
        }

        // Disconnect all adapters
        for ((name, adapter) in _adapters) {
            try {
                adapter.disconnect()
                deliveryRouter.unregister(name)
                status.markDisconnected(name)
                Log.i(TAG, "Platform $name disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting platform $name: ${e.message}")
            }
        }
        _adapters.clear()

        // Persist sessions
        sessionStore.persist()

        // Cancel background scope
        _scope.cancel()

        Log.i(TAG, "Gateway stopped")
    }

    /**
     * Restart the gateway.
     *
     * Stops and then starts the gateway.
     */
    suspend fun restart() {
        stop()
        delay(1000)
        start()
    }

    // ------------------------------------------------------------------
    // Adapter management
    // ------------------------------------------------------------------

    /**
     * Create and connect an adapter for the given platform config.
     */
    private suspend fun _createAdapter(platformConfig: PlatformConfig): BasePlatformAdapter? {
        val adapter = when (platformConfig.platform) {
            Platform.FEISHU -> FeishuAdapter(context, platformConfig)
            Platform.TELEGRAM -> TelegramAdapter(context, platformConfig)
            Platform.DISCORD -> DiscordAdapter(context, platformConfig)
            Platform.SLACK -> SlackAdapter(context, platformConfig)
            Platform.SIGNAL -> SignalAdapter(context, platformConfig)
            Platform.WHATSAPP -> WhatsAppAdapter(context, platformConfig)
            Platform.WECOM -> WeComAdapter(context, platformConfig)
            Platform.WECOM_CALLBACK -> WeComCallbackAdapter(context, platformConfig)
            Platform.WEIXIN -> WeixinAdapter(context, platformConfig)
            Platform.DINGTALK -> DingtalkAdapter(context, platformConfig)
            Platform.QQBOT -> QqbotAdapter(context, platformConfig)
            Platform.EMAIL -> EmailAdapter(context, platformConfig)
            Platform.SMS -> SmsAdapter(context, platformConfig)
            Platform.MATRIX -> MatrixAdapter(context, platformConfig)
            Platform.MATTERMOST -> MattermostAdapter(context, platformConfig)
            Platform.HOMEASSISTANT -> HomeassistantAdapter(context, platformConfig)
            Platform.WEBHOOK -> WebhookAdapter(context, platformConfig)
            Platform.API_SERVER -> ApiServerAdapter(context, platformConfig)
            Platform.BLUEBUBBLES -> BluebubblesAdapter(context, platformConfig)
            else -> {
                Log.w(TAG, "Unknown platform: ${platformConfig.platform}")
                return null
            }
        }

        // Set up message handler
        adapter.messageHandler = { event -> _handleMessage(event, adapter) }

        // Connect
        val connected = adapter.connect()
        if (!connected) {
            Log.w(TAG, "Failed to connect platform ${adapter.name}")
            return null
        }

        return adapter
    }

    /**
     * Handle an incoming message from a platform adapter.
     */
    private suspend fun _handleMessage(event: MessageEvent, adapter: BasePlatformAdapter) {
        // Acquire session semaphore
        if (!_sessionSemaphore.tryAcquire()) {
            Log.w(TAG, "Max concurrent sessions reached, dropping message")
            return
        }

        try {
            // Get or create session
            val session = sessionStore.getOrCreate(
                sessionKey = event.sessionKey,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
                chatName = event.source.chatName,
                userName = event.source.userName,
                chatType = event.source.chatType,
            )

            // Record message
            session.recordMessage()
            session.markProcessing()
            status.processingSessions++
            status.countersFor(adapter.name).recordReceived()

            // Run pre-validate hooks
            val preValidateResult = hookPipeline.run(
                HookEvent.PRE_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )
            if (preValidateResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by pre-validate hook: ${preValidateResult.reason}")
                return
            }

            // Run post-validate hooks
            val postValidateResult = hookPipeline.run(
                HookEvent.POST_VALIDATE,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )
            if (postValidateResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by post-validate hook: ${postValidateResult.reason}")
                return
            }

            // Run pre-agent hooks
            val preAgentResult = hookPipeline.run(
                HookEvent.PRE_AGENT,
                sessionKey = session.sessionKey,
                text = event.text,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )
            if (preAgentResult is HookResult.Halt) {
                Log.i(TAG, "Message halted by pre-agent hook: ${preAgentResult.reason}")
                return
            }

            // TODO: Invoke the agent loop here
            // For now, echo back the message as a placeholder
            val responseText = "Echo: ${event.text}"

            // Run post-agent hooks
            val postAgentResult = hookPipeline.run(
                HookEvent.POST_AGENT,
                sessionKey = session.sessionKey,
                text = responseText,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )
            val finalResponse = when (postAgentResult) {
                is HookResult.Replace -> postAgentResult.newText
                is HookResult.Halt -> {
                    Log.i(TAG, "Any? halted by post-agent hook: ${postAgentResult.reason}")
                    return
                }
                else -> responseText
            }

            // Run pre-send hooks
            val preSendResult = hookPipeline.run(
                HookEvent.PRE_SEND,
                sessionKey = session.sessionKey,
                text = finalResponse,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )
            val sendText = when (preSendResult) {
                is HookResult.Replace -> preSendResult.newText
                is HookResult.Halt -> {
                    Log.i(TAG, "Send halted by pre-send hook: ${preSendResult.reason}")
                    return
                }
                else -> finalResponse
            }

            // Send response
            val result = deliveryRouter.deliverText(
                platform = adapter.name,
                chatId = event.source.chatId,
                text = sendText,
                replyTo = event.message_id,
            )

            // Record send
            if (result.success) {
                status.countersFor(adapter.name).recordSent()
            } else {
                status.countersFor(adapter.name).recordError()
                Log.w(TAG, "Failed to send response: ${result.error}")
            }

            // Run post-send hooks
            hookPipeline.run(
                HookEvent.POST_SEND,
                sessionKey = session.sessionKey,
                text = sendText,
                platform = adapter.name,
                chatId = event.source.chatId,
                userId = event.source.userId,
            )

            // Record turn
            session.recordTurn()

            // Mirror if configured
            val mirrorRule = mirrorBridge.getRule(session.sessionKey)
            if (mirrorRule != null) {
                _scope.launch {
                    mirrorBridge.mirror(session.sessionKey, sendText, event.source.userId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        } finally {
            sessionStore.get(event.sessionKey)?.markIdle()
            status.processingSessions--
            _sessionSemaphore.release()
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Get an adapter by name.
     */
    fun getAdapter(name: String): BasePlatformAdapter? = _adapters[name]

    /**
     * Get all connected adapters.
     */
    fun getAdapters(): Map<String, BasePlatformAdapter> = _adapters.toMap()

    /**
     * Get the number of active sessions.
     */
    val activeSessionCount: Int get() = sessionStore.size

    /**
     * Get the number of processing sessions.
     */
    val processingSessionCount: Int get() = sessionStore.processingCount

    /**
     * Build a human-readable status string.
     */
    fun formatStatus(): String = buildString {
        appendLine("Gateway Status")
        appendLine("  Running: ${isRunning.get()}")
        appendLine("  Uptime: ${GatewayStatus.formatDuration(status.uptimeSeconds)}")
        appendLine("  Connected platforms: ${_adapters.keys.joinToString(", ").ifEmpty { "none" }}")
        appendLine("  Active sessions: ${sessionStore.size}")
        appendLine("  Processing sessions: ${sessionStore.processingCount}")
        if (status.platformCounters.isNotEmpty()) {
            appendLine("  Platform counters:")
            status.platformCounters.forEach { (name, c) ->
                appendLine("    $name: recv=${c.messagesReceived.get()} sent=${c.messagesSent.get()} errors=${c.sendErrors.get()}")
            }
        }
    }

    /**
     * Get the gateway status as JSON.
     */
    fun statusJson(): JSONObject = status.toJson()

    /**
     * Send a system notification to the home channel of all connected platforms.
     */
    suspend fun broadcastNotification(text: String) {
        deliveryRouter.broadcast(text)
    }

    /**
     * Send a message to a specific session.
     */
    suspend fun sendMessage(sessionKey: String, text: String): SendResult {
        val session = sessionStore.get(sessionKey)
            ?: return SendResult(success = false, error = "Session not found: $sessionKey")

        val result = deliveryRouter.deliverText(
            platform = session.platform,
            chatId = session.chatId,
            text = text,
        )
        return SendResult(success = result.success, messageId = result.messageId, error = result.error)
    }

    /**
     * Create a new session manually.
     */
    fun createSession(
        platform: String,
        chatId: String,
        userId: String,
        chatName: String = "",
        userName: String = "",
        chatType: String = "dm",
    ): SessionContext {
        val sessionKey = buildSessionKey(platform, chatId, userId)
        return sessionStore.getOrCreate(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    /**
     * Remove a session.
     */
    fun removeSession(sessionKey: String) {
        sessionStore.remove(sessionKey)
    }

    /**
     * Get all active sessions.
     */
    fun getSessions(): Collection<SessionContext> = sessionStore.all

    /**
     * Register a mirror rule.
     */
    fun addMirrorRule(sourceKey: String, targetUrl: String, targetKey: String, label: String = "") {
        mirrorBridge.addRule(sourceKey, MirrorRule(targetUrl, targetKey, label))
    }

    /**
     * Remove a mirror rule.
     */
    fun removeMirrorRule(sourceKey: String) {
        mirrorBridge.removeRule(sourceKey)
    }

    // ── Exit handling (ported from gateway/run.py) ──────────────────

    @Volatile private var _exitReason: String? = null
    @Volatile private var _exitCode: Int? = null

    /** Whether the gateway should exit cleanly. */
    fun shouldExitCleanly(): Boolean = _exitCode == 0

    /** Whether the gateway should exit with failure. */
    fun shouldExitWithFailure(): Boolean = _exitCode != null && _exitCode != 0

    /** The reason for exit. */
    fun exitReason(): String? = _exitReason

    /** The exit code. */
    fun exitCode(): Int? = _exitCode

    /** Request a clean exit. */
    fun requestCleanExit(reason: String) {
        _exitReason = reason
        _exitCode = 0
        Log.i(TAG, "Clean exit requested: $reason")
    }

    /** Number of currently running agents. */
    fun runningAgentCount(): Int = sessionStore.processingCount

    /** Status action label for display. */
    fun statusActionLabel(): String = when {
        sessionStore.processingCount > 0 -> "processing"
        else -> "idle"
    }

    /** Status action in gerund form. */
    fun statusActionGerund(): String = when {
        sessionStore.processingCount > 0 -> "Processing messages"
        else -> "Waiting for messages"
    }

    /** Whether queuing is enabled during drain. */
    fun queueDuringDrainEnabled(): Boolean = false

    // ── Voice mode (ported from gateway/run.py) ─────────────────────

    private val voiceModes = mutableMapOf<String, String>()

    /** Check if setup skill is available. */
    fun hasSetupSkill(): Boolean = false

    /** Load voice modes from config. */
    fun loadVoiceModes(): Map<String, String> = voiceModes.toMap()

    /** Save voice modes. */
    fun saveVoiceModes() {
        // Persist to config if needed
    }

    /** Set adapter auto-TTS disabled. */
    fun setAdapterAutoTtsDisabled(adapter: BasePlatformAdapter, chatId: String, disabled: Boolean) {
        Log.d(TAG, "setAutoTtsDisabled=$disabled for chat=$chatId on ${adapter.name}")
    }

    /** Sync voice mode state to adapter. */
    fun syncVoiceModeStateToAdapter(adapter: BasePlatformAdapter) {
        Log.d(TAG, "Syncing voice mode state to ${adapter.name}")
    }

    // ── Session helpers (ported from gateway/run.py) ────────────────

    /** Get session key for a source. */
    
    /** Build a session key from a MessageSource. */
    fun sessionKeyForSource(source: MessageSource): String {
        return buildSessionKey(source.platform, source.chatId, source.userId)
    }

    fun sessionKeyForSource(source: Map<String, Any?>): String {
        val platform = source["platform"] as? String ?: "unknown"
        val chatId = source["chat_id"] as? String ?: ""
        val userId = source["user_id"] as? String ?: ""
        return buildSessionKey(platform, chatId, userId)
    }

    /** Resolve session agent runtime config. */
    fun resolveSessionAgentRuntime(sessionKey: String, model: String): Map<String, Any?> {
        val session = sessionStore.get(sessionKey)
        return mapOf<String, Any?>(
            "model" to (session?.modelOverride ?: model),
            "session_key" to sessionKey,
        )
    }

    /** Resolve turn agent config. */
    fun resolveTurnAgentConfig(userMessage: String, model: String, runtimeKwargs: Map<String, Any?>): Map<String, Any?> {
        return mapOf<String, Any?>(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to userMessage)),
        ) + runtimeKwargs
    }

    // ── Runtime status ──────────────────────────────────────────────

    /** Update runtime status. */
    fun updateRuntimeStatus(gatewayState: String? = null, exitReason: String? = null) {
        gatewayState?.let { Log.i(TAG, "Gateway state: $it") }
        exitReason?.let { _exitReason = it }
    }

    /** Update platform runtime status. */
    fun updatePlatformRuntimeStatus(adapterName: String, connected: Boolean, error: String? = null) {
        Log.i(TAG, "Platform $adapterName: connected=$connected error=$error")
    }

    /** Flush memories for a session. */
    fun flushMemoriesForSession(sessionKey: String, sessionId: String) {
        Log.d(TAG, "Flushing memories for session $sessionKey")
    }

    // ── Config loading ──────────────────────────────────────────────

    /** Load reasoning config. */
    fun loadReasoningConfig(): Map<String, Any?> {
        return mapOf("enabled" to false, "budget" to 0, "effort" to "auto")
    }

    /** Load fallback model. */
    fun loadFallbackModel(): String? {
        return config.defaultModel.ifEmpty { null }
    }

    /** Load service tier. */
    fun loadServiceTier(): String = "default"


    /** Load show reasoning. */
    fun loadShowReasoning(): Boolean = config.verbose


    /** Load busy input mode. */
    fun loadBusyInputMode(): String = "queue"


    /** Load provider routing config. */
    fun loadProviderRouting(): Map<String, Any?> {
        return mapOf("provider" to config.provider).filterValues { it.isNotEmpty() }
    }

    /** Load smart model routing. */
    fun loadSmartModelRouting(): Map<String, Any?> = emptyMap()


    /** Load restart drain timeout. */
    fun loadRestartDrainTimeout(): Double = config.restartDrainTimeoutSeconds


    // ── Session formatting ──────────────────────────────────────────

    /** Format session info for display. */
    fun formatSessionInfo(): String = buildString {
        val keys = sessionStore.getSessionKeys()
        appendLine("Active sessions: ${keys.size}")
        for (key in keys) {
            val session = sessionStore.get(key) ?: continue
            appendLine("  • $key")
            appendLine("    platform: ${session.platform}, chat: ${session.chatId}")
        }
        if (keys.isEmpty()) {
            appendLine("  (none)")
        }
    }

    /** Get unauthorized DM behavior for a platform. */
    fun getUnauthorizedDmBehavior(platform: String): String = "pair"


    /** Check if a user is authorized for the given source. */
    fun isUserAuthorized(source: MessageSource): Boolean {
        val chatId = source.chatId
        val userId = source.userId
        // Simplified: check if user is in approved users list
        return _approvedUsers.contains(userId) || _approvedUsers.contains(chatId)
    }

    /** Approved users set. */
    private val _approvedUsers = mutableSetOf<String>()

    /** Add an approved user. */
    fun addApprovedUser(userId: String) { _approvedUsers.add(userId) }

    /** Remove an approved user. */
    fun removeApprovedUser(userId: String) { _approvedUsers.remove(userId) }

    /** Resolve the gateway model for a given context. */
    fun resolveGatewayModel(): String {
        return config.defaultModel.ifEmpty {
            System.getenv("HERMES_MODEL") ?: "default"
        }
    }

    // ── Command handlers (simplified for Android) ───────────────────


    /** Convert a MessageEvent to a source map for session key lookup. */
    private fun messageEventToSource(event: MessageEvent): Map<String, Any?> = mapOf(
        "platform" to event.source.platform,
        "chat_id" to event.source.chatId,
        "user_id" to event.source.userId,
        "chat_name" to event.source.chatName,
        "user_name" to event.source.userName,
        "chat_type" to event.source.chatType,
    )

    /** Handle /reset command. */
    suspend fun handleResetCommand(event: MessageEvent) {
        val source = messageEventToSource(event)
        val sessionKey = sessionKeyForSource(event.source)
        val session = sessionStore.get(sessionKey)
        if (session != null) {
            sessionStore.remove(sessionKey)
            _adapters[event.source.platform]?.send(
                event.source.chatId, "🔄 Session reset.", replyTo = event.message_id
            )
        } else {
            _adapters[event.source.platform]?.send(
                event.source.chatId, "No active session to reset.", replyTo = event.message_id
            )
        }
    }

    /** Handle /status command. */
    suspend fun handleStatusCommand(event: MessageEvent) {
        val statusText = formatStatus()
        _adapters[event.source.platform]?.send(event.source.chatId, statusText, replyTo = event.message_id)
    }

    /** Handle /help command. */
    suspend fun handleHelpCommand(event: MessageEvent) {
        val help = buildString {
            appendLine("Available commands:")
            appendLine("/new - Start new session")
            appendLine("/reset - Reset current session")
            appendLine("/status - Show gateway status")
            appendLine("/sessions - List active sessions")
            appendLine("/model [name] - Show/set current model")
            appendLine("/help - Show this help")
        }
        _adapters[event.source.platform]?.send(event.source.chatId, help, replyTo = event.message_id)
    }

    /** Handle /stop command. */
    suspend fun handleStopCommand(event: MessageEvent) {
        _interruptRunningAgents("User requested stop")
        _adapters[event.source.platform]?.send(
            event.source.chatId, "⏹ Stopped.", replyTo = event.message_id
        )
    }

    /** Interrupt running agents. */
    fun _interruptRunningAgents(reason: String) {
        sessionStore.clear()
        Log.i(TAG, "Interrupted running agents: $reason")
    }

    /** Handle /model command. */
    suspend fun handleModelCommand(event: MessageEvent, args: String) {
        if (args.isBlank()) {
            val model = resolveGatewayModel()
            _adapters[event.source.platform]?.send(
                event.source.chatId, "Current model: $model", replyTo = event.message_id
            )
        } else {
            _adapters[event.source.platform]?.send(
                event.source.chatId, "Model override: $args (per-session)", replyTo = event.message_id
            )
        }
    }

    /** Handle /sessions command. */
    suspend fun handleSessionsCommand(event: MessageEvent) {
        val info = formatSessionInfo()
        _adapters[event.source.platform]?.send(event.source.chatId, info, replyTo = event.message_id)
    }

    // ── Background tasks ────────────────────────────────────────────

    /** Run a background task. */
    fun runBackgroundTask(prompt: String, source: Map<String, Any?>, taskId: String) {
        Log.d(TAG, "Background task $taskId: ${prompt.take(50)}")
    }

    /** Monitor for interrupt on a session. */
    fun monitorForInterrupt(sessionKey: String, timeoutMs: Long): Boolean {
        // Simplified: check if session has been cleared
        return sessionStore.get(sessionKey) == null
    }

    /** Track an active agent. */
    fun trackAgent(sessionKey: String) {
        Log.d(TAG, "Tracking agent: $sessionKey")
    }

    /** Send progress messages. */
    suspend fun sendProgressMessages(sessionKey: String, messages: List<String>) {
        val session = sessionStore.get(sessionKey) ?: return
        for (msg in messages) {
            _adapters[session.platform]?.send(session.chatId, msg)
        }
    }

    // ── Gateway lifecycle ───────────────────────────────────────────

    /** Resolve prompt for the session. */
    fun resolvePrompt(source: Map<String, Any?>): String = "You are a helpful assistant."

    /** Cleanup resources. */
    suspend fun cleanup() {
        Log.d(TAG, "Cleaning up gateway resources")
        for (adapter in _adapters.values) {
            try { adapter.disconnect() } catch (_unused: Exception) {}
        }
        _adapters.clear()
    }

    /** Get guild ID from source. */
    fun getGuildId(source: MessageSource): String? {
        return source.chatId
    }

    /** Strip ANSI codes from text. */
    fun stripAnsi(text: String): String {
        return text.replace(Regex("\\x1B\\[[0-9;]*[a-zA-Z]"), "")
    }

    /** Format gateway process notification. */
    fun formatGatewayProcessNotification(state: String, message: String = ""): String {
        return when (state) {
            "starting" -> "🚀 Gateway starting..."
            "running" -> "✅ Gateway running${if (message.isNotEmpty()) ": $message" else ""}"
            "stopping" -> "🛑 Gateway stopping..."
            "error" -> "❌ Gateway error: $message"
            else -> "Gateway: $state"
        }
    }

    /** Resolve the hermes binary path. */
    fun resolveHermesBin(): String {
        return System.getenv("HERMES_BIN") ?: "hermes"
    }

    /** Update notification watch. */
    fun scheduleUpdateNotificationWatch() {
        Log.d(TAG, "Scheduled update notification watch")
    }

    /** Agent config signature for caching. */
    fun agentConfigSignature(model: String, runtimeKwargs: Map<String, Any?>): String {
        return "$model:${runtimeKwargs.hashCode()}"
    }

    /** Evict a cached agent. */
    fun evictCachedAgent(sessionKey: String) {
        Log.d(TAG, "Evicted cached agent: $sessionKey")
    }

    /** Check if the hermes-agent-setup skill is installed. */
    fun _hasSetupSkill(): Boolean {
        return false
    }
    fun _loadVoiceModes(): Map<String, String> {
        return emptyMap()
    }
    fun _saveVoiceModes(): Unit {
        // TODO: implement _saveVoiceModes
    }
    /** Update an adapter's in-memory auto-TTS suppression set if present. */
    fun _setAdapterAutoTtsDisabled(adapter: Any?, chatId: String, disabled: Boolean): Unit {
        // TODO: implement _setAdapterAutoTtsDisabled
    }
    /** Restore persisted /voice off state into a live platform adapter. */
    fun _syncVoiceModeStateToAdapter(adapter: Any?): Unit {
        // TODO: implement _syncVoiceModeStateToAdapter
    }
    /** Prompt the agent to save memories/skills before context is lost. */
    fun _flushMemoriesForSession(oldSessionId: String, sessionKey: String? = null): Any? {
        return null
    }
    /** Run the sync memory flush in a thread pool so it won't block the event loop. */
    suspend fun _asyncFlushMemories(oldSessionId: String, sessionKey: String? = null): Any? {
        return null
    }
    /** Resolve the current session key for a source, honoring gateway config when available. */
    fun _sessionKeyForSource(source: SessionSource): String {
        return ""
    }
    /** Resolve model/runtime for a session, honoring session-scoped /model overrides. */
    fun _resolveSessionAgentRuntime(): Pair<String, Any?> {
        throw NotImplementedError("_resolveSessionAgentRuntime")
    }
    fun _resolveTurnAgentConfig(userMessage: String, model: String, runtimeKwargs: Any?): Any? {
        return null
    }
    /** React to an adapter failure after startup. */
    suspend fun _handleAdapterFatalError(adapter: BasePlatformAdapter): Unit {
        // TODO: implement _handleAdapterFatalError
    }
    fun _requestCleanExit(reason: String): Unit {
        // TODO: implement _requestCleanExit
    }
    fun _runningAgentCount(): Int {
        return 0
    }
    fun _statusActionLabel(): String {
        return ""
    }
    fun _statusActionGerund(): String {
        return ""
    }
    fun _queueDuringDrainEnabled(): Boolean {
        return false
    }
    fun _updateRuntimeStatus(gatewayState: String? = null, exitReason: String? = null): Unit {
        // TODO: implement _updateRuntimeStatus
    }
    fun _updatePlatformRuntimeStatus(platform: String): Unit {
        // TODO: implement _updatePlatformRuntimeStatus
    }
    /** Load ephemeral prefill messages from config or env var. */
    fun _loadPrefillMessages(): List<Map<String, Any>> {
        return emptyList()
    }
    /** Load ephemeral system prompt from config or env var. */
    fun _loadEphemeralSystemPrompt(): String {
        return ""
    }
    /** Load reasoning effort from config.yaml. */
    fun _loadReasoningConfig(): Any? {
        return null
    }
    /** Load Priority Processing setting from config.yaml. */
    fun _loadServiceTier(): Any? {
        return null
    }
    /** Load show_reasoning toggle from config.yaml display section. */
    fun _loadShowReasoning(): Boolean {
        return false
    }
    /** Load gateway drain-time busy-input behavior from config/env. */
    fun _loadBusyInputMode(): String {
        return ""
    }
    /** Load graceful gateway restart/stop drain timeout in seconds. */
    fun _loadRestartDrainTimeout(): Double {
        return 0.0
    }
    /** Load background process notification mode from config or env var. */
    fun _loadBackgroundNotificationsMode(): String {
        return ""
    }
    /** Load OpenRouter provider routing preferences from config.yaml. */
    fun _loadProviderRouting(): Any? {
        return null
    }
    /** Load fallback provider chain from config.yaml. */
    fun _loadFallbackModel(): Any? {
        return null
    }
    /** Load optional smart cheap-vs-strong model routing config. */
    fun _loadSmartModelRouting(): Any? {
        return null
    }
    fun _snapshotRunningAgents(): Map<String, Any> {
        return emptyMap()
    }
    fun _queueOrReplacePendingEvent(sessionKey: String, event: MessageEvent): Unit {
        // TODO: implement _queueOrReplacePendingEvent
    }
    suspend fun _handleActiveSessionBusyMessage(event: MessageEvent, sessionKey: String): Boolean {
        return false
    }
    suspend fun _drainActiveAgents(timeout: Double): Pair<Map<String, Any>, Boolean> {
        throw NotImplementedError("_drainActiveAgents")
    }
    fun _finalizeShutdownAgents(activeAgents: Map<String, Any>): Unit {
        // TODO: implement _finalizeShutdownAgents
    }
    suspend fun _launchDetachedRestartCommand(): Unit {
        // TODO: implement _launchDetachedRestartCommand
    }
    fun requestRestart(): Boolean {
        return false
    }
    /** Background task that proactively flushes memories for expired sessions. */
    suspend fun _sessionExpiryWatcher(interval: Int = 300): Any? {
        return null
    }
    /** Background task that periodically retries connecting failed platforms. */
    suspend fun _platformReconnectWatcher(): Unit {
        // TODO: implement _platformReconnectWatcher
    }
    /** Wait for shutdown signal. */
    suspend fun waitForShutdown(): Unit {
        // TODO: implement waitForShutdown
    }
    /** Check if a user is authorized to use the bot. */
    fun _isUserAuthorized(source: SessionSource): Boolean {
        return false
    }
    /** Return how unauthorized DMs should be handled for a platform. */
    fun _getUnauthorizedDmBehavior(platform: Platform?): String {
        return ""
    }
    /** Prepare inbound event text for the agent. */
    suspend fun _prepareInboundMessageText(): String? {
        return null
    }
    /** Inner handler that runs under the _running_agents sentinel guard. */
    suspend fun _handleMessageWithAgent(event: Any?, source: Any?, _quickKey: String): Any? {
        return null
    }
    /** Resolve current model config and return a formatted info block. */
    fun _formatSessionInfo(): String {
        return ""
    }
    /** Handle /new or /reset command. */
    suspend fun _handleResetCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /profile — show active profile name and home directory. */
    suspend fun _handleProfileCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /status command. */
    suspend fun _handleStatusCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /stop command - interrupt a running agent. */
    suspend fun _handleStopCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /restart command - drain active work, then restart the gateway. */
    suspend fun _handleRestartCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /help command - list available commands. */
    suspend fun _handleHelpCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /commands [page] - paginated list of all commands and skills. */
    suspend fun _handleCommandsCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /model command — switch model for this session. */
    suspend fun _handleModelCommand(event: MessageEvent): String? {
        return null
    }
    /** Handle /provider command - show available providers. */
    suspend fun _handleProviderCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /personality command - list or set a personality. */
    suspend fun _handlePersonalityCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /retry command - re-send the last user message. */
    suspend fun _handleRetryCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /undo command - remove the last user/assistant exchange. */
    suspend fun _handleUndoCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /sethome command -- set the current chat as the platform's home channel. */
    suspend fun _handleSetHomeCommand(event: MessageEvent): String {
        return ""
    }
    /** Extract Discord guild_id from the raw message object. */
    fun _getGuildId(event: MessageEvent): Int? {
        return null
    }
    /** Handle /voice [on|off|tts|channel|leave|status] command. */
    suspend fun _handleVoiceCommand(event: MessageEvent): String {
        return ""
    }
    /** Join the user's current Discord voice channel. */
    suspend fun _handleVoiceChannelJoin(event: MessageEvent): String {
        return ""
    }
    /** Leave the Discord voice channel. */
    suspend fun _handleVoiceChannelLeave(event: MessageEvent): String {
        return ""
    }
    /** Called by the adapter when a voice channel times out. */
    fun _handleVoiceTimeoutCleanup(chatId: String): Unit {
        // TODO: implement _handleVoiceTimeoutCleanup
    }
    /** Handle transcribed voice from a user in a voice channel. */
    suspend fun _handleVoiceChannelInput(guildId: Int, userId: Int, transcript: String): Any? {
        return null
    }
    /** Decide whether the runner should send a TTS voice reply. */
    fun _shouldSendVoiceReply(event: MessageEvent, response: String, agentMessages: Any?, alreadySent: Boolean = false): Boolean {
        return false
    }
    /** Generate TTS audio and send as a voice message before the text reply. */
    suspend fun _sendVoiceReply(event: MessageEvent, text: String): Unit {
        // TODO: implement _sendVoiceReply
    }
    /** Extract MEDIA: tags and local file paths from a response and deliver them. */
    suspend fun _deliverMediaFromResponse(response: String, event: MessageEvent, adapter: Any?): Unit {
        // TODO: implement _deliverMediaFromResponse
    }
    /** Handle /rollback command — list or restore filesystem checkpoints. */
    suspend fun _handleRollbackCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /background <prompt> — run a prompt in a separate background session. */
    suspend fun _handleBackgroundCommand(event: MessageEvent): String {
        return ""
    }
    /** Execute a background agent task and deliver the result to the chat. */
    suspend fun _runBackgroundTask(prompt: String, source: Any?, taskId: String): Unit {
        // TODO: implement _runBackgroundTask
    }
    /** Handle /btw <question> — ephemeral side question in the same chat. */
    suspend fun _handleBtwCommand(event: MessageEvent): String {
        return ""
    }
    /** Execute an ephemeral /btw side question and deliver the answer. */
    suspend fun _runBtwTask(question: String, source: Any?, sessionKey: String, taskId: String): Unit {
        // TODO: implement _runBtwTask
    }
    /** Handle /reasoning command — manage reasoning effort and display toggle. */
    suspend fun _handleReasoningCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /fast — mirror the CLI Priority Processing toggle in gateway chats. */
    suspend fun _handleFastCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /yolo — toggle dangerous command approval bypass for this session only. */
    suspend fun _handleYoloCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /verbose command — cycle tool progress display mode. */
    suspend fun _handleVerboseCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /compress command -- manually compress conversation context. */
    suspend fun _handleCompressCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /title command — set or show the current session's title. */
    suspend fun _handleTitleCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /resume command — switch to a previously-named session. */
    suspend fun _handleResumeCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /branch [name] — fork the current session into a new independent copy. */
    suspend fun _handleBranchCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /usage command -- show token usage for the current session. */
    suspend fun _handleUsageCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /insights command -- show usage insights and analytics. */
    suspend fun _handleInsightsCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /reload-mcp command -- disconnect and reconnect all MCP servers. */
    suspend fun _handleReloadMcpCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /approve command — unblock waiting agent thread(s). */
    suspend fun _handleApproveCommand(event: MessageEvent): String? {
        return null
    }
    /** Handle /deny command — reject pending dangerous command(s). */
    suspend fun _handleDenyCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /debug — upload debug report + logs and return paste URLs. */
    suspend fun _handleDebugCommand(event: MessageEvent): String {
        return ""
    }
    /** Handle /update command — update Hermes Agent to the latest version. */
    suspend fun _handleUpdateCommand(event: MessageEvent): String {
        return ""
    }
    /** Ensure a background task is watching for update completion. */
    fun _scheduleUpdateNotificationWatch(): Unit {
        // TODO: implement _scheduleUpdateNotificationWatch
    }
    /** Watch ``hermes update --gateway``, streaming output + forwarding prompts. */
    suspend fun _watchUpdateProgress(pollInterval: Double, streamInterval: Double, timeout: Double): Unit {
        // TODO: implement _watchUpdateProgress
    }
    /** If an update finished, notify the user. */
    suspend fun _sendUpdateNotification(): Boolean {
        return false
    }
    /** Notify the chat that initiated /restart that the gateway is back. */
    suspend fun _sendRestartNotification(): Unit {
        // TODO: implement _sendRestartNotification
    }
    /** Set session context variables for the current async task. */
    fun _setSessionEnv(context: SessionContext): Any? {
        return null
    }
    /** Restore session context variables to their pre-handler values. */
    fun _clearSessionEnv(tokens: Any?): Unit {
        // TODO: implement _clearSessionEnv
    }
    /** Auto-analyze user-attached images with the vision tool and prepend */
    suspend fun _enrichMessageWithVision(userText: String, imagePaths: List<String>): String {
        return ""
    }
    /** Auto-transcribe user voice/audio messages using the configured STT provider */
    suspend fun _enrichMessageWithTranscription(userText: String, audioPaths: List<String>): String {
        return ""
    }
    /** Inject a watch-pattern notification as a synthetic message event. */
    suspend fun _injectWatchNotification(synthText: String, originalEvent: Any?): Unit {
        // TODO: implement _injectWatchNotification
    }
    /** Periodically check a background process and push updates to the user. */
    suspend fun _runProcessWatcher(watcher: Any?): Unit {
        // TODO: implement _runProcessWatcher
    }
    /** Compute a stable string key from agent config values. */
    fun _agentConfigSignature(model: String, runtime: Any?, enabledToolsets: Any?, ephemeralPrompt: String): String {
        return ""
    }
    /** Apply /model session overrides if present, returning (model, runtime_kwargs). */
    fun _applySessionModelOverride(sessionKey: String, model: String, runtimeKwargs: Any?): Any? {
        return null
    }
    /** Return True if *agent_model* matches an active /model session override. */
    fun _isIntentionalModelSwitch(sessionKey: String, agentModel: String): Boolean {
        return false
    }
    /** Remove a cached agent for a session (called on /new, /model, etc). */
    fun _evictCachedAgent(sessionKey: String): Unit {
        // TODO: implement _evictCachedAgent
    }
    /** Run the agent with the given message and context. */
    suspend fun _runAgent(message: String, contextPrompt: String, history: List<Map<String, Any>>, source: SessionSource, sessionId: String, sessionKey: String? = null, _interruptDepth: Int = 0, eventMessageId: String? = null): Map<String, Any> {
        return emptyMap()
    }

}
