package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-001 source-scan wiring tests for the
 * `HermesGatewayController.runHermesAgent` side of the streaming sidecar
 * integration.
 *
 * Each `@Test` corresponds to a TC-GW-STREAMING-001-x row in
 * `docs/hermes-test-cases.md`:
 *  - TC-GW-STREAMING-001-h → sidecar constructed + started + awaited + stopped
 *    AND `wasDelivered()` drives `STREAMING_DELIVERED_SENTINEL` return
 *    (TC-h covers both wiring and sentinel return per test-cases doc).
 *  - TC-GW-STREAMING-001-i → group chat (`@chatroom`) skips sidecar
 *  - TC-GW-STREAMING-001-j → bilingual `MULTI_MESSAGE_HINT` injected via
 *    `SystemPromptComposeHook` (Python upstream's `ephemeral_system_prompt`
 *    idiom — hint appended to `role: "system"`, NOT folded into user message
 *    text. The v1 implementation that wrapped `messageTextOverride =
 *    buildString { MULTI_MESSAGE_HINT + text }` was an architectural error:
 *    it persisted the hint to Room as a `ChatMessage(sender="user")` and
 *    polluted the conversation across turns. v2 uses
 *    `PromptHookRegistry.registerSystemPromptComposeHook` with a
 *    `finally { unregister }` cleanup. v2.1 (2026-06-26) drops the
 *    `context.chatId == historyChatId` filter — `SystemPromptConfig`
 *    doesn't thread chatId into `PromptHookContext`, so the filter always
 *    rejected and the hint was never injected. The hook now fires on every
 *    compose pass during the gateway's `try { ... finally }` window.)
 *
 * **Source-scan rationale**: `runHermesAgent` integrates with Room DB,
 * ChatRuntimeHolder, `core.sendUserMessage(...)`, `core.activeStreamingChatIds`,
 * MemoryLibrary, hookPipeline, and more — driving an actual behavior test
 * needs a full Android instrumentation harness. Literal-level wiring
 * assertions are sufficient to lock in the proven shape and prevent
 * regression.
 */
class HermesGatewayControllerStreamingWiringTest {

    private val source: String by lazy { stripKotlinComments(File(controllerPath()).readText()) }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-h: sidecar wired into runHermesAgent
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-h runHermesAgent constructs and drives AgentStreamingSidecar`() {
        assertTrue(
            "TC-GW-STREAMING-001-h: `HermesGatewayController` must reference " +
                "`AgentStreamingSidecar` so the per-turn streaming behavior is wired " +
                "into the normal IM gateway path (not just cron).",
            source.contains("AgentStreamingSidecar")
        )
        // Construction
        assertTrue(
            "TC-GW-STREAMING-001-h: must construct `AgentStreamingSidecar(` with the " +
                "caller-supplied parameters (chatId, platform, dispatchOutgoing, " +
                "paragraphRegex, interParagraphDelayMs).",
            source.contains("AgentStreamingSidecar(")
        )
        // Lifecycle: start + awaitReady + stop
        assertTrue(
            "TC-GW-STREAMING-001-h: must call `.start(` to launch the sidecar collect job " +
                "inside the controller's coroutine scope.",
            source.contains(".start(")
        )
        assertTrue(
            "TC-GW-STREAMING-001-h: must call `awaitReady` BEFORE triggering the agent so " +
                "the SharedFlow(replay=0) subscription is registered before the first " +
                "`AssistantDelta` is emitted (race window protection).",
            source.contains("awaitReady")
        )
        assertTrue(
            "TC-GW-STREAMING-001-h: must call `.stop(` on the sidecar after the agent loop " +
                "finishes so the collector job doesn't outlive the turn.",
            source.contains(".stop(")
        )
        // awaitReady must come BEFORE core.sendUserMessage (subscription must be
        // registered before the agent runs and emits AssistantDelta).
        val awaitIdx = source.indexOf("awaitReady")
        val sendUserMsgIdx = source.indexOf("core.sendUserMessage")
        assertTrue(
            "TC-GW-STREAMING-001-h: `awaitReady` (idx=$awaitIdx) must appear in source " +
                "BEFORE the first `core.sendUserMessage` call (idx=$sendUserMsgIdx) so " +
                "the subscription is guaranteed registered before the agent runs.",
            awaitIdx in 0 until sendUserMsgIdx
        )
        // v2 (2026-06-27, TC-001-m/n/o REVERTED): the v1 caller-supplied
        // STREAMING_PARAGRAPH_REGEX / STREAMING_INTER_PARAGRAPH_DELAY_MS
        // constants are gone — sidecar no longer splits or delays. Regression
        // guard: those constants must NOT remain in the controller source.
        assertFalse(
            "TC-GW-STREAMING-001-h v2 (2026-06-27): controller must NOT reference " +
                "`STREAMING_PARAGRAPH_REGEX` — v1 paragraph-split design was reverted " +
                "(see TC-GW-STREAMING-001-c v2). Found leftover constant.",
            source.contains("STREAMING_PARAGRAPH_REGEX")
        )
        assertFalse(
            "TC-GW-STREAMING-001-h v2 (2026-06-27): controller must NOT reference " +
                "`STREAMING_INTER_PARAGRAPH_DELAY_MS` — v1 inter-segment-delay design " +
                "was reverted (see TC-GW-STREAMING-001-m REVERTED). Found leftover " +
                "constant.",
            source.contains("STREAMING_INTER_PARAGRAPH_DELAY_MS")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-i: group chats (@chatroom) skip the sidecar
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-i group chats skip the streaming sidecar`() {
        assertTrue(
            "TC-GW-STREAMING-001-i: must check for the `@chatroom` suffix to detect " +
                "WeChat group chats (user explicit requirement 2026-06-25: 群聊不需要).",
            source.contains("@chatroom")
        )
        // The @chatroom guard must come BEFORE sidecar construction so we don't
        // pay the construction cost for a group chat we're going to skip.
        val groupIdx = source.indexOf("@chatroom")
        val ctorIdx = source.indexOf("AgentStreamingSidecar(")
        assertTrue(
            "TC-GW-STREAMING-001-i: `@chatroom` skip check (idx=$groupIdx) must appear " +
                "in source BEFORE the `AgentStreamingSidecar(` constructor call " +
                "(idx=$ctorIdx) so we short-circuit before construction.",
            groupIdx in 0 until ctorIdx
        )
        // Sidecar variable must be nullable so the skip path can hold `null`
        // and the post-loop code can null-check before calling .wasDelivered().
        assertTrue(
            "TC-GW-STREAMING-001-i: sidecar reference must be nullable (`AgentStreamingSidecar?`) " +
                "so the @chatroom skip path can hold `null` and post-loop code can null-check.",
            source.contains("AgentStreamingSidecar?")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-h (sentinel return half):
    // wasDelivered() drives STREAMING_DELIVERED_SENTINEL
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-h wasDelivered gates STREAMING_DELIVERED_SENTINEL return`() {
        assertTrue(
            "TC-GW-STREAMING-001-h (sentinel): must call `wasDelivered(` on the sidecar after the " +
                "agent loop finishes to decide whether at least one paragraph was " +
                "successfully streamed.",
            source.contains("wasDelivered(")
        )
        assertTrue(
            "TC-GW-STREAMING-001-h (sentinel): must reference `GatewayRunner.STREAMING_DELIVERED_SENTINEL` " +
                "as the return value when the sidecar successfully streamed, so " +
                "`GatewayRunner._handleMessage` knows to skip its fallback `deliverText`.",
            source.contains("STREAMING_DELIVERED_SENTINEL")
        )
        // wasDelivered must appear before the sentinel return — i.e. it's the gate,
        // not a no-op call after the fact.
        val wasDelIdx = source.indexOf("wasDelivered(")
        val sentinelIdx = source.indexOf("STREAMING_DELIVERED_SENTINEL")
        assertTrue(
            "TC-GW-STREAMING-001-h (sentinel): `wasDelivered(` (idx=$wasDelIdx) must appear BEFORE " +
                "the `STREAMING_DELIVERED_SENTINEL` return (idx=$sentinelIdx) — " +
                "wasDelivered is the gate that drives whether to return the sentinel.",
            wasDelIdx in 0 until sentinelIdx
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-j (v2.1, 2026-06-26 fix): bilingual MULTI_MESSAGE_HINT
    // injected via SystemPromptComposeHook (NOT via user-text prefix), with
    // NO chatId filter (the v2 filter was broken: PromptHookContext.chatId
    // is null because SystemPromptConfig doesn't thread it through, so the
    // gate always rejected → hint never injected → real-device "回复还是一坨"
    // bug). v2.1 drops the filter; cleanup is via `finally { unregister }`.
    //
    // Background: the v1 impl wrapped `messageTextOverride = buildString {
    //   appendLine(MULTI_MESSAGE_HINT); appendLine(); append(text) }`. That
    // persisted the hint as a Room `ChatMessage(sender="user")`, polluted the
    // chat UI, and contaminated context across turns. Python upstream uses
    // `ephemeral_system_prompt` — appended to `role: "system"` at API-call
    // time, never persisted. Kotlin idiom: `SystemPromptComposeHook` via
    // `PromptHookRegistry.registerSystemPromptComposeHook(...)` returning a
    // `PromptHookMutation(systemPrompt = base + "\n\n" + MULTI_MESSAGE_HINT)`,
    // unregistered in `finally`.
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-j runHermesAgent injects bilingual multi-message hint via SystemPromptComposeHook`() {
        // (1) Constant declaration exists (independent of cron's copy)
        assertTrue(
            "TC-GW-STREAMING-001-j: `HermesGatewayController.kt` must declare a `MULTI_MESSAGE_HINT` " +
                "(or synonymous) constant carrying the bilingual instruction.",
            source.contains("MULTI_MESSAGE_HINT") ||
                source.contains("MULTI_MSG_HINT") ||
                source.contains("MULTIPLE_MESSAGES_HINT")
        )

        // (2) English `blank line` keyword
        assertTrue(
            "TC-GW-STREAMING-001-j: hint must mention English `blank line` keyword.",
            source.contains("blank line")
        )

        // (3) Chinese `空行` keyword
        assertTrue(
            "TC-GW-STREAMING-001-j: hint must mention Chinese `空行` keyword.",
            source.contains("空行")
        )

        // (4) Must register a SystemPromptComposeHook (the proper injection channel —
        //     Python upstream `ephemeral_system_prompt` equivalent; runs inside
        //     `ConversationService.prepareConversationHistory` and mutates the
        //     `role: "system"` message, never touching the persisted user text).
        assertTrue(
            "TC-GW-STREAMING-001-j: `runHermesAgent` must call " +
                "`registerSystemPromptComposeHook(` to install a per-call system-prompt " +
                "addendum hook. This is the only injection channel that (a) does NOT persist " +
                "to Room as a user message, (b) does NOT contaminate context across turns, " +
                "and (c) lands in `role: \"system\"`. Note: gateway-scoping is achieved by " +
                "the `try { register } finally { unregister }` window, NOT by an in-hook " +
                "chatId filter — `PromptHookContext.chatId` is null because " +
                "`SystemPromptConfig` doesn't thread it through.",
            source.contains("registerSystemPromptComposeHook(")
        )

        // (5) Must use `PromptHookMutation` (the return type that carries `systemPrompt`)
        //     OR reference `SystemPromptComposeHook` interface — either evidences the
        //     correct API is being used.
        assertTrue(
            "TC-GW-STREAMING-001-j: `runHermesAgent` must construct a `PromptHookMutation` " +
                "(carrying `systemPrompt`) or reference the `SystemPromptComposeHook` " +
                "interface. Otherwise the hook returns nothing useful and the hint is lost.",
            source.contains("PromptHookMutation") || source.contains("SystemPromptComposeHook")
        )

        // (6) Must unregister the hook in a `finally` block (no leaks across calls
        //     — `PromptHookRegistry` is process-global and concurrent gateway runs
        //     for different chats must not bleed into each other).
        assertTrue(
            "TC-GW-STREAMING-001-j: `runHermesAgent` must call `unregisterSystemPromptComposeHook(` " +
                "to clean up the hook after the agent run completes. Without this, the hook " +
                "leaks into subsequent invocations (including APP UI path).",
            source.contains("unregisterSystemPromptComposeHook(")
        )
        assertTrue(
            "TC-GW-STREAMING-001-j: `runHermesAgent` must have a `finally` block (for the " +
                "unregister cleanup). Otherwise an exception mid-run leaks the hook.",
            source.contains("finally")
        )

        // (7) The unregister call must appear AFTER the register call (ordering sanity).
        val registerIdx = source.indexOf("registerSystemPromptComposeHook(")
        val unregisterIdx = source.indexOf("unregisterSystemPromptComposeHook(")
        assertTrue(
            "TC-GW-STREAMING-001-j: `unregisterSystemPromptComposeHook(` (idx=$unregisterIdx) " +
                "must appear AFTER `registerSystemPromptComposeHook(` (idx=$registerIdx) in source order.",
            registerIdx in 0 until unregisterIdx
        )

        // (8) `messageTextOverride` MUST NOT receive the v1 wrappedText form —
        //     the user text path must be clean (raw `text` passed through).
        //     This is the regression guard against the v1 architectural error.
        val v1WrappedText = source.contains("messageTextOverride = wrappedText") ||
            Regex("""messageTextOverride\s*=\s*buildString""").containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-j: `messageTextOverride` MUST NOT be assigned `wrappedText` " +
                "or `buildString { ... MULTI_MESSAGE_HINT ... }`. The v1 impl was an " +
                "architectural error: it persisted the hint as a user message in Room and " +
                "polluted context across turns. v2 uses `SystemPromptComposeHook` instead.",
            !v1WrappedText
        )

        // (9) `messageTextOverride` must receive the raw `text` parameter
        //     (the original user text — no prefix, no wrapper).
        val rawTextOverride = source.contains("messageTextOverride = text,") ||
            source.contains("messageTextOverride = text)") ||
            Regex("""messageTextOverride\s*=\s*text\s*[,)]""").containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-j: `messageTextOverride` MUST receive the raw `text` parameter " +
                "(e.g. `messageTextOverride = text,`). The hint goes through the system-prompt " +
                "hook, not through user text.",
            rawTextOverride
        )

        // (10) v2.1 regression guard (2026-06-26): the hook MUST NOT short-circuit
        //      on `context.chatId != historyChatId`. The v2 impl did this and
        //      the gate always rejected (PromptHookContext.chatId is null
        //      because SystemPromptConfig doesn't thread it through), causing
        //      the hint to never be injected → real-device "回复还是一坨" bug.
        //      If a future refactor wants to restore chatId-scoping, it MUST
        //      first thread chatId through `SystemPromptConfig.getSystemPrompt*`
        //      and `ConversationService.prepareConversationHistory`, then this
        //      assertion can be relaxed. Until then: any literal of the
        //      broken form is a regression.
        val brokenChatIdFilter =
            Regex("""context\.chatId\s*!=\s*historyChatId""").containsMatchIn(source) ||
                Regex("""context\.chatId\s*==\s*historyChatId""").containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-j (v2.1 regression guard): the SystemPromptComposeHook " +
                "MUST NOT short-circuit on `context.chatId != historyChatId` (or its " +
                "inverse). `PromptHookContext.chatId` is null on the gateway path because " +
                "`SystemPromptConfig` doesn't thread chatId into the hook context — the v2 " +
                "filter caused the hint to never be injected (real-device '回复还是一坨' bug). " +
                "Gateway-scoping is achieved by the `try { register } finally { unregister }` " +
                "window, not by an in-hook chatId comparison.",
            !brokenChatIdFilter
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-e (2026-06-26 new feature): MULTI_MESSAGE_HINT
    // pushes agent toward `send_message` tool usage.
    //
    // The R-GW-STREAMING-001 v2.1 hint was bilingual but only mentioned
    // `blank line` / `空行` — it relied on the model splitting paragraphs in
    // its single AssistantDelta. That left the "回复还是一坨" symptom in
    // real-device tests because 1 turn = 1 AssistantDelta and Chinese LLMs
    // don't emit blank lines.
    //
    // R-GW-STREAMING-002 augments the hint to push the model toward
    // calling the `send_message` tool mid-loop, which delivers each chunk
    // as a separate IM bubble immediately. The two-layer design is
    // preserved: (a) tool calls are the primary path (each call = 1
    // bubble), (b) sidecar blank-line splitting is the fallback for
    // models that didn't take the tool hint.
    //
    // The bilingual `blank line` / `空行` assertions from
    // TC-GW-STREAMING-001-j stay intact (covered above) — this test only
    // adds NEW assertions for the tool-pushing keywords.
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-e MULTI_MESSAGE_HINT pushes send_message tool usage`() {
        // (1) Hint must mention the literal tool name `send_message` so the
        //     model knows exactly which tool to call.
        assertTrue(
            "TC-GW-STREAMING-002-e: `MULTI_MESSAGE_HINT` (or synonymous constant) " +
                "must contain the literal `send_message` so the model can " +
                "unambiguously identify which tool to call.",
            source.contains("send_message")
        )

        // (2) English half of the hint must mention `tool` so the model
        //     understands this is a tool-call instruction (not a textual
        //     instruction to literally write "send_message" in the reply).
        assertTrue(
            "TC-GW-STREAMING-002-e: hint must mention English `tool` keyword " +
                "to make clear this is a tool-call instruction, not a literal " +
                "string-output instruction.",
            source.contains("tool")
        )

        // (3) Chinese half must mention `工具` for CN-leaning models.
        assertTrue(
            "TC-GW-STREAMING-002-e: hint must mention Chinese `工具` keyword " +
                "— bilingual support so CN-leaning models reliably interpret " +
                "the instruction.",
            source.contains("工具")
        )

        // (4) Regression guard for TC-GW-STREAMING-001-j: bilingual
        //     `blank line` / `空行` keywords MUST still be present (the
        //     sidecar fallback layer still expects the model to optionally
        //     emit blank lines when not using the tool).
        assertTrue(
            "TC-GW-STREAMING-002-e (regression guard): `blank line` keyword " +
                "from R-GW-STREAMING-001-j hint MUST still be present — the " +
                "sidecar blank-line splitting layer is still active as fallback.",
            source.contains("blank line")
        )
        assertTrue(
            "TC-GW-STREAMING-002-e (regression guard): `空行` keyword from " +
                "R-GW-STREAMING-001-j hint MUST still be present — the sidecar " +
                "fallback layer is still active.",
            source.contains("空行")
        )
    }


    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-p (2026-06-27 bugfix): controller wiring must pass
    // historyChatId as busTagChatId, and the platform-native chatId as
    // wireChatId. See AgentStreamingSidecarShapeWiringTest for the sidecar
    // side of the fix.
    //
    // Real-device log evidence: `dispatchOutgoing` was receiving the 5-token
    // `gw:weixin:wxid@im.wechat:wxid@im.wechat:wxid@im.wechat` as `chatId`,
    // which then passed unmodified to `WeixinAdapter.send(to_user_id=...)`
    // and triggered `errcode=-3` from iLink. Bus-filter requires the
    // 5-token historyChatId; adapter wire requires the 1-token chatId.
    // Two purposes → two fields.
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-p controller passes historyChatId as busTag and platform chatId as wire`() {
        // (1) Construct sidecar with the bus-tag field set to historyChatId.
        val hasBusTagAssignment = Regex("""busTagChatId\s*=\s*historyChatId""")
            .containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-p: `HermesGatewayController.runHermesAgent` must construct " +
                "`AgentStreamingSidecar(busTagChatId = historyChatId, ...)`. The bus-tag " +
                "must match the AgentEventBus tag, which is the 5-token historyChatId.",
            hasBusTagAssignment
        )
        // (2) Construct sidecar with the wire field set to the raw `chatId` param.
        val hasWireAssignment = Regex("""wireChatId\s*=\s*chatId\b""")
            .containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-p: `HermesGatewayController.runHermesAgent` must construct " +
                "`AgentStreamingSidecar(..., wireChatId = chatId, ...)`. The wire chatId is " +
                "the platform-native single-segment chatId (e.g. WeChat `wxid@im.wechat`) " +
                "that the IM adapter expects as `to_user_id`.",
            hasWireAssignment
        )
        // (3) Regression guard: the legacy single-field form
        //     `AgentStreamingSidecar(chatId = historyChatId, ...)` MUST be gone.
        val hasLegacyChatIdArg = Regex("""AgentStreamingSidecar\s*\([\s\S]*?chatId\s*=\s*historyChatId""")
            .containsMatchIn(source)
        assertFalse(
            "TC-GW-STREAMING-001-p: legacy single-field invocation " +
                "`AgentStreamingSidecar(chatId = historyChatId, ...)` MUST be gone. That " +
                "form fed the polluted 5-token historyChatId into `WeixinAdapter.send` and " +
                "triggered the WeChat `errcode=-3` bug. Use `busTagChatId = historyChatId, " +
                "wireChatId = chatId` instead.",
            hasLegacyChatIdArg
        )
    }


    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments while
     * preserving newlines so failure messages stay meaningful and string
     * literals inside docstrings don't pollute literal-content checks.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun controllerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        return alt.path
    }
}
