package com.ai.assistance.operit.hermes.gateway

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.xiaomo.hermes.hermes.gateway.GatewayRunner
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
                    chatId = chatId,
                    interruptCheck = { runner?.getInterruptFlag(sessionKey)?.get() == true }
                )
            }
            runner = instance
            instance.start()
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
            runner = null
            _status.value = Status.STOPPED
        }
    }

    /** Fire-and-forget start used by service onStartCommand. */
    fun startAsync(): Job = _scope.launch { start() }

    /** Fire-and-forget stop used by service onDestroy. */
    fun stopAsync(): Job = _scope.launch { stop() }

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
        chatId: String,
        interruptCheck: () -> Boolean = { false },
    ): String {
        val historyChatId = "gw:$sessionKey:$chatId"
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

        // Fire-and-forget: this launches a coroutine inside ChatServiceCore
        // that goes through the full MessageCoordinationDelegate →
        // MessageProcessingDelegate → AIMessageManager → EnhancedAIService
        // → HermesAgentLoop pipeline — exactly like the APP UI.
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
            GatewayFileLogger.i(TAG, "═══ runHermesAgent END (interrupted) ═══\n")
            return GatewayRunner.INTERRUPTED_SENTINEL
        }
        val elapsedMs = System.currentTimeMillis() - startMs

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
        GatewayFileLogger.i(TAG, "═══ runHermesAgent END ═══\n")

        return strippedReply
    }

    private fun gatewayChatTitle(sessionKey: String, chatId: String): String {
        val platform = sessionKey.substringBefore(':').ifEmpty { sessionKey }
        val shortChat = chatId.substringBefore('@').take(24).ifEmpty { chatId.take(24) }
        return "[$platform] $shortChat"
    }

    /** Strip all internal XML markup from a text segment, leaving only user-visible text. */
    private fun stripMarkup(text: String): String {
        return text
            .replace(ChatMarkupRegex.thinkTag, "")
            .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
    }

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
