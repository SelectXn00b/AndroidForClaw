package com.ai.assistance.operit.hermes.gateway

import com.xiaomo.hermes.hermes.AgentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * R-GW-STREAMING-001: common streaming-sidecar component for the per-turn IM dispatch
 * path.  Mirrors the inline sidecar that lives in `CronAgentRunner.run()` (R-CRON-
 * STREAMING-001/002), but factored out so both the cron path and the normal IM
 * gateway path (`HermesGatewayController.runHermesAgent`) can reuse the same
 * proven shape.
 *
 * **What it does**: subscribes to [AgentEventBus.events], filters [AgentEvent.AssistantDelta]
 * tagged with `chatId == this.chatId`, strips Hermes XML markup via
 * [HermesReplyMarkupStripper.strip], splits the stripped text into paragraphs by
 * [paragraphRegex] (caller-supplied; cron passes `\R\s*\R+`), and dispatches each
 * paragraph sequentially via the caller-supplied [dispatchOutgoing] callback under
 * a single [Mutex] with `withContext(NonCancellable)` guard around the network
 * call.  Inter-paragraph gap is [interParagraphDelayMs] (caller-supplied; cron uses
 * 200 ms).
 *
 * **What it deliberately does NOT do**:
 *  - Persist anything to app history / memory / chat note.  Caller (cron or gateway)
 *    still does its own bookkeeping after the agent loop finishes.
 *  - Subscribe to [AgentEvent.Thinking] / `ToolCallStart` / `ToolCallEnd` / `Final` /
 *    `Error`.  Only `AssistantDelta` is dispatched as a user-visible IM bubble.
 *  - Cancel itself when the agent loop returns.  Caller owns the [Job] returned
 *    from [start] and cancels via [stop] after main path is done.
 *
 * **Subscription-race protection** (R-CRON-STREAMING-001-c): [AgentEventBus.events]
 * is a `SharedFlow(replay=0)`, so the agent's first `AssistantDelta` (emitted
 * synchronously from inside `enhancedService.sendMessage(...)`) can race ahead of
 * `flow.collect { ... }` registering its subscription.  We use a
 * [CompletableDeferred] + `onSubscription { ready.complete(Unit) }` so the caller
 * can [awaitReady] before triggering the agent.
 *
 * **In-flight cancel guard** (R-CRON-STREAMING-001 / TC-CRON-STREAMING-g): the
 * caller cancels the sidecar [Job] after main collect finishes, but an
 * in-flight `dispatchOutgoing(...)` (OkHttp request) would also be cancelled,
 * surfacing as `ok=false`.  We wrap each dispatch in `withContext(NonCancellable)`
 * so the OkHttp call always runs to completion.
 *
 * **Failure semantics** (TC-GW-STREAMING-001-e): a single failed paragraph
 * dispatch logs to [GatewayFileLogger] but does **not** throw, abort the agent
 * loop, or stop subsequent paragraphs.  [CancellationException] is always
 * rethrown — structured-concurrency invariant.
 *
 * **Observability** (TC-GW-STREAMING-001-k): each dispatch logs to
 * [GatewayFileLogger] with `streaming dispatch turn=` / `paragraphIdx=` /
 * `paragraphCount=` fields, mirroring R-CRON-STREAMING field names so the same
 * R-CRON-DIAG-001-style diagnostic queries work on `gateway.log`.
 *
 * @property chatId tagged chat id on [AgentEventBus] — gateway path uses
 *   `"gw:$sessionKey:$chatId"`, cron path uses `"cron-$jobId"` (or whatever
 *   `taskId` the agent loop is started with).
 * @property platform IM platform identifier (e.g. `"weixin"`, `"telegram"`).
 *   Passed straight through to [dispatchOutgoing].
 * @property dispatchOutgoing caller-supplied IM send callback.  Signature is
 *   `(platform, chatId, text, threadId?) -> ok`; threadId is `null` for
 *   non-threaded platforms.  Returns `true` on success.  Already-existing
 *   `HermesGatewayController.dispatchOutgoing` matches.
 * @property paragraphRegex blank-line regex used to split the AssistantDelta
 *   text.  Caller supplies — cron uses `Regex("""\R\s*\R+""")`, gateway can
 *   use the same.
 * @property interParagraphDelayMs gap between sequential paragraph dispatches,
 *   in ms.  Caller supplies — cron uses 200 ms.
 */
class AgentStreamingSidecar(
    private val chatId: String,
    private val platform: String,
    private val dispatchOutgoing: suspend (String, String, String, String?) -> Boolean,
    private val paragraphRegex: Regex,
    private val interParagraphDelayMs: Long,
    private val threadId: String? = null,
    private val tag: String = "AgentStreamingSidecar",
) {

    /** subscription-ready signal — see class doc. */
    private val ready: CompletableDeferred<Unit> = CompletableDeferred()

    /** records whether ANY paragraph has been successfully dispatched. Caller uses
     *  this to decide whether to return `STREAMING_DELIVERED_SENTINEL` (skip the
     *  fallback `deliverText` in `Run.kt`) vs returning the original text. */
    private val deliveredAny = AtomicBoolean(false)

    /** dispatchMutex — serializes paragraph dispatches so we never have two
     *  concurrent IM sends to the same chat. */
    private val dispatchMutex: Mutex = Mutex()

    /** observability counters — caller can read at the end and log. */
    val totalEvents = AtomicInteger(0)
    val chatIdMatched = AtomicInteger(0)
    val assistantDeltas = AtomicInteger(0)
    val dispatchCalls = AtomicInteger(0)
    val paragraphDispatches = AtomicInteger(0)
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
                        val match = tagged.chatId == this@AgentStreamingSidecar.chatId
                        if (match) chatIdMatched.incrementAndGet()
                        match
                    }
                    .collect { tagged ->
                        val event = tagged.event
                        if (event is AgentEvent.AssistantDelta) {
                            assistantDeltas.incrementAndGet()
                            handleAssistantDelta(event)
                        }
                        // Thinking / ToolCallStart / ToolCallEnd / Final / Error
                        // are intentionally NOT dispatched as IM bubbles —
                        // they're internal agent loop signals, not user-visible
                        // turn output.
                    }
            } catch (e: CancellationException) {
                // structured-concurrency requires rethrow
                throw e
            } catch (e: Throwable) {
                GatewayFileLogger.w(
                    tag,
                    "sidecar collect threw chatId=$chatId platform=$platform " +
                        "reason=${e.message}"
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

    /** True if at least one paragraph has been successfully dispatched.  Caller
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

    /** For caller drain logic: tryLock the dispatchMutex — if it succeeds the
     *  sidecar has no in-flight `dispatchOutgoing(...)` running.  Mirrors the
     *  cron path's TC-CRON-STREAMING-g drain-window check. */
    fun tryDrain(): Boolean {
        val locked = dispatchMutex.tryLock()
        if (locked) dispatchMutex.unlock()
        return locked
    }

    private suspend fun handleAssistantDelta(event: AgentEvent.AssistantDelta) {
        val rawLen = event.text.length
        val stripped = HermesReplyMarkupStripper.strip(event.text).trim()
        GatewayFileLogger.i(
            tag,
            "streaming AssistantDelta chatId=$chatId platform=$platform " +
                "turn=${event.turn} rawLen=$rawLen strippedLen=${stripped.length} " +
                "isBlank=${stripped.isBlank()}"
        )
        if (stripped.isBlank()) return
        dispatchCalls.incrementAndGet()

        val paragraphs = stripped.split(paragraphRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        GatewayFileLogger.i(
            tag,
            "streaming AssistantDelta chatId=$chatId platform=$platform " +
                "turn=${event.turn} paragraphCount=${paragraphs.size}"
        )

        paragraphs.forEachIndexed { idx, paragraph ->
            dispatchMutex.withLock {
                try {
                    GatewayFileLogger.i(
                        tag,
                        "streaming dispatch turn=${event.turn} chatId=$chatId " +
                            "platform=$platform paragraphIdx=$idx/${paragraphs.size} " +
                            "textLen=${paragraph.length}"
                    )
                    // TC-GW-STREAMING-001-d / TC-CRON-STREAMING-g (2026-06-25):
                    // dispatchOutgoing is an OkHttp call; if the caller cancels
                    // the sidecar Job while a dispatch is in flight, the OkHttp
                    // request gets cancelled too and we'd see ok=false even
                    // though the IM API would have succeeded.  Wrap in
                    // NonCancellable so the network call always runs to
                    // completion.
                    val ok = withContext(NonCancellable) {
                        dispatchOutgoing(platform, chatId, paragraph, threadId)
                    }
                    paragraphDispatches.incrementAndGet()
                    if (ok) {
                        dispatchSuccess.incrementAndGet()
                        deliveredAny.set(true)
                    } else {
                        GatewayFileLogger.w(
                            tag,
                            "streaming dispatch failed turn=${event.turn} " +
                                "chatId=$chatId platform=$platform " +
                                "paragraphIdx=$idx/${paragraphs.size} reason=ok=false"
                        )
                    }
                } catch (e: CancellationException) {
                    // structured-concurrency requires rethrow.  Note: the
                    // NonCancellable above shields the OkHttp call itself,
                    // so we only reach this catch on outer scope cancel after
                    // the call has already returned (or before it started).
                    throw e
                } catch (e: Throwable) {
                    // Single-paragraph failure: log + swallow.  Don't abort
                    // the agent loop, don't stop subsequent paragraphs.
                    GatewayFileLogger.w(
                        tag,
                        "streaming dispatch threw turn=${event.turn} " +
                            "chatId=$chatId platform=$platform " +
                            "paragraphIdx=$idx/${paragraphs.size} reason=${e.message}"
                    )
                }
            }
            // TC-CRON-STREAMING-j parity: inter-paragraph gap to avoid WeChat
            // short-window rate-limiting / merging.  Skip after the last
            // paragraph.
            if (idx < paragraphs.size - 1) {
                delay(interParagraphDelayMs)
            }
        }
    }
}
