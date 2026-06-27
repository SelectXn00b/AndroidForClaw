package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.library.MemoryLibrary
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookMutation
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.chat.hooks.SystemPromptComposeHook
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
import com.xiaomo.hermes.hermes.gateway.UndeliveredReplyNotifier
import com.xiaomo.hermes.hermes.gateway.UndeliveredReplyStore
import com.xiaomo.hermes.hermes.gateway.platforms.PlatformDiagSink
import com.xiaomo.hermes.hermes.gateway.platforms.WeixinAdapter
import com.xiaomo.hermes.hermes.cron.cronOutboundDispatcher
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives a single [GatewayRunner] instance on behalf of
 * [com.ai.assistance.operit.services.gateway.GatewayForegroundService].
 *
 * Owns a supervisor scope on [Dispatchers.IO] so platform adapter
 * connect/disconnect work does not block the service's main thread.
 * Settings UI observes [status] to render start/stop state live.
 */
class HermesGatewayController private constructor(private val appContext: Context) {

    enum class Status { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var runner: GatewayRunner? = null
    private val _scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _mutex = Mutex()

    suspend fun start(): Boolean = _mutex.withLock {
        if (_status.value == Status.RUNNING || _status.value == Status.STARTING) return@withLock true
        _status.value = Status.STARTING
        _error.value = null
        GatewayFileLogger.logSessionStart()
        try {
            val config = HermesGatewayConfigBuilder.build(appContext)
            if (config.enabledPlatforms.isEmpty()) {
                _status.value = Status.FAILED
                _error.value = "no enabled platforms with credentials"
                Log.w(TAG, "start(): no enabled platforms — refusing to start")
                GatewayFileLogger.w(TAG, "start(): no enabled platforms — refusing to start")
                return@withLock false
            }
            val instance = GatewayRunner(appContext, config)
            instance.agentRunner = { text, sessionKey, platform, chatId, userId ->
                runHermesAgent(
                    text = text,
                    sessionKey = sessionKey,
                    platform = platform,
                    chatId = chatId,
                    interruptCheck = { runner?.getInterruptFlag(sessionKey)?.get() == true }
                )
            }
            // R-GW-003 bugfix: when a delivery finally fails (after retry), persist the reply
            // and pop a local Android notification so the user can copy-paste it manually.
            // UndeliveredReplyStore + UndeliveredReplyNotifier together form the "rescue kit".
            instance.onSendFailed = run {
                val rescueStore: UndeliveredReplyStore = UndeliveredReplyStore(undeliveredFile(appContext))
                val rescueNotifier: UndeliveredReplyNotifier = UndeliveredReplyNotifier(appContext)
                rescueNotifier.ensureChannel()
                ({ platform: String, chatId: String, text: String, error: String ->
                    try {
                        rescueStore.append(platform, chatId, text, error)
                        rescueNotifier.notify(platform, chatId, text, error)
                        GatewayFileLogger.w(TAG, "onSendFailed: persisted+notified platform=$platform chatId=$chatId len=${text.length} error=$error")
                    } catch (e: Throwable) {
                        Log.w(TAG, "rescue kit failed: ${e.message}")
                    }
                })
            }
            // R-UI-062: forward gateway /steer + /stop commands into the
            // GATEWAY-slot ChatServiceCore so they hit the running
            // HermesAgentLoop (registered via EnhancedAIService.activeAgentLoopRef).
            //
            // Mapping: runHermesAgent feeds chats with historyChatId =
            //   "gw:<sessionKey>:<chatId>" (see line ~230). The GATEWAY-slot
            // core's currentChatId tracks the most recently switched chat,
            // which is the one currently being driven. We only honor the
            // callback when the prefix matches `gw:<sessionKey>:` so a
            // command arriving for one session does not steer another.
            instance.steerActiveAgent = { sessionKey, text ->
                val core = ChatRuntimeHolder.getInstance(appContext)
                    .getCore(ChatRuntimeSlot.GATEWAY)
                val historyChatId = core.currentChatId.value
                if (historyChatId != null && historyChatId.startsWith("gw:$sessionKey:")) {
                    core.steerActiveLoop(historyChatId, text)
                } else {
                    false
                }
            }
            instance.cancelActiveAgent = { sessionKey ->
                val core = ChatRuntimeHolder.getInstance(appContext)
                    .getCore(ChatRuntimeSlot.GATEWAY)
                val historyChatId = core.currentChatId.value
                if (historyChatId != null && historyChatId.startsWith("gw:$sessionKey:")) {
                    core.cancelMessage(historyChatId)
                    true
                } else {
                    false
                }
            }
            runner = instance
            instance.start()
            // R-AGENT-033 Bug C: inject the cron→IM dispatcher hook so that
            // Scheduler.deliverResult can fan out cron job output back to the
            // platform adapter that originated the job. `app→hermes-android`
            // is single-direction, so `Scheduler.kt` exposes a top-level
            // `var cronOutboundDispatcher` and we wire it here.
            cronOutboundDispatcher = { platform, chatId, text, threadId ->
                dispatchOutgoing(platform, chatId, text, threadId)
            }
            _status.value = Status.RUNNING
            val msg = "gateway started with ${config.enabledPlatforms.size} platform(s)"
            Log.i(TAG, msg)
            GatewayFileLogger.i(TAG, msg)
            GatewayFileLogger.i(TAG, "log file: ${GatewayFileLogger.getLogFilePath()}")
            true
        } catch (e: Throwable) {
            _status.value = Status.FAILED
            _error.value = e.message
            Log.e(TAG, "start() failed", e)
            GatewayFileLogger.e(TAG, "start() failed: ${e.message}")
            runner = null
            false
        }
    }

    suspend fun stop() = _mutex.withLock {
        val instance = runner ?: run {
            _status.value = Status.STOPPED
            return@withLock
        }
        _status.value = Status.STOPPING
        try {
            instance.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "stop() threw: ${e.message}")
        } finally {
            // R-AGENT-033 Bug C: clear the cron→IM dispatcher hook so that
            // a stale lambda does not leak into the next gateway start (which
            // would re-inject) and so cron ticks while gateway is stopped
            // record an explicit "no dispatcher injected" delivery error.
            cronOutboundDispatcher = null
            runner = null
            _status.value = Status.STOPPED
        }
    }

    /** Fire-and-forget start used by service onStartCommand. */
    fun startAsync(): Job = _scope.launch { start() }

    /** Fire-and-forget stop used by service onDestroy. */
    fun stopAsync(): Job = _scope.launch { stop() }

    /**
     * R-AGENT-033 Bug C: cron → IM outbound dispatch entry point.
     *
     * Looks up the platform adapter by name from `runner.deliveryRouter` and
     * invokes its `.send(chatId, content, replyTo, metadata)`. For Telegram,
     * `threadId` is forwarded via the `message_thread_id` metadata key so
     * the reply lands in the correct topic/thread.
     *
     * Mirrors Python `gateway/run.py` cron deliver loop: `adapters[platform].send(...)`.
     *
     * @return `true` if the adapter accepted the send, `false` otherwise.
     */
    suspend fun dispatchOutgoing(
        platform: String,
        chatId: String,
        text: String,
        threadId: String?
    ): Boolean {
        CronFileLogger.i(
            TAG,
            "dispatchOutgoing IN platform=$platform chat=$chatId textLen=${text.length} thread=${threadId.orEmpty()}"
        )
        val instance = runner
        if (instance == null) {
            Log.w(TAG, "dispatchOutgoing: gateway not running, dropping platform=$platform chatId=$chatId")
            GatewayFileLogger.w(TAG, "dispatchOutgoing: gateway not running, dropping platform=$platform chatId=$chatId")
            CronFileLogger.w(
                TAG,
                "dispatchOutgoing OUT success=false error=gateway not running platform=$platform chat=$chatId"
            )
            return false
        }
        val adapter = instance.deliveryRouter.getAdapter(platform)
        if (adapter == null) {
            Log.w(TAG, "dispatchOutgoing: no adapter for platform=$platform")
            GatewayFileLogger.w(TAG, "dispatchOutgoing: no adapter for platform=$platform")
            CronFileLogger.w(
                TAG,
                "dispatchOutgoing OUT success=false error=no adapter platform=$platform chat=$chatId"
            )
            return false
        }
        // R-OBS-001 wire: if this is the Weixin adapter, lazy-install the
        // WeixinFileLogger as its PlatformDiagSink so connect/send/poll
        // events land in weixin.log. Sibling Telegram/Feishu adapters can
        // follow the same pattern when they get diag instrumentation.
        if (adapter is WeixinAdapter && adapter._diagSink == null) {
            adapter._diagSink = WeixinFileLoggerDiagSink
        }
        // Telegram routes thread replies via the `message_thread_id` metadata key.
        val metadata: JSONObject? = if (platform == "telegram" && !threadId.isNullOrEmpty()) {
            try {
                JSONObject().put("message_thread_id", threadId.toInt())
            } catch (e: NumberFormatException) {
                JSONObject().put("message_thread_id", threadId)
            }
        } else {
            null
        }
        return try {
            val result = adapter.send(
                chatId = chatId,
                content = text,
                replyTo = null,
                metadata = metadata,
            )
            if (!result.success) {
                Log.w(TAG, "dispatchOutgoing: adapter.send returned failure platform=$platform chatId=$chatId error=${result.error}")
                GatewayFileLogger.w(TAG, "dispatchOutgoing: adapter.send failure platform=$platform chatId=$chatId error=${result.error}")
                CronFileLogger.w(
                    TAG,
                    "dispatchOutgoing OUT success=false error=${result.error} platform=$platform chat=$chatId"
                )
            } else {
                GatewayFileLogger.i(TAG, "dispatchOutgoing: delivered platform=$platform chatId=$chatId len=${text.length}")
                CronFileLogger.i(
                    TAG,
                    "dispatchOutgoing OUT success=true platform=$platform chat=$chatId len=${text.length}"
                )
            }
            result.success
        } catch (e: Throwable) {
            Log.w(TAG, "dispatchOutgoing: adapter.send threw platform=$platform chatId=$chatId: ${e.message}")
            GatewayFileLogger.w(TAG, "dispatchOutgoing: adapter.send threw platform=$platform chatId=$chatId: ${e.message}")
            CronFileLogger.e(
                TAG,
                "dispatchOutgoing OUT success=false error=threw:${e.message} platform=$platform chat=$chatId"
            )
            false
        }
    }

    /**
     * Feed [text] through the same ChatServiceCore path the APP UI uses.
     *
     * Instead of calling HermesAdapter (which had its own XML-mode agent loop),
     * we now route through ChatServiceCore — the exact same entry point the UI
     * uses when the user types a message.  This gives us:
     * - Tool Call API mode with package_proxy (structured function calling)
     * - Proper validToolNames enforcement
     * - Full model parameters, token budget, summarization
     * - Chat history persisted in the same Room DB the APP UI reads
     *
     * The gateway-specific chat lives in Room DB with a "gw:" prefixed ID
     * and shows up in the APP's conversation list for visibility.
     */
    private suspend fun runHermesAgent(
        text: String,
        sessionKey: String,
        platform: String,
        chatId: String,
        interruptCheck: () -> Boolean = { false },
    ): String {
        val historyChatId = "gw:$sessionKey:$chatId"
        // TC-GW-DIAG-002: mark run boundary — trims gateway.log to keep
        // only "previous run + this run". See GatewayFileLogger.startRun.
        GatewayFileLogger.startRun(historyChatId)
        GatewayFileLogger.i(TAG, "═══ runHermesAgent START ═══")
        GatewayFileLogger.i(TAG, "  user text (${text.length} chars): ${text.take(1000)}${if (text.length > 1000) "…[truncated]" else ""}")
        GatewayFileLogger.i(TAG, "  chatId: $historyChatId")

        val history = ChatHistoryManager.getInstance(appContext)

        // Handle /new command: clear chat history for this session so the
        // next request starts with a clean context.
        val trimmedText = text.trim()
        if (trimmedText.equals("/new", ignoreCase = true) ||
            trimmedText.equals("新话题", ignoreCase = true)) {
            try {
                history.clearChatMessages(historyChatId)
                GatewayFileLogger.i(TAG, "  /new command — cleared chat history")
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  /new command — failed to clear history: ${e.message}")
            }
            GatewayFileLogger.i(TAG, "═══ runHermesAgent END ═══\n")
            GatewayFileLogger.endRun("$historyChatId (early-out /new)")
            return "好的，已切换到新话题。"
        }

        // Ensure the chat record exists in Room DB (creates it if not).
        val chatTitle = gatewayChatTitle(sessionKey, chatId)
        try {
            history.ensureChatWithId(historyChatId, title = chatTitle)
        } catch (e: Throwable) {
            Log.w(TAG, "failed to ensure gateway chat record: ${e.message}")
        }

        // Get the GATEWAY ChatServiceCore — same component the APP UI uses,
        // but on a dedicated slot so it doesn't interfere with the user's
        // active MAIN or FLOATING sessions.
        val core = ChatRuntimeHolder.getInstance(appContext)
            .getCore(ChatRuntimeSlot.GATEWAY)

        // Switch the gateway core to this chat (local only, doesn't affect
        // the global currentChatId that the MAIN UI tracks).
        core.switchChatLocal(historyChatId)

        // Brief delay to let switchChatLocal's coroutine complete DB load.
        delay(200)

        val prefs = HermesGatewayPreferences.getInstance(appContext)
        val maxTurns = prefs.agentMaxTurnsFlow.first()
        val timeoutMs = maxTurns.toLong() * 120_000L

        GatewayFileLogger.i(TAG, "  maxTurns=$maxTurns timeoutMs=$timeoutMs")
        GatewayFileLogger.i(TAG, "  routing through ChatServiceCore (本体 path)...")

        val startMs = System.currentTimeMillis()

        // R-GW-STREAMING-001 v2 (2026-06-27 simplification): per-turn streaming
        // sidecar. v2 dispatches each AssistantDelta as ONE IM message — no
        // paragraph splitting, no inter-paragraph delay, no retry, no
        // pre-warm. The v1 splitting + serializing through dispatchMutex
        // caused the collector to fall arbitrarily far behind the agent loop
        // (real-device WeChat showed "silence then burst" — segments from
        // earlier turns still being delivered while the agent loop had
        // already finished). v2 keeps the 1:1 mapping between agent turns
        // and IM messages, matching what the app body sees via emitChunk.
        //
        // Finer-grained multi-bubble dispatch is now the agent's job via
        // R-GW-STREAMING-002's `send_message` tool — agents call it inside
        // the loop to push intermediate progress as separate bubbles.
        //
        // Group chats (`@chatroom`) still skip the sidecar entirely
        // (paragraph-by-paragraph delivery to a multi-user channel is noisy;
        // user explicitly said 群聊不需要 2026-06-25).
        val skipSidecar = chatId.endsWith("@chatroom")
        val sidecar: AgentStreamingSidecar? = if (skipSidecar) {
            GatewayFileLogger.i(TAG, "  [streaming] skip sidecar (group chat @chatroom): chatId=$chatId")
            null
        } else {
            AgentStreamingSidecar(
                busTagChatId = historyChatId,
                wireChatId = chatId,
                platform = platform,
                dispatchOutgoing = { p, c, t, th -> dispatchOutgoing(p, c, t, th) },
                threadId = null,
                tag = "GwStreamingSidecar",
            ).also { sc ->
                sc.start(_scope)
                // Wait for SharedFlow(replay=0) subscription registration before
                // triggering the agent — see AgentStreamingSidecar KDoc.
                sc.awaitReady()
                GatewayFileLogger.i(
                    TAG,
                    "  [streaming] sidecar ready chatId=$historyChatId platform=$platform"
                )
            }
        }

        // R-GW-STREAMING-001 (TC-GW-STREAMING-001-j, v2 2026-06-26): inject bilingual
        // multi-message hint via SystemPromptComposeHook — Python upstream's
        // `ephemeral_system_prompt` idiom. The hint lands in the `role: "system"`
        // message at API-call time, never persisted to Room as a user message and
        // never leaks across turns. v1 wrapped the hint into `messageTextOverride`
        // (a buildString of HINT + "\n\n" + text) — that was an architectural error:
        // MessageProcessingDelegate persists `messageTextOverride` as
        // `ChatMessage(sender="user")` in Room and feeds it back into the next
        // turn's history, polluting both the chat UI and the conversation context.
        //
        // The hook is unregistered in a `finally` block to avoid leaks into the
        // APP UI path (PromptHookRegistry is process-global). NOTE (2026-06-26):
        // we previously gated this hook with `context.chatId == historyChatId`,
        // but `SystemPromptConfig.getSystemPromptWithCustomPrompts(...)` does NOT
        // thread chatId into `PromptHookContext`, so `context.chatId` is always
        // null and the gate always failed → the hint was never injected → the
        // real-device "回复还是一坨" bug. Fix is Plan A: drop the chatId filter
        // so the hook fires on every compose pass during the gateway run. The
        // collateral is that any APP-UI prompt-compose pass that races inside
        // the gateway's `try { ... }` window will also get the hint — accepted
        // because (a) the hint is neutral advice, (b) the window is short
        // (single agent turn), and (c) the hint is ephemeral (system-prompt
        // addendum, never persisted). Cleaner-architecture fix (Plan B —
        // thread chatId through SystemPromptConfig) is deferred.
        val hookId = "gw:multi-message-hint:$historyChatId:${System.nanoTime()}"
        val multiMessageHintHook = object : SystemPromptComposeHook {
            override val id: String = hookId

            override fun onEvent(context: PromptHookContext): PromptHookMutation? {
                val base = context.systemPrompt ?: ""
                val composed = if (base.isBlank()) MULTI_MESSAGE_HINT
                    else base + "\n\n" + MULTI_MESSAGE_HINT
                return PromptHookMutation(systemPrompt = composed)
            }
        }
        PromptHookRegistry.registerSystemPromptComposeHook(multiMessageHintHook)
        GatewayFileLogger.i(TAG, "  [streaming] registered SystemPromptComposeHook id=$hookId")

        // R-GW-STREAMING-002: register per-chatId outbound dispatcher so the
        // gateway-only `send_message` tool (injected by EnhancedAIService on
        // `isSubTask && chatId.startsWith("gw:")` path) can push chunks to
        // the IM during the agent loop. Cleanup in `finally`.
        GatewayOutboundRegistry.register(historyChatId) { text ->
            dispatchOutgoing(platform, chatId, text, null)
        }
        GatewayFileLogger.i(TAG, "  [send_message] registered outbound dispatcher chatId=$historyChatId")

        try {

        // Fire-and-forget: this launches a coroutine inside ChatServiceCore
        // that goes through the full MessageCoordinationDelegate →
        // MessageProcessingDelegate → AIMessageManager → EnhancedAIService
        // → HermesAgentLoop pipeline — exactly like the APP UI. The raw user
        // text passes through unmodified; the multi-message hint is added by
        // the SystemPromptComposeHook above (role: "system" addendum), not by
        // user-text prefix.
        core.sendUserMessage(
            chatIdOverride = historyChatId,
            messageTextOverride = text,
            isSubTask = true
        )

        // Notify the MAIN UI that the gateway has started processing this chat.
        // The subscribers in MessageProcessingDelegate and ChatHistoryDelegate
        // will add the chatId to _activeStreamingChatIds and reload messages,
        // so the user sees the "processing" indicator if they're viewing this chat.
        GatewayChatEventBus.emit(GatewayChatEventBus.Event.UserMessagePersisted(historyChatId))
        GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingStarted(historyChatId))

        // Emit periodic StreamingUpdate events so the MAIN UI can reload
        // from DB and show progressively growing AI content.  The GATEWAY
        // core already persists streaming snapshots every ~1000ms; this
        // coroutine notifies the MAIN UI to pick them up.
        val streamingUpdateJob = _scope.launch {
            delay(STREAMING_UPDATE_INTERVAL_MS)
            while (true) {
                GatewayChatEventBus.emit(GatewayChatEventBus.Event.StreamingUpdate(historyChatId))
                delay(STREAMING_UPDATE_INTERVAL_MS)
            }
        }

        // Wait for the processing to complete by observing the
        // activeStreamingChatIds StateFlow.  The chatId enters the set when
        // processing starts and leaves when it finishes.
        //
        // IMPORTANT: When a token-limit is hit mid-agent-loop, the first
        // round's chatId leaves activeStreamingChatIds *before* the
        // auto-continuation second round re-adds it (there is a gap while
        // summarization runs).  We must loop and re-check after a
        // stabilization window to avoid returning intermediate text.
        var wasInterrupted = false
        val completed = try { withTimeoutOrNull(timeoutMs) {
            // First wait for the chatId to appear (processing started)
            val appeared = withTimeoutOrNull(10_000L) {
                core.activeStreamingChatIds.first { historyChatId in it }
            }
            if (appeared == null) {
                // Check if it already finished before we started observing
                val state = core.inputProcessingStateByChatId.value[historyChatId]
                if (state is InputProcessingState.Completed || state is InputProcessingState.Idle) {
                    GatewayFileLogger.i(TAG, "  processing completed before we started observing")
                    return@withTimeoutOrNull true
                }
                if (state is InputProcessingState.Error) {
                    GatewayFileLogger.w(TAG, "  processing errored before observation: ${state.message}")
                    return@withTimeoutOrNull true
                }
                GatewayFileLogger.w(TAG, "  chatId never appeared in activeStreamingChatIds within 10s")
            }

            // Wait for chatId to leave, but account for continuation gaps.
            // After it leaves, check whether a continuation is pending
            // (summarization in progress or Summarizing state), and if so
            // wait for the next round.
            while (true) {
                // Wait for chatId to leave OR interrupt to be signaled.
                // Poll every INTERRUPT_POLL_MS to check both conditions.
                var interruptDetected = false
                while (true) {
                    if (interruptCheck()) {
                        interruptDetected = true
                        break
                    }
                    if (historyChatId !in core.activeStreamingChatIds.value) break
                    delay(INTERRUPT_POLL_MS)
                }

                if (interruptDetected) {
                    GatewayFileLogger.i(TAG, "  ⚡ Interrupt detected — cancelling agent run")
                    core.cancelMessage(historyChatId)
                    // Wait for cancellation to take effect
                    withTimeoutOrNull(10_000L) {
                        while (historyChatId in core.activeStreamingChatIds.value) {
                            delay(200)
                        }
                    }
                    delay(300) // let isLoading fully clear
                    GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingFailed(historyChatId))
                    wasInterrupted = true
                    return@withTimeoutOrNull true
                }

                // The agent completed naturally (chatId left activeStreamingChatIds).
                // Check the interrupt flag one more time: if it was set while the
                // agent was finishing (race condition), we still treat it as interrupted
                // so the old response is discarded and the pending message gets processed.
                if (interruptCheck()) {
                    GatewayFileLogger.i(TAG, "  ⚡ Interrupt flag set after agent completed — treating as interrupted")
                    GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingFailed(historyChatId))
                    wasInterrupted = true
                    return@withTimeoutOrNull true
                }

                GatewayFileLogger.i(TAG, "  chatId left activeStreamingChatIds, checking for continuation...")

                // Fast path: if the processing state is Completed and no summary
                // (neither mid-stream nor pre-send async) is running, we're done.
                val procState = core.inputProcessingStateByChatId.value[historyChatId]
                if (procState is InputProcessingState.Completed && !core.isSummarizing.value && !core.isSendTriggeredSummarizing.value) {
                    // One final interrupt check before declaring completion
                    if (interruptCheck()) {
                        GatewayFileLogger.i(TAG, "  ⚡ Interrupt flag set during continuation check — treating as interrupted")
                        GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingFailed(historyChatId))
                        wasInterrupted = true
                        return@withTimeoutOrNull true
                    }
                    GatewayFileLogger.i(TAG, "  inputProcessingState=Completed, no summarization — done immediately")
                    break
                }

                // If the core is currently summarizing (mid-stream), an auto-continuation
                // is about to start.  Wait for summarization to finish, then
                // wait for the new round to appear.
                if (core.isSummarizing.value) {
                    GatewayFileLogger.i(TAG, "  core is summarizing — waiting for it to finish")
                    core.isSummarizing.first { !it }
                    GatewayFileLogger.i(TAG, "  summarization finished, re-checking activeStreamingChatIds")
                }

                // If a pre-send async summary is running, the Completed state is
                // suppressed until it finishes.  Wait for it instead of falling
                // through to the 45-second stabilization window.
                if (core.isSendTriggeredSummarizing.value) {
                    GatewayFileLogger.i(TAG, "  pre-send async summary in progress — waiting for it to finish")
                    core.isSendTriggeredSummarizing.first { !it }
                    GatewayFileLogger.i(TAG, "  pre-send async summary finished")
                    // Re-check procState: it should now transition to Completed/Idle
                    delay(300)
                    val updatedState = core.inputProcessingStateByChatId.value[historyChatId]
                    if (updatedState is InputProcessingState.Completed || updatedState is InputProcessingState.Idle) {
                        GatewayFileLogger.i(TAG, "  state=$updatedState after async summary — done")
                        break
                    }
                }

                // Stabilization window: wait up to CONTINUATION_SETTLE_MS
                // to see if the chatId re-enters (a new round started).
                val reEntered = withTimeoutOrNull(CONTINUATION_SETTLE_MS) {
                    core.activeStreamingChatIds.first { historyChatId in it }
                    true
                } ?: false

                if (reEntered) {
                    GatewayFileLogger.i(TAG, "  chatId re-entered — continuation round started, waiting again")
                    continue
                }

                // No re-entry — truly done.
                GatewayFileLogger.i(TAG, "  stable — processing truly finished")
                break
            }
            true
        } } finally { streamingUpdateJob.cancel() }

        // If interrupted, return the sentinel immediately — caller will process the pending event.
        if (wasInterrupted) {
            // R-GW-STREAMING-001 (TC-GW-STREAMING-001-h): stop sidecar on interrupt
            // so its collector job doesn't outlive this turn. In-flight dispatch
            // calls are protected by `withContext(NonCancellable)` inside the
            // sidecar so any paragraph already mid-flight still finishes.
            try { sidecar?.stop() } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  [streaming] sidecar.stop on interrupt threw: ${e.message}")
            }
            GatewayFileLogger.i(TAG, "═══ runHermesAgent END (interrupted) ═══\n")
            GatewayFileLogger.endRun("$historyChatId (interrupted)")
            return GatewayRunner.INTERRUPTED_SENTINEL
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        // R-GW-STREAMING-001 (TC-GW-STREAMING-001-h/i/j): now that the main wait
        // loop has confirmed the agent finished, stop the sidecar and decide
        // whether to return the STREAMING_DELIVERED sentinel (so GatewayRunner
        // ._handleMessage skips its fallback deliveryRouter.deliverText) or
        // fall through to the normal DB-read + return path.
        val streamingDelivered = sidecar?.let { sc ->
            val delivered = sc.wasDelivered()
            try { sc.stop() } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  [streaming] sidecar.stop threw: ${e.message}")
            }
            GatewayFileLogger.i(
                TAG,
                "  [streaming] sidecar done chatId=$historyChatId delivered=$delivered " +
                    "events=${sc.totalEvents.get()} matched=${sc.chatIdMatched.get()} " +
                    "deltas=${sc.assistantDeltas.get()} dispatches=${sc.dispatchCalls.get()} " +
                    "success=${sc.dispatchSuccess.get()}"
            )
            delivered
        } ?: false

        if (completed == null) {
            Log.w(TAG, "runHermesAgent: TIMED OUT after ${elapsedMs}ms")
            GatewayFileLogger.w(TAG, "  TIMED OUT after ${elapsedMs}ms")
            GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingFailed(historyChatId))
        } else {
            Log.i(TAG, "runHermesAgent: completed in ${elapsedMs}ms")
            GatewayFileLogger.i(TAG, "  completed in ${elapsedMs}ms")
            GatewayChatEventBus.emit(GatewayChatEventBus.Event.ProcessingCompleted(historyChatId))
        }

        // Read the last AI message from Room DB.
        // ChatServiceCore has already persisted both user and AI messages
        // through its normal pipeline (MessageProcessingDelegate → ChatHistoryDelegate).
        val lastAiMessage = try {
            val messages = history.loadChatMessages(historyChatId)
            messages.lastOrNull { it.sender == "ai" }
        } catch (e: Throwable) {
            Log.w(TAG, "failed to read AI reply from DB: ${e.message}")
            GatewayFileLogger.w(TAG, "  failed to read AI reply from DB: ${e.message}")
            null
        }

        val rawContent = lastAiMessage?.content ?: ""
        // Extract the final reply from the raw content.
        // The raw content may contain multiple agent turns with interleaved
        // <think>, <tool>, <tool_result>, and <status> tags.  We want only
        // the text from the LAST turn — the actual answer.
        //
        // Strategy: find the last <status type="complete"> tag.  The text
        // between it and the preceding markup tag (tool_result, tool, think,
        // or status) is the final reply.  If no <status type="complete"> is
        // found, fall back to stripping all markup and returning everything.
        val strippedReply = extractFinalReply(rawContent).ifEmpty {
            if (completed == null) "(agent timed out)" else "(empty response)"
        }

        GatewayFileLogger.i(TAG, "  stripped reply length: ${strippedReply.length}")
        if (strippedReply == "(empty response)") {
            GatewayFileLogger.w(TAG, "  ⚠ EMPTY RESPONSE — raw content was: ${rawContent.take(500)}")
        } else if (strippedReply == "(agent timed out)") {
            GatewayFileLogger.w(TAG, "  ⚠ AGENT TIMED OUT — raw content tail: ${rawContent.takeLast(500)}")
        } else {
            GatewayFileLogger.i(TAG, "  full reply (${strippedReply.length} chars): ${strippedReply.take(2000)}${if (strippedReply.length > 2000) "…[truncated]" else ""}")
        }

        // R-AGENT-010: force-save gateway conversation as long-term memory.
        // APP UI path triggers MemoryLibrary.saveMemoryAsync only when the agent
        // outputs <complete>; gateway short-chat scenarios almost never write
        // <complete>, so we must force a save here. Gated by ApiPreferences
        // .enableMemoryQueryFlow (same switch as APP UI). Skipped on interrupt
        // (already short-circuited above), empty/timeout placeholders.
        if (!interruptCheck() &&
            strippedReply.isNotBlank() &&
            strippedReply != "(empty response)" &&
            strippedReply != "(agent timed out)"
        ) {
            try {
                val enableMemoryQuery = ApiPreferences.getInstance(appContext)
                    .enableMemoryQueryFlow.first()
                if (enableMemoryQuery) {
                    val conversationHistory: List<Pair<String, String>> = try {
                        ChatHistoryManager.getInstance(appContext)
                            .loadChatMessages(historyChatId)
                            .map { msg ->
                                val role = when (msg.sender) {
                                    "ai" -> "assistant"
                                    "user" -> "user"
                                    "system" -> "system"
                                    else -> msg.sender
                                }
                                role to msg.content
                            }
                    } catch (e: Throwable) {
                        GatewayFileLogger.w(TAG, "  R-AGENT-010: failed to load history for memory save: ${e.message}")
                        emptyList()
                    }
                    // bugfix 2026-06-06 (TC-AGENT-246-f): reuse EnhancedAIService singleton's
                    // multiServiceManager (via the public companion helper) instead of allocating
                    // a fresh MultiServiceManager per turn —— shares service cache + token
                    // counters with APP UI and honors EnhancedAIService.refreshServiceForFunction
                    // (FunctionType.MEMORY) cache-invalidation when the user changes MEMORY
                    // config in settings.
                    val memoryService = EnhancedAIService.getAIServiceForFunction(
                        appContext, FunctionType.MEMORY
                    )
                    // R-AGENT-011 (2026-06-06): tag every memory node produced by this gateway
                    // turn with `#gateway:<platform>` so users can distinguish (or filter out)
                    // bot-driven memories from APP-UI-created ones in MemoryScreen. `platform`
                    // is derived from sessionKey (§1: sessionKey = "<platform>:<chat>"), so
                    // feishu → "#gateway:feishu", wechat → "#gateway:wechat", etc.
                    val gatewayPlatform = sessionKey.substringBefore(':').ifEmpty { sessionKey }
                    MemoryLibrary.saveMemoryAsync(
                        appContext,
                        AIToolHandler.getInstance(appContext),
                        conversationHistory,
                        strippedReply,
                        memoryService,
                        onError = { e ->
                            GatewayFileLogger.w(TAG, "  R-AGENT-010: saveMemoryAsync failed: ${e.message}")
                        },
                        extraTags = listOf("#gateway:$gatewayPlatform")
                    )
                    GatewayFileLogger.i(TAG, "  R-AGENT-010: dispatched saveMemoryAsync (history=${conversationHistory.size} msgs, gatewayTag=#gateway:$gatewayPlatform)")
                } else {
                    GatewayFileLogger.i(TAG, "  R-AGENT-010: enableMemoryQuery=false — skipped memory save")
                }
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  R-AGENT-010: memory save wiring failed: ${e.message}")
            }
        }

        GatewayFileLogger.i(TAG, "═══ runHermesAgent END ═══\n")
        GatewayFileLogger.endRun(historyChatId)

        // R-GW-STREAMING-001 (TC-GW-STREAMING-001-h/j): if the sidecar successfully
        // dispatched at least one paragraph, the user has already received the reply
        // turn-by-turn. Return the STREAMING_DELIVERED sentinel so GatewayRunner
        // ._handleMessage SKIPS its fallback `deliveryRouter.deliverText(...)` —
        // re-sending `strippedReply` here would duplicate the full reply on top of
        // the already-streamed paragraphs. Memory save / chat history persistence
        // above is unaffected (those happen via core.sendUserMessage pipeline +
        // MemoryLibrary.saveMemoryAsync regardless of sentinel).
        if (streamingDelivered) {
            return GatewayRunner.STREAMING_DELIVERED_SENTINEL
        }

        return strippedReply
        } finally {
            // R-GW-STREAMING-001 (TC-GW-STREAMING-001-j v2): unregister the
            // SystemPromptComposeHook so the MULTI_MESSAGE_HINT doesn't leak
            // into subsequent gateway invocations or the APP UI path.
            // PromptHookRegistry is process-global.
            try {
                PromptHookRegistry.unregisterSystemPromptComposeHook(hookId)
                GatewayFileLogger.i(TAG, "  [streaming] unregistered SystemPromptComposeHook id=$hookId")
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  [streaming] unregisterSystemPromptComposeHook threw: ${e.message}")
            }
            // R-GW-STREAMING-002: remove per-chatId outbound dispatcher so it
            // doesn't leak into future invocations. ConcurrentHashMap.remove
            // is idempotent — safe even if the agent threw before register.
            try {
                GatewayOutboundRegistry.unregister(historyChatId)
                GatewayFileLogger.i(TAG, "  [send_message] unregistered outbound dispatcher chatId=$historyChatId")
            } catch (e: Throwable) {
                GatewayFileLogger.w(TAG, "  [send_message] unregister threw: ${e.message}")
            }
        }
    }

    private fun gatewayChatTitle(sessionKey: String, chatId: String): String {
        val platform = sessionKey.substringBefore(':').ifEmpty { sessionKey }
        val shortChat = chatId.substringBefore('@').take(24).ifEmpty { chatId.take(24) }
        return "[$platform] $shortChat"
    }

    /** R-GW-003: path to the undelivered-reply JSONL store. Same folder as gateway logs. */
    private fun undeliveredFile(ctx: Context): java.io.File {
        // Mirror GatewayFileLogger's path layout for consistency.
        val externalDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val dir = java.io.File(externalDir, "gateway_rescue")
        dir.mkdirs()
        return java.io.File(dir, "undelivered.jsonl")
    }

    /**
     * Strip all internal XML markup from a text segment, leaving only
     * user-visible text.  Delegates to [HermesReplyMarkupStripper] so
     * cron-headless and gateway-normal paths share one source of truth
     * (TC-CRON-SANITIZE-a).  Previously this function held its own
     * regex chain + a private `UNCLOSED_THINK_REGEX`; both moved to the
     * stripper object.
     */
    private fun stripMarkup(text: String): String = HermesReplyMarkupStripper.strip(text)

    /**
     * Extract the final reply text from raw AI message content.
     *
     * The raw content contains interleaved XML markup and plain text across
     * multiple agent turns.  We find the last `<status type="complete">`
     * (or `<status type="wait_for_user_need">`) and extract all plain text
     * between the preceding markup boundary and that status tag.
     *
     * If no status tag is found, fall back to stripping all markup and
     * returning everything (single-turn simple response).
     */
    private fun extractFinalReply(rawContent: String): String {
        if (rawContent.isBlank()) return ""

        // Find the last <status ...> tag position
        val lastStatusIdx = LAST_STATUS_TAG_REGEX.findAll(rawContent)
            .lastOrNull()?.range?.first ?: -1

        if (lastStatusIdx <= 0) {
            // No status tag found — strip all markup and return everything
            val stripped = stripMarkup(rawContent).trim()
            return stripped.ifEmpty { extractThinkingFallback(rawContent) }
        }

        // From the text before the last status tag, find the nearest
        // preceding markup boundary (end of </think>, </tool_result>,
        // </tool>, or another </status>).
        val textBeforeStatus = rawContent.substring(0, lastStatusIdx)
        val lastMarkupEnd = MARKUP_END_TAG_REGEX.findAll(textBeforeStatus)
            .lastOrNull()?.let { it.range.last + 1 } ?: 0

        // The final reply is the text between the last markup end and
        // the last status tag, with any remaining markup stripped.
        val replySlice = rawContent.substring(lastMarkupEnd, lastStatusIdx)
        val cleaned = stripMarkup(replySlice).trim()

        if (cleaned.isNotEmpty()) return cleaned

        // If the slice is empty (e.g., status tag immediately follows
        // tool_result), fall back to full stripped content.
        val fullStripped = stripMarkup(rawContent).trim()
        return fullStripped.ifEmpty { extractThinkingFallback(rawContent) }
    }

    /**
     * Fallback for reasoning models (e.g. Qwen 3.5) that produce only
     * `<think>...</think>` without any visible reply text.  Rather than
     * showing "(empty response)" to the user, extract the thinking content
     * and return it directly — it IS the model's response.
     */
    private fun extractThinkingFallback(rawContent: String): String {
        val match = THINK_CONTENT_REGEX.findAll(rawContent).lastOrNull() ?: return ""
        val thinkText = match.groupValues[1].trim()
        if (thinkText.isBlank()) return ""
        GatewayFileLogger.i(TAG, "  using thinking-content fallback (${thinkText.length} chars)")
        return thinkText
    }

    companion object {
        private const val TAG = "HermesGatewayCtl"

        /**
         * After the chatId leaves activeStreamingChatIds and the processing
         * state is NOT Completed, wait this long to see if it re-enters
         * (indicating an auto-continuation round is starting after
         * summarization).  Only used when the fast-path check doesn't apply.
         */
        private const val CONTINUATION_SETTLE_MS = 45_000L

        /**
         * Interval between [GatewayChatEventBus.Event.StreamingUpdate]
         * emissions so the MAIN UI can periodically reload growing AI
         * content from DB while the GATEWAY core is streaming.
         */
        private const val STREAMING_UPDATE_INTERVAL_MS = 1_500L

        /** Polling interval for interrupt detection in the wait loop. */
        private const val INTERRUPT_POLL_MS = 500L

        // R-GW-STREAMING-001 v2 (2026-06-27): the v1 `STREAMING_PARAGRAPH_REGEX`
        // and `STREAMING_INTER_PARAGRAPH_DELAY_MS` constants have been removed.
        // v2 sidecar dispatches each AssistantDelta as one IM message with no
        // segmentation or inter-segment delay — see [AgentStreamingSidecar]
        // class doc for rationale.

        /**
         * R-GW-STREAMING-001 (TC-GW-STREAMING-001-j): bilingual multi-message
         * delivery hint injected into the user message at the head of every gateway
         * agent turn. Mirrors `CronAgentRunner.MULTI_MESSAGE_HINT` (R-CRON-STREAMING-
         * 002 / TC-CRON-STREAMING-h) but is an **independent** declaration to keep
         * the gateway path free of cross-file constant coupling.
         *
         * Both English `blank line` and Chinese `空行` keywords are present so the
         * hint is understandable by English-mode and Chinese-mode agents alike.
         * The hint is a *soft* nudge — it does not force the agent to split, and
         * `AgentStreamingSidecar` still falls back to paragraph-regex splitting
         * even when the agent produces a single block.
         */
        private const val MULTI_MESSAGE_HINT =
            "[MULTI-MESSAGE HINT] You are replying to an IM chat (e.g. WeChat). To make the conversation feel responsive, " +
                "you SHOULD call the `send_message` tool to push each step of your progress as a separate bubble " +
                "(e.g. \"I'm checking the weather...\", \"Found it: rain today\", \"Suggest bringing an umbrella\"). " +
                "Each `send_message` call appears as one IM bubble immediately — the user sees you working step by step " +
                "instead of waiting for one long blob at the end. You may still return a final summary reply at the end " +
                "of the agent loop; tool calls do not replace it. Fallback: if you are NOT calling the tool, separate " +
                "your paragraphs with a blank line (\\n\\n) so the IM client can still split them.\n" +
                "[多消息提示] 你正在回复一段 IM 聊天（如微信）。为了让对话有推进感，请**主动调用 `send_message` 工具**，" +
                "把每一步的进度作为独立气泡发出去（例如「我去查一下天气...」「查到了：今天有雨」「建议带伞」）。" +
                "每次调用本工具会立即在 IM 里显示一条独立气泡，用户能看到你在一步一步工作，而不是等一大坨结果。" +
                "最终 turn 仍可正常返回一条总结性回复，工具调用不会替代它。退路：如果你**没有**调用该工具，" +
                "请在段落之间留一个空行（\\n\\n），IM 端会自动拆分。"

        /** Matches the last `<status ...>...</status>` or self-closing `<status .../>`. */
        private val LAST_STATUS_TAG_REGEX = Regex(
            "<status\\b[^>]*(?:>[\\s\\S]*?</status>|/>)",
            RegexOption.IGNORE_CASE
        )

        /**
         * Matches the end of any markup closing tag that acts as a
         * boundary between agent turns: `</think>`, `</thinking>`,
         * `</tool_result>`, `</tool>`, `</status>`.
         */
        private val MARKUP_END_TAG_REGEX = Regex(
            "</(?:think(?:ing)?|tool_result|tool|status)\\s*>",
            RegexOption.IGNORE_CASE
        )

        /**
         * Catches unclosed `<think>` / `<thinking>` tags.  The model sometimes
         * emits `<think>…` without a matching `</think>`.  After paired-tag
         * regexes have removed properly closed blocks, this sweeps any
         * remaining opening-tag-to-end-of-string residue.
         */
        private val UNCLOSED_THINK_REGEX = Regex(
            "<think(?:ing)?\\b[^>]*>[\\s\\S]*",
            RegexOption.IGNORE_CASE
        )

        /**
         * Extracts the content inside `<think>...</think>` or `<thinking>...</thinking>`.
         * Used as fallback when reasoning models (Qwen, etc.) produce only thinking
         * content without a visible reply.
         */
        private val THINK_CONTENT_REGEX = Regex(
            "<think(?:ing)?\\b[^>]*>([\\s\\S]*?)</think(?:ing)?>",
            RegexOption.IGNORE_CASE
        )

        @Volatile private var INSTANCE: HermesGatewayController? = null

        fun getInstance(context: Context): HermesGatewayController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HermesGatewayController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
