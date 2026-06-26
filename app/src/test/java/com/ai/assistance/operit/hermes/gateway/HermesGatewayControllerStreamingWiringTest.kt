package com.ai.assistance.operit.hermes.gateway

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
 *  - TC-GW-STREAMING-001-j → bilingual `MULTI_MESSAGE_HINT` injected into the
 *    user message before `core.sendUserMessage` (mirrors cron's
 *    R-CRON-STREAMING-002 `MULTI_MESSAGE_HINT`).
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
        // Caller-supplied paragraphRegex + delay constants (architecture decision:
        // sidecar takes them as constructor args, not file-scope coupling).
        assertTrue(
            "TC-GW-STREAMING-001-h: must define `STREAMING_PARAGRAPH_REGEX` and " +
                "`STREAMING_INTER_PARAGRAPH_DELAY_MS` as caller-supplied constants and " +
                "pass them through the sidecar constructor.",
            source.contains("STREAMING_PARAGRAPH_REGEX") &&
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
    // TC-GW-STREAMING-001-j: bilingual MULTI_MESSAGE_HINT injected into user text
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-j runHermesAgent injects bilingual multi-message hint`() {
        // (1) Constant declaration exists (independent of cron's copy — no cross-file coupling)
        assertTrue(
            "TC-GW-STREAMING-001-j: `HermesGatewayController.kt` must declare a `MULTI_MESSAGE_HINT` " +
                "(or synonymous) file-/companion-scope constant carrying the bilingual multi-message instruction. " +
                "Independent of `CronAgentRunner.MULTI_MESSAGE_HINT` (no cross-file constant coupling).",
            source.contains("MULTI_MESSAGE_HINT") ||
                source.contains("MULTI_MSG_HINT") ||
                source.contains("MULTIPLE_MESSAGES_HINT")
        )

        // (2) Hint content must include English `blank line` keyword
        assertTrue(
            "TC-GW-STREAMING-001-j: hint constant must mention English `blank line` keyword so the " +
                "instruction is understandable to English-mode agents.",
            source.contains("blank line")
        )

        // (3) Hint content must include Chinese `空行` keyword
        assertTrue(
            "TC-GW-STREAMING-001-j: hint constant must mention Chinese `空行` keyword so the " +
                "instruction reaches Chinese-mode agents too.",
            source.contains("空行")
        )

        // (4) Hint must be referenced/injected BEFORE `core.sendUserMessage(` — i.e. the
        //     hint is wired into the user-message path (since ChatServiceCore.sendUserMessage
        //     has no separate `systemPrompt` parameter, the only injection vector is
        //     prefixing the user text via a `buildString { append(MULTI_MESSAGE_HINT) … }` block).
        val hintIdx = listOf(
            source.indexOf("MULTI_MESSAGE_HINT"),
            source.indexOf("MULTI_MSG_HINT"),
            source.indexOf("MULTIPLE_MESSAGES_HINT")
        ).filter { it >= 0 }.minOrNull() ?: -1
        // The first `MULTI_MESSAGE_HINT` reference is the const declaration; we want the
        // USAGE site to also exist (declaration + at least one usage = 2 occurrences).
        val hintOccurrences = Regex("""MULTI_MESSAGE_HINT""").findAll(source).count() +
            Regex("""MULTI_MSG_HINT""").findAll(source).count() +
            Regex("""MULTIPLE_MESSAGES_HINT""").findAll(source).count()
        assertTrue(
            "TC-GW-STREAMING-001-j: `MULTI_MESSAGE_HINT` must appear at least twice in source " +
                "(declaration + at least one usage). Otherwise the hint is a dead constant. " +
                "Found $hintOccurrences occurrence(s).",
            hintOccurrences >= 2
        )
        assertTrue("TC-GW-STREAMING-001-j: hint reference must exist.", hintIdx >= 0)

        // (5) `messageTextOverride` must NOT be passed the raw `text` parameter directly —
        //     it must receive the wrapped/prefixed form (e.g. `wrappedText`). We check for
        //     the wrapping pattern: a `messageTextOverride = ` assignment that is NOT
        //     `messageTextOverride = text,` / `messageTextOverride = text)`.
        val rawTextOverride = source.contains("messageTextOverride = text,") ||
            source.contains("messageTextOverride = text)") ||
            Regex("""messageTextOverride\s*=\s*text\s*[,)]""").containsMatchIn(source)
        assertTrue(
            "TC-GW-STREAMING-001-j: `messageTextOverride` MUST NOT receive raw `text` directly — " +
                "it must be passed a wrapped form (e.g. `wrappedText` from " +
                "`buildString { appendLine(MULTI_MESSAGE_HINT); appendLine(); append(text) }`). " +
                "Otherwise the hint is never delivered to the agent.",
            !rawTextOverride
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
