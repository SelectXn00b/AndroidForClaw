package com.ai.assistance.operit.hermes.gateway

import com.xiaomo.hermes.hermes.AgentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * R-GW-STREAMING-001 v2 (2026-06-27 simplification): per-turn streaming sidecar
 * for the IM gateway dispatch path.
 *
 * **v2 design — one AssistantDelta = one IM message (no segmenting)**:
 *
 * The v1 design split each AssistantDelta into paragraphs and serialized
 * dispatch through `dispatchMutex.withLock { withContext(NonCancellable) {...} }`
 * with caller-supplied `interParagraphDelayMs` gap.  v1.1 added pre-warm 800ms +
 * retry-once 2000ms + bumped gap to 1500ms.  All four extras lived inside the
 * mutex, so the single collector fell arbitrarily far behind the agent loop:
 * the real-device WeChat experience was "silence for tens of seconds, then a
 * burst of all paragraphs catching up" (2026-06-27 user report).  The agent
 * loop itself was streaming fine — only the IM consumer was slow.
 *
 * v2 deletes segmenting / mutex / inter-paragraph delay / pre-warm / retry.
 * Each [AgentEvent.AssistantDelta] = one stripped, trimmed text = one
 * `dispatchOutgoing(...)` call = one IM bubble.  The 1:1 mapping between
 * agent turns and IM messages restores real-time experience and matches
 * what the user sees in the app body (which emits per-turn via `emitChunk`).
 *
 * Finer-grained multi-bubble dispatch is now the agent's responsibility via
 * R-GW-STREAMING-002's `send_message` tool — agents call it inside the loop
 * to push intermediate progress as separate bubbles.  This is also more
 * aligned with the Python upstream `send_message_tool.py` design.
 *
 * **What it does**:
 *  - subscribes to [AgentEventBus.events]
 *  - filters [AgentEvent.AssistantDelta] tagged with `chatId == this.busTagChatId`
 *  - strips Hermes XML markup via [HermesReplyMarkupStripper.strip]
 *  - if non-blank, dispatches the whole stripped text in a single
 *    `dispatchOutgoing(...)` call wrapped in `withContext(NonCancellable)`,
 *    passing [wireChatId] (NOT busTagChatId) as the platform-native chatId
 *
 * **What it deliberately does NOT do (anymore)**:
 *  - split text into paragraphs (handled by agent + `send_message` tool now)
 *  - serialize through a mutex (single AssistantDelta per turn → naturally serial)
 *  - inter-paragraph delay (no paragraphs)
 *  - retry on failure (failed dispatches log a warning; agent's next turn or
 *    `send_message` call recovers)
 *  - pre-warm delay (no need to "fake typing cadence" — agent loop pacing
 *    drives the natural cadence)
 *  - persist to app history / memory / chat note.  Caller (cron or gateway)
 *    still does its own bookkeeping after the agent loop finishes.
 *
 * **Subscription-race protection** (preserved from v1): [AgentEventBus.events]
 * is a `SharedFlow(replay=0)`, so the agent's first `AssistantDelta` (emitted
 * synchronously from inside `enhancedService.sendMessage(...)`) can race ahead
 * of `flow.collect { ... }` registering its subscription.  We use a
 * [CompletableDeferred] + `onSubscription { ready.complete(Unit) }` so the
 * caller can [awaitReady] before triggering the agent.
 *
 * **In-flight cancel guard** (preserved from v1): the caller cancels the
 * sidecar [Job] after main collect finishes, but an in-flight `dispatchOutgoing(...)`
 * (OkHttp request) would also be cancelled, surfacing as `ok=false`.  We wrap
 * dispatch in `withContext(NonCancellable)` so the OkHttp call always runs
 * to completion.
 *
 * **Failure semantics** (preserved from v1): a failed dispatch logs to
 * [GatewayFileLogger] but does **not** throw or abort the agent loop.
 * [CancellationException] is always rethrown — structured-concurrency
 * invariant.
 *
 * **Observability**: each dispatch logs to [GatewayFileLogger] with
 * `streaming dispatch turn=` / `textLen=` fields.
 *
 * @property busTagChatId tagged chat id on [AgentEventBus] used for filtering
 *   — gateway path uses `"gw:$sessionKey:$chatId"` (the historyChatId),
 *   cron path uses `"cron-$jobId"`.  This is the Hermes-internal identity
 *   and MUST match how the producer (agent loop) tags its events.
 * @property wireChatId platform-native chat id passed UNCHANGED to
 *   [dispatchOutgoing] (and from there into the IM adapter `send`).  For
 *   WeChat this is `wxid@im.wechat`; for Telegram this is the numeric chat
 *   id; for cron it's the per-platform native form.  MUST NOT be the
 *   historyChatId — the IM adapter has no logic to strip Hermes prefixes
 *   and a polluted chatId on WeChat causes iLink `errcode=-3`
 *   (2026-06-27 real-device regression that motivated splitting this field).
 * @property platform IM platform identifier (e.g. `"weixin"`, `"telegram"`).
 * @property dispatchOutgoing caller-supplied IM send callback.
 *   `(platform, chatId, text, threadId?) -> ok`.
 * @property threadId optional thread/topic id for platforms that support it
 *   (Telegram); `null` for non-threaded platforms.
 */
class AgentStreamingSidecar(
    private val busTagChatId: String,
    private val wireChatId: String,
    private val platform: String,
    private val dispatchOutgoing: suspend (String, String, String, String?) -> Boolean,
    private val threadId: String? = null,
    private val tag: String = "AgentStreamingSidecar",
) {

    /** subscription-ready signal — see class doc. */
    private val ready: CompletableDeferred<Unit> = CompletableDeferred()

    /** records whether ANY dispatch has been successfully delivered. Caller
     *  uses this to decide whether to return `STREAMING_DELIVERED_SENTINEL`
     *  (skip the fallback `deliverText` in `Run.kt`) vs returning the
     *  original text. */
    private val deliveredAny = AtomicBoolean(false)

    /** observability counters — caller can read at the end and log. */
    val totalEvents = AtomicInteger(0)
    val chatIdMatched = AtomicInteger(0)
    val assistantDeltas = AtomicInteger(0)
    val dispatchCalls = AtomicInteger(0)
    val dispatchSuccess = AtomicInteger(0)

    /** the sidecar collect [Job], owned by the caller's [CoroutineScope]. */
    private var collectJob: Job? = null

    /**
     * Launches the sidecar collect job inside [scope].  Returns immediately;
     * the caller MUST call [awaitReady] before triggering the agent to avoid
     * the SharedFlow(replay=0) race window.
     */
    fun start(scope: CoroutineScope): Job {
        val job = scope.launch {
            try {
                AgentEventBus.events
                    .onSubscription { ready.complete(Unit) }
                    .filter { tagged ->
                        totalEvents.incrementAndGet()
                        val match = tagged.chatId == this@AgentStreamingSidecar.busTagChatId
                        if (match) chatIdMatched.incrementAndGet()
                        match
                    }
                    .collect { tagged ->
                        val event = tagged.event
                        if (event is AgentEvent.AssistantDelta) {
                            assistantDeltas.incrementAndGet()
                            handleAssistantDelta(event)
                        }
                        // Other agent event types are intentionally NOT
                        // dispatched as IM bubbles — they're internal agent
                        // loop signals, not user-visible turn output.
                    }
            } catch (e: CancellationException) {
                // structured-concurrency requires rethrow
                throw e
            } catch (e: Throwable) {
                GatewayFileLogger.w(
                    tag,
                    "sidecar collect threw busTagChatId=$busTagChatId wireChatId=$wireChatId " +
                        "platform=$platform reason=${e.message}"
                )
            }
        }
        collectJob = job
        return job
    }

    /** Suspends until the [onSubscription] callback has fired, guaranteeing
     *  that AssistantDelta events emitted AFTER this returns will be observed
     *  by the collector.  Caller MUST await before triggering the agent. */
    suspend fun awaitReady() {
        ready.await()
    }

    /** True if at least one dispatch has been successfully delivered.  Caller
     *  reads after the agent loop finishes to decide whether to return the
     *  streaming-delivered sentinel (skip fallback IM send) vs. fall back. */
    fun wasDelivered(): Boolean = deliveredAny.get()

    /** Cancels the collect job.  Caller calls this AFTER the main path has
     *  drained its response stream — in-flight network calls are protected
     *  by `withContext(NonCancellable)` inside [handleAssistantDelta] so they
     *  still complete. */
    fun stop() {
        collectJob?.cancel()
    }

    private suspend fun handleAssistantDelta(event: AgentEvent.AssistantDelta) {
        val rawLen = event.text.length
        val stripped = HermesReplyMarkupStripper.strip(event.text).trim()
        GatewayFileLogger.i(
            tag,
            "streaming AssistantDelta busTagChatId=$busTagChatId wireChatId=$wireChatId " +
                "platform=$platform turn=${event.turn} rawLen=$rawLen " +
                "strippedLen=${stripped.length} isBlank=${stripped.isBlank()}"
        )
        if (stripped.isBlank()) return
        dispatchCalls.incrementAndGet()

        GatewayFileLogger.i(
            tag,
            "streaming dispatch turn=${event.turn} busTagChatId=$busTagChatId " +
                "wireChatId=$wireChatId platform=$platform textLen=${stripped.length}"
        )
        try {
            // dispatchOutgoing is an OkHttp call; if the caller cancels the
            // sidecar Job while a dispatch is in flight, the OkHttp request
            // gets cancelled too and we'd see ok=false even though the IM
            // API would have succeeded.  Wrap in NonCancellable so the
            // network call always runs to completion.
            val ok = withContext(NonCancellable) {
                dispatchOutgoing(platform, wireChatId, stripped, threadId)
            }
            if (ok) {
                dispatchSuccess.incrementAndGet()
                deliveredAny.set(true)
            } else {
                GatewayFileLogger.w(
                    tag,
                    "streaming dispatch failed turn=${event.turn} " +
                        "busTagChatId=$busTagChatId wireChatId=$wireChatId " +
                        "platform=$platform textLen=${stripped.length} reason=ok=false"
                )
            }
        } catch (e: CancellationException) {
            // structured-concurrency requires rethrow.  Note: the
            // NonCancellable above shields the OkHttp call itself, so we
            // only reach this catch on outer scope cancel after the call
            // has already returned (or before it started).
            throw e
        } catch (e: Throwable) {
            // Single-dispatch failure: log + swallow.  Don't abort the
            // agent loop.
            GatewayFileLogger.w(
                tag,
                "streaming dispatch threw turn=${event.turn} " +
                    "busTagChatId=$busTagChatId wireChatId=$wireChatId " +
                    "platform=$platform reason=${e.message}"
            )
        }
    }
}
