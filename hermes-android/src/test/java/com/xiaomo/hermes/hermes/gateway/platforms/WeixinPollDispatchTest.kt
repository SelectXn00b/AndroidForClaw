package com.xiaomo.hermes.hermes.gateway.platforms

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.Platform
import com.xiaomo.hermes.hermes.gateway.PlatformConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * R-GATEWAY-038 — Weixin adapter inbound dispatch must NOT block the poll loop.
 *
 * Bugfix story (no R touch — see CLAUDE.md §0.1):
 *  - User reported on HEAD 6ce44300 that mid-turn 插话 doesn't work over wechat:
 *    sending a 2nd message while agent is still running → no busy ack, agent
 *    finishes the 1st instruction completely before reading the 2nd.
 *  - Root cause: `Weixin.kt:_runPollLoop` calls `_handleInbound(msg)` directly
 *    inside the for-loop, which awaits `handleMessage(event)` → GatewayRunner
 *    `_handleMessage` → agentRunner end-to-end on the **same poll coroutine**.
 *    The poll coroutine never returns to its `getUpdates` long-poll until the
 *    agent turn finishes, so the 2nd message stays buffered on the iLink
 *    server side and never reaches GatewayRunner's busy branch.
 *  - Reference correct contract: `Telegram.kt:528-547` (per-chat Channel +
 *    `scope.launch`) and `Feishu.kt:782-795` (`scope.launch { handleMessage }`,
 *    with a comment that explicitly names this contract:
 *      "Messages are dispatched concurrently so that a second message can
 *       reach GatewayRunner's busy-guard and trigger the interrupt mechanism
 *       while the first is still being processed by the agent.")
 *
 * This file holds two TCs that close the bug:
 *   - TC-GATEWAY-038-d: source-scan — `Weixin.kt` inbound dispatch must not
 *     directly await `_handleInbound(msg)` in the poll for-loop. It must
 *     route through a per-chat Channel queue or `scope.launch`.
 *   - TC-GATEWAY-038-e: runtime — two inbound msgs from the same chat must
 *     produce two **overlapping** `messageHandler` invocations (the second
 *     enters before the first releases), proving the dispatch is concurrent.
 */
class WeixinPollDispatchTest {

    // ────────────────────────────────────────────────────────────────────
    // TC-GATEWAY-038-d: source-scan guard
    // ────────────────────────────────────────────────────────────────────

    /**
     * TC-GATEWAY-038-d: `Weixin.kt` must not dispatch inbound msgs by direct
     * suspending await on `_handleInbound(msg)` from the poll for-loop.
     *
     * Why source-scan: the runtime test (TC-e) is more authoritative but the
     * fastest defense against future regression is a structural assertion
     * that mirrors how `Run.kt` busy-branch is guarded by TC-GATEWAY-038-b.
     */
    @Test
    fun `TC-GATEWAY-038-d weixin inbound dispatch decouples from poll loop`() {
        val srcRoots = listOf(
            java.io.File("src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
            java.io.File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/platforms/Weixin.kt"),
        )
        val src = srcRoots.firstOrNull { it.exists() }?.readText()
            ?: error("Cannot locate Weixin.kt; cwd=${java.io.File(".").absolutePath}")

        // 1. Locate the poll loop body.
        val pollIdx = src.indexOf("private suspend fun _runPollLoop")
        assertTrue("_runPollLoop must exist in Weixin.kt", pollIdx >= 0)
        val pollBodyEnd = run {
            // Find the matching end brace of _runPollLoop by paren-balanced scan
            // starting from the first `{` after pollIdx. Acceptable approximation:
            // scan up to the next `private suspend fun _handleInbound`.
            val nextFunIdx = src.indexOf("private suspend fun _handleInbound", pollIdx)
            if (nextFunIdx > pollIdx) nextFunIdx else (pollIdx + 4000)
        }
        val pollBody = src.substring(pollIdx, pollBodyEnd)

        // 2. The for-loop over msgs must NOT contain a direct same-stack
        //    `_handleInbound(msg)` call (i.e. no `_handleInbound(` that is
        //    NOT preceded by `launch {` or wrapped in a queue helper).
        //
        //    Strategy: if the substring contains `_handleInbound(` then it
        //    must also contain a concurrent dispatch token between the for-
        //    loop and that call. We assert presence of a per-chat dispatch
        //    structure (any of the 3 forms below) within the poll body:
        val concurrentTokens = listOf(
            "_queueForProcessing(",       // Telegram-style per-chat Channel queue
            "scope.launch",                // Feishu-style direct launch
            "Channel<",                    // anyone declaring a Channel for inbound
        )
        val hasConcurrent = concurrentTokens.any { pollBody.contains(it) }
        assertTrue(
            "Weixin _runPollLoop must dispatch inbound messages concurrently — " +
            "expected one of $concurrentTokens inside the poll body to decouple " +
            "msg dispatch from the long-poll coroutine. Without this, GatewayRunner's " +
            "busy default-path (R-GATEWAY-038) cannot trigger because the 2nd " +
            "message never reaches `_handleMessage` while the 1st is still in-flight. " +
            "Contract reference: Telegram.kt:528-547 + Feishu.kt:782-795.",
            hasConcurrent,
        )

        // 3. Specifically: the for-loop body that iterates `msgs` must NOT
        //    have `_handleInbound(msg)` as a bare same-stack await with no
        //    surrounding launch/queue.
        val forLoopIdx = pollBody.indexOf("for (i in 0 until msgs.length())")
        assertTrue("poll loop must iterate msgs via `for (i in 0 until msgs.length())`", forLoopIdx >= 0)
        // Slice the for-loop body (heuristic — until next `} catch` or end of poll body)
        val forBodyStart = pollBody.indexOf("{", forLoopIdx)
        val forBodyEnd = pollBody.indexOf("} catch", forBodyStart).let {
            if (it >= 0) it else pollBody.length
        }
        val forBody = pollBody.substring(forBodyStart, forBodyEnd)

        // The for-body MUST NOT contain a bare `_handleInbound(msg)` line that
        // is not wrapped in launch / queue. The simplest assertion: the for-
        // body must contain at least one of the concurrent tokens.
        val forBodyConcurrent = concurrentTokens.any { forBody.contains(it) }
        assertTrue(
            "Inside `for (i in 0 until msgs.length())`, dispatch to _handleInbound " +
            "must be wrapped in launch/queue (one of $concurrentTokens). Bare same- " +
            "stack `_handleInbound(msg)` await blocks the poll loop and breaks " +
            "R-GATEWAY-038 mid-turn 插话 over wechat.",
            forBodyConcurrent,
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // TC-GATEWAY-038-e: runtime overlap guard
    // ────────────────────────────────────────────────────────────────────

    /**
     * TC-GATEWAY-038-e: Two inbound messages from the same `from_user_id`
     * must produce **overlapping** `messageHandler` invocations.
     *
     * Setup:
     *   - Build WeixinAdapter with mocked Context, no real iLink credentials
     *     (we never call connect()).
     *   - Install a `messageHandler` that:
     *       * records its entry timestamp
     *       * suspends on a CompletableDeferred for ~1s for the 1st event
     *       * returns immediately for the 2nd event
     *   - Reflectively invoke `_handleInbound(msgJson)` twice in rapid
     *     succession with the **same** `from_user_id`.
     *
     * Expectation:
     *   - Both `messageHandler` invocations are entered within < 200ms of
     *     each other (i.e. the 2nd enters before the 1st releases).
     *
     * If `_handleInbound` is awaited serially (current bug), the 2nd entry
     * timestamp would be ~1s after the 1st, causing this assertion to fail.
     */
    @Test
    @Ignore("TC-GATEWAY-038-e drives _handleInbound via reflection which bypasses _runPollLoop, so the production scope.launch wrap is not exercised. TC-d source-scan is the authoritative contract guard. Re-enable when an integration test rig with a fake iLink server is available.")
    fun `TC-GATEWAY-038-e weixin per-chat dispatch overlaps in time`() = runBlocking {
        val ctx: Context = mock()
        val cfg = PlatformConfig(
            platform = Platform.WEIXIN,
            enabled = true,
            extra = mapOf(
                "account_id" to "test_account",
                "login_token" to "test_token",
            ),
        )
        val adapter = WeixinAdapter(ctx, cfg)

        val firstEntered = CompletableDeferred<Long>()
        val secondEntered = CompletableDeferred<Long>()
        val firstRelease = CompletableDeferred<Unit>()
        val handlerInvocations = java.util.concurrent.atomic.AtomicInteger(0)

        adapter.messageHandler = { event ->
            val now = System.nanoTime()
            handlerInvocations.incrementAndGet()
            // Identify which msg by message_id (string form of the original Long)
            when (event.message_id) {
                "1" -> {
                    firstEntered.complete(now)
                    // Block until test releases — simulates agent turn in flight
                    firstRelease.await()
                }
                "2" -> {
                    secondEntered.complete(now)
                }
                else -> { /* ignore */ }
            }
        }

        fun buildInbound(msgId: Long): JSONObject {
            return JSONObject().apply {
                put("message_type", MSG_TYPE_USER)
                put("from_user_id", "user_a")
                put("client_id", "")  // empty so dedup doesn't kick in
                put("message_id", msgId)
                put("context_token", "ctx_$msgId")
                put("item_list", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("type", ITEM_TEXT)
                        put("text_item", JSONObject().apply { put("text", "msg-$msgId") })
                    }
                ))
            }
        }

        // Reflectively invoke `_handleInbound` (private suspend) twice in
        // quick succession from the same coroutine — mimicking the for-loop
        // in `_runPollLoop`. If the production code awaits `_handleInbound`
        // serially, the 2nd call won't even be entered until the 1st handler
        // releases.
        //
        // Note: when the bug is present, the test will simply time out after
        // 5s (firstEntered ok, secondEntered never completes), and we treat
        // the timeout as RED.
        val handleInbound = WeixinAdapter::class.java.declaredMethods.first { it.name == "_handleInbound" }
        handleInbound.isAccessible = true

        // Drive both invocations from a fresh coroutine each (mimic launch)
        // — but we cannot patch production code from here. The whole point
        // of TC-d is to make production wrap in launch; once that's done,
        // calling `_handleInbound` from the same coroutine becomes irrelevant
        // because dispatch is delegated to a sub-coroutine inside the for body.
        //
        // For TC-e to be a valid runtime probe, we drive it via the public
        // path: simulate one tick of the poll loop by reflection-invoking
        // `_handleInbound` for each msg. The fix is required to make the
        // adapter route through a non-blocking dispatch internally.
        val driver = CoroutineScope(Dispatchers.Default + SupervisorJob())
        // Kick off both inbound dispatches from independent coroutines so
        // the test fixture itself doesn't serialize them; we want to verify
        // adapter behavior, not test driver behavior.
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val job1: Job = driver.launch {
            try { invokeSuspendReflectively(adapter, handleInbound, buildInbound(1L)) }
            catch (t: Throwable) { errors += t }
        }
        val job2: Job = driver.launch {
            // Tiny stagger so the 1st reaches handler first
            delay(20)
            try { invokeSuspendReflectively(adapter, handleInbound, buildInbound(2L)) }
            catch (t: Throwable) { errors += t }
        }

        // Wait for first handler to enter (proves the path works at all).
        val t1 = withTimeoutOrNull(3000) { firstEntered.await() }
            ?: error(
                "First messageHandler never entered. handlerInvocations=${handlerInvocations.get()}, " +
                "errors=$errors. Adapter dispatch broken upstream of the bug under test."
            )

        // The crux: the second handler must enter *while the first is still
        // blocked*, i.e. within a small window — NOT after firstRelease.
        val t2 = withTimeoutOrNull(2000) { secondEntered.await() }

        // Now release the first handler so the test can finish cleanly.
        firstRelease.complete(Unit)
        job1.join()
        job2.join()

        assertTrue(
            "Second messageHandler must enter before the first releases — got " +
            "t2=$t2 (null = timed out, meaning serial dispatch). This is the " +
            "exact symptom users reported: agent finishes 1st instruction " +
            "before reading 2nd. Fix: route inbound dispatch through " +
            "scope.launch / per-chat Channel like Telegram.kt:528-547.",
            t2 != null,
        )
        val deltaMs = (t2!! - t1) / 1_000_000L
        assertTrue(
            "Second messageHandler must enter within 500ms of the first (got " +
            "${deltaMs}ms). Larger deltas indicate dispatch is still serialized.",
            deltaMs < 500,
        )

        adapter.disconnect()
    }

    /** Reflectively invoke a `private suspend fun` and await its completion. */
    private suspend fun invokeSuspendReflectively(
        target: Any,
        method: java.lang.reflect.Method,
        arg: Any,
    ) {
        val completion = CompletableDeferred<Any?>()
        val continuation = object : kotlin.coroutines.Continuation<Any?> {
            override val context: kotlin.coroutines.CoroutineContext
                get() = kotlin.coroutines.EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) {
                if (result.isSuccess) completion.complete(result.getOrNull())
                else completion.completeExceptionally(result.exceptionOrNull()!!)
            }
        }
        method.invoke(target, arg, continuation)
        completion.await()
    }
}
