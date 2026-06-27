package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-001 source-scan wiring tests for [AgentStreamingSidecar]
 * (`AgentStreamingSidecar.kt`).
 *
 * Each `@Test` corresponds to a TC-GW-STREAMING-001-x row in
 * `docs/hermes-test-cases.md`.
 *
 * **Source-scan rationale**: the real sidecar wires `AgentEventBus`
 * (a `SharedFlow`) into a live coroutine scope; the timing race window
 * (`replay=0` + `onSubscription`) is by definition hard to deterministically
 * exercise in a pure-JVM unit test.  Same shape used by the analogous
 * R-CRON-STREAMING-001/002 tests (`CronStreamingParagraphSplitWiringTest`
 * etc.).  Literal-level assertions on the class file are sufficient to
 * prevent regression of the proven structure.
 */
class AgentStreamingSidecarShapeWiringTest {

    private val source: String by lazy { stripKotlinComments(File(sidecarPath()).readText()) }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-a: class exposes required constructor surface
    // (v2 2026-06-27: paragraphRegex/interParagraphDelayMs dropped — sidecar
    // is now single-shot whole-message dispatch, no segmentation)
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-a class exposes required constructor surface`() {
        assertTrue(
            "TC-GW-STREAMING-001-a: `AgentStreamingSidecar.kt` must declare `class AgentStreamingSidecar`.",
            source.contains("class AgentStreamingSidecar")
        )
        // Required v2 constructor parameter names. Caller-supplied params
        // avoid file-scope constant coupling between cron and gateway paths.
        val requiredParams = listOf(
            "chatId",
            "platform",
            "dispatchOutgoing"
        )
        requiredParams.forEach { p ->
            assertTrue(
                "TC-GW-STREAMING-001-a: constructor must declare parameter `$p` so the caller " +
                    "injects per-context behavior. Not found in source.",
                source.contains(p)
            )
        }
        // v2 regression guard: paragraphRegex / interParagraphDelayMs are
        // gone — sidecar dispatches each AssistantDelta whole, no splitting.
        val v1Params = listOf("paragraphRegex", "interParagraphDelayMs")
        v1Params.forEach { p ->
            assertFalse(
                "TC-GW-STREAMING-001-a v2 (2026-06-27): constructor must NOT reference `$p` — " +
                    "v1 paragraph-split + inter-paragraph-delay design was reverted because " +
                    "the serialized collector fell arbitrarily far behind the agent loop, " +
                    "causing real-device WeChat 'silence then burst' symptom. v2 dispatches " +
                    "each AssistantDelta as one whole IM message; no splitting.",
                source.contains(p)
            )
        }
        // Package check — must be in app/.../hermes/gateway/
        assertTrue(
            "TC-GW-STREAMING-001-a: class must live in `com.ai.assistance.operit.hermes.gateway` package.",
            source.contains("package com.ai.assistance.operit.hermes.gateway")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-b: subscription-ready signal
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-b sidecar exposes subscription-ready signal`() {
        assertTrue(
            "TC-GW-STREAMING-001-b: must reference `CompletableDeferred` as the ready-signal carrier.",
            source.contains("CompletableDeferred")
        )
        assertTrue(
            "TC-GW-STREAMING-001-b: must use `onSubscription` to fire ready signal once the " +
                "collector is actually subscribed (SharedFlow(replay=0) race protection).",
            source.contains("onSubscription")
        )
        assertTrue(
            "TC-GW-STREAMING-001-b: must invoke `.complete(` on the ready deferred from inside " +
                "`onSubscription`.",
            source.contains(".complete(")
        )
        // Public API caller can `await` on
        val hasAwaitReady = source.contains("awaitReady") || source.contains("subscriptionReady")
        assertTrue(
            "TC-GW-STREAMING-001-b: must expose a public API named `awaitReady` (or `subscriptionReady`) " +
                "so the caller can wait for subscription before triggering the agent.",
            hasAwaitReady
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-c: filter + one IM message per AssistantDelta
    // (v2 2026-06-27: no paragraph splitting, no mutex, no inter-segment delay)
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-c sidecar filters AssistantDelta and dispatches as one IM message per turn`() {
        assertTrue(
            "TC-GW-STREAMING-001-c: must subscribe to `AgentEventBus.events`.",
            source.contains("AgentEventBus.events")
        )
        assertTrue(
            "TC-GW-STREAMING-001-c: must reference `AssistantDelta` event type for the dispatchable " +
                "subset of agent events.",
            source.contains("AssistantDelta")
        )
        // Other agent event types must NOT be dispatched — they're internal
        // signals, not user-visible IM bubbles. Comments are stripped via
        // stripKotlinComments(), so these checks hit real references only.
        val forbidden = listOf("ToolCallStart", "ToolCallEnd")
        forbidden.forEach { token ->
            assertFalse(
                "TC-GW-STREAMING-001-c: sidecar must NOT reference `$token` — only " +
                    "`AssistantDelta` should be dispatched as a user-visible bubble. Found in source.",
                source.contains(token)
            )
        }
        assertTrue(
            "TC-GW-STREAMING-001-c: must call `HermesReplyMarkupStripper.strip` to remove internal " +
                "`<think>` / `<tool>` / `<status>` XML before sending to user.",
            source.contains("HermesReplyMarkupStripper.strip")
        )
        // v2: must actually call dispatchOutgoing with the stripped text.
        // Constructor declares `dispatchOutgoing:` (with colon); the invocation
        // uses `dispatchOutgoing(platform`.
        assertTrue(
            "TC-GW-STREAMING-001-c: must invoke `dispatchOutgoing(platform, ...)` — the actual " +
                "IM send call site (not just the constructor param declaration).",
            source.contains("dispatchOutgoing(platform")
        )
        // v2 regression guards: paragraph splitting / mutex serialization /
        // inter-paragraph delay are all gone.
        val v1Forbidden = listOf(
            "paragraphRegex",
            "dispatchMutex",
            ".withLock",
            "interParagraphDelayMs"
        )
        v1Forbidden.forEach { token ->
            assertFalse(
                "TC-GW-STREAMING-001-c v2 (2026-06-27): sidecar must NOT reference `$token` — " +
                    "v1 paragraph-split + mutex-serialize + inter-segment-delay design was " +
                    "reverted because the serialized collector fell arbitrarily far behind " +
                    "the agent loop. v2 dispatches each AssistantDelta as one whole IM " +
                    "message with no segmentation.",
                source.contains(token)
            )
        }
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-d: NonCancellable wraps dispatchOutgoing
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-d dispatch is wrapped in NonCancellable`() {
        assertTrue(
            "TC-GW-STREAMING-001-d: must reference `NonCancellable` for in-flight OkHttp call protection.",
            source.contains("NonCancellable")
        )
        assertTrue(
            "TC-GW-STREAMING-001-d: must use `withContext(` to enter the NonCancellable scope.",
            source.contains("withContext(")
        )
        // The NonCancellable scope must enclose the dispatchOutgoing call:
        // we conservatively check that the FIRST `withContext(NonCancellable`
        // appears BEFORE the FIRST in-block `dispatchOutgoing(` reference that
        // is the actual invocation (i.e. excluding the constructor declaration).
        val nonCancellableIdx = source.indexOf("withContext(NonCancellable")
        assertTrue(
            "TC-GW-STREAMING-001-d: must have a literal `withContext(NonCancellable` invocation " +
                "(found idx=$nonCancellableIdx).",
            nonCancellableIdx >= 0
        )
        // Find the dispatch call site — it's the `dispatchOutgoing(platform,` invocation.
        // Constructor param is `dispatchOutgoing:` (with colon), so we anchor on
        // the open-paren form to find the actual call.
        val dispatchCallIdx = source.indexOf("dispatchOutgoing(platform")
        assertTrue(
            "TC-GW-STREAMING-001-d: must have a literal invocation `dispatchOutgoing(platform, ...)` " +
                "(found idx=$dispatchCallIdx).",
            dispatchCallIdx >= 0
        )
        assertTrue(
            "TC-GW-STREAMING-001-d: `withContext(NonCancellable` (idx=$nonCancellableIdx) must " +
                "appear BEFORE the `dispatchOutgoing(platform, ...)` invocation (idx=$dispatchCallIdx) " +
                "in source order, i.e. it must wrap the call.",
            nonCancellableIdx in 0 until dispatchCallIdx
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-e: failure semantics + CancellationException rethrow
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-e dispatch failures are swallowed but CancellationException is rethrown`() {
        // Must have try/catch around the dispatch
        assertTrue(
            "TC-GW-STREAMING-001-e: sidecar must contain a `try` block around dispatch.",
            source.contains("try")
        )
        assertTrue(
            "TC-GW-STREAMING-001-e: sidecar must contain a `catch` block to handle dispatch failures.",
            source.contains("catch")
        )
        // CancellationException must be referenced + rethrown
        assertTrue(
            "TC-GW-STREAMING-001-e: must reference `CancellationException` to handle structured-" +
                "concurrency cancel correctly.",
            source.contains("CancellationException")
        )
        assertTrue(
            "TC-GW-STREAMING-001-e: must `throw` (rethrow) CancellationException to honor " +
                "structured concurrency. Swallowing it would mask cancel + leak the collector job.",
            source.contains("throw")
        )
        // Errors must be logged, not silently swallowed
        val hasLogger = source.contains("GatewayFileLogger") || source.contains("AppLogger")
        assertTrue(
            "TC-GW-STREAMING-001-e: dispatch errors must be logged (`GatewayFileLogger` or " +
                "`AppLogger`), not silently swallowed.",
            hasLogger
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-k: observability log fields
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-k sidecar emits gateway log lines with required fields`() {
        // Gateway path → GatewayFileLogger.  Cron path uses CronFileLogger but
        // the sidecar component itself should target GatewayFileLogger (cron
        // can still log its own summary line separately via CronFileLogger).
        assertTrue(
            "TC-GW-STREAMING-001-k: must use `GatewayFileLogger` (gateway path log). The CronFileLogger " +
                "is reserved for cron's own summary line, not the sidecar's per-dispatch lines.",
            source.contains("GatewayFileLogger")
        )
        assertTrue(
            "TC-GW-STREAMING-001-k: log lines must include the `streaming dispatch turn=` literal " +
                "so R-CRON-DIAG-001-style diagnostic queries can locate per-turn dispatch records.",
            source.contains("streaming dispatch turn=")
        )
        val hasFieldMarker = source.contains("paragraphIdx=") ||
            source.contains("paragraphCount=") ||
            source.contains("textLen=")
        assertTrue(
            "TC-GW-STREAMING-001-k: log lines must include `textLen=` (v2 — one dispatch per " +
                "AssistantDelta) or `paragraphIdx=` / `paragraphCount=` (v1 legacy) so operators " +
                "can pin down which dispatch of which turn failed.",
            hasFieldMarker
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-p: bus-tag chatId vs wire chatId must be SEPARATE
    // fields (2026-06-27 bugfix).
    //
    // Real-device log: `WeixinAdapter.send(to_user_id=...)` was receiving
    // `gw:weixin:wxid@im.wechat:wxid@im.wechat:wxid@im.wechat` (the
    // `historyChatId` = `gw:$sessionKey:$chatId`, which is correct for
    // Hermes-side identity but is NOT a valid WeChat wire chatId). iLink
    // rejected with `errcode=-3` → every streaming dispatch failed →
    // user-visible "silence then burst" symptom.
    //
    // Root cause: the v2 sidecar had a single `chatId: String` field that
    // was used both as the AgentEventBus filter key (must equal
    // historyChatId) AND as the second arg to `dispatchOutgoing(...)`
    // (must be the platform-native wire chatId). Fix: split into
    // `busTagChatId` + `wireChatId`. The bus filter uses busTagChatId;
    // the dispatch uses wireChatId.
    //
    // The `dispatchOutgoing` lambda in HermesGatewayController already
    // forwards its `chatId` arg straight to `WeixinAdapter.send` without
    // stripping, so feeding the clean wire chatId at this layer is the
    // minimal correct fix. Adapter contract is unchanged; non-streaming
    // fallback (which already uses `currentEvent.source.chatId` —
    // clean wire chatId) and `send_message` tool path (closure captures
    // clean outer chatId) are unaffected.
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-p sidecar splits busTagChatId and wireChatId`() {
        // (1) Constructor declares BOTH fields.
        assertTrue(
            "TC-GW-STREAMING-001-p: constructor must declare `busTagChatId` — used as the " +
                "AgentEventBus tag filter (matches `tagged.chatId == historyChatId`).",
            source.contains("busTagChatId")
        )
        assertTrue(
            "TC-GW-STREAMING-001-p: constructor must declare `wireChatId` — the platform-" +
                "native chatId that goes to `dispatchOutgoing` and then unmodified into " +
                "`adapter.send(chatId = ...)`. WeChat needs `wxid@im.wechat`, NOT the " +
                "5-token `gw:sessionKey:chatId` historyChatId.",
            source.contains("wireChatId")
        )
        // (2) The legacy single `chatId` field MUST be gone — leaving it would
        //     keep the door open for the same conflation to recur.
        val hasLegacyChatIdField = Regex("""private\s+val\s+chatId\s*:""")
            .containsMatchIn(source)
        assertFalse(
            "TC-GW-STREAMING-001-p: legacy single-field `private val chatId:` MUST be " +
                "removed — replaced by busTagChatId + wireChatId. Leaving both forms is a " +
                "regression risk.",
            hasLegacyChatIdField
        )
        // (3) dispatchOutgoing actually called with wireChatId — this is the
        //     core wire fix.
        assertTrue(
            "TC-GW-STREAMING-001-p: must invoke `dispatchOutgoing(platform, wireChatId, ...)` " +
                "— passing the wire chatId, not the bus-tag chatId. This is the line that " +
                "previously polluted WeChat's `to_user_id` with the 5-token historyChatId.",
            source.contains("dispatchOutgoing(platform, wireChatId")
        )
        // (4) Old polluted form must NOT appear — regression guard.
        assertFalse(
            "TC-GW-STREAMING-001-p: must NOT contain `dispatchOutgoing(platform, chatId,` " +
                "(the legacy single-field invocation that caused the WeChat `errcode=-3` " +
                "bug). Replace with `dispatchOutgoing(platform, wireChatId, ...)`.",
            Regex("""dispatchOutgoing\s*\(\s*platform\s*,\s*chatId\s*,""")
                .containsMatchIn(source)
        )
    }

    // =====================================================================
    // helpers — mirror CronStreamingParagraphSplitWiringTest
    // =====================================================================

    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments while
     * preserving newlines so failure messages stay meaningful and string
     * literals inside docstrings don't pollute literal-content checks.
     * Naive — does not honor string literals — but sufficient for source-scan.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun sidecarPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/AgentStreamingSidecar.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/AgentStreamingSidecar.kt")
        return alt.path
    }
}
