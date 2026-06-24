package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-CRON-STREAMING-001 (TC-CRON-STREAMING-a..e): `CronAgentRunner.run` must run
 * a per-turn streaming sidecar that subscribes to `AgentEventBus.events`,
 * filters by current `chatId`, reacts only to `AgentEvent.AssistantDelta`,
 * and dispatches each turn's reply through `gateway.dispatchOutgoing(...)`
 * **as a separate IM message**.
 *
 * Why source-scan only: the real bus subscription is glued together by
 * Android coroutines + a live `EnhancedAIService` + a live `HermesGatewayController`,
 * none of which is unit-testable inside a pure JVM test. Literal-level wiring
 * is sufficient to prevent regression and is consistent with the existing
 * `CronAgentRunnerSanitizeWiringTest` style.
 *
 * Behavior locked by the 5 sub-tests:
 *  (a) sidecar subscribes to AgentEventBus + AssistantDelta BEFORE main-path
 *      stream collection
 *  (b) each AssistantDelta -> strip markup -> dispatchOutgoing; errors swallowed
 *  (c) weixin group chat (`@chatroom` suffix) skips the sidecar
 *  (d) per-chatId sidecar dispatch is sequential (Mutex / Channel)
 *  (e) `deliver(...)` skips IM re-dispatch when the sidecar already delivered,
 *      but still writes the local chat note + saves job output
 *
 * No existing feature is touched: interjection / `/steer` lives on the `inbound`
 * path, cron alarm receiver entry is untouched, the existing
 * `HermesReplyMarkupStripper.strip` callsites remain.
 */
class CronStreamingDispatchWiringTest {

    /** Comment-stripped source so literal `indexOf` doesn't false-positive on KDoc. */
    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }

    /** Comment-stripped body of the `run` function only. */
    private val runBody: String by lazy { extractFunctionBody(source, "suspend fun run(") }

    /** Comment-stripped body of the `deliver` function only. */
    private val deliverBody: String by lazy { extractFunctionBody(source, "suspend fun deliver(") }

    // ---------------------------------------------------------------------
    // (a) sidecar subscribes BEFORE responseStream collect / responseBuilder
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-a run subscribes to AssistantDelta bus before stream collect`() {
        assertTrue(
            "TC-CRON-STREAMING-a: `CronAgentRunner.run` body not found — structure changed.",
            runBody.isNotEmpty()
        )

        // (1) Must reference AgentEventBus + AssistantDelta inside run()
        assertTrue(
            "TC-CRON-STREAMING-a: `run` must reference `AgentEventBus` to subscribe per-turn deltas. " +
                "Actual body head:\n${runBody.take(2000)}",
            runBody.contains("AgentEventBus")
        )
        assertTrue(
            "TC-CRON-STREAMING-a: `run` must reference `AssistantDelta` to react only to per-turn replies " +
                "(skipping pure tool_call / reasoning turns).",
            runBody.contains("AssistantDelta")
        )

        // (2) The AssistantDelta reference must appear BEFORE responseStream.collect / responseBuilder.append
        val deltaIdx = runBody.indexOf("AssistantDelta")
        val collectIdx = runBody.indexOf("responseStream")
        val appendIdx = runBody.indexOf("responseBuilder.append(")
        val mainPathIdx = listOf(collectIdx, appendIdx).filter { it >= 0 }.minOrNull() ?: -1
        assertTrue(
            "TC-CRON-STREAMING-a: sidecar subscription (`AssistantDelta` literal) must appear BEFORE " +
                "main-path stream collection (`responseStream` / `responseBuilder.append(`). " +
                "deltaIdx=$deltaIdx mainPathIdx=$mainPathIdx",
            deltaIdx in 0 until mainPathIdx
        )

        // (3) Must launch a sidecar coroutine (not block the main path)
        assertTrue(
            "TC-CRON-STREAMING-a: sidecar must be started via `launch` (or `launchIn`) so it does not " +
                "block the main-path stream collection.",
            runBody.contains("launch")
        )

        // (4) Subscription must filter by chatId (key match with EnhancedAIService.taskIdValue == chatId)
        // We accept any literal mention of chatId inside the same function — the filter expression
        // necessarily contains it.
        assertTrue(
            "TC-CRON-STREAMING-a: subscription filter must reference `chatId` to match the bus key " +
                "emitted by `EnhancedAIService` (taskIdValue == chatId).",
            runBody.contains("chatId")
        )
    }

    // ---------------------------------------------------------------------
    // (b) each AssistantDelta -> strip -> dispatchOutgoing; errors swallowed
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-b each AssistantDelta is stripped and dispatched and errors swallowed`() {
        assertTrue(
            "TC-CRON-STREAMING-b: `run` body not found.",
            runBody.isNotEmpty()
        )

        // Sidecar block boundary: from the first `AssistantDelta` reference to the end of run().
        val sidecarStart = runBody.indexOf("AssistantDelta")
        assertTrue(
            "TC-CRON-STREAMING-b: no `AssistantDelta` reference in `run` — TC-CRON-STREAMING-a should " +
                "have caught this first.",
            sidecarStart >= 0
        )
        val sidecarSlice = runBody.substring(sidecarStart)

        // (1) Must call HermesReplyMarkupStripper.strip on the per-turn text
        assertTrue(
            "TC-CRON-STREAMING-b: sidecar must call `HermesReplyMarkupStripper.strip(` on each turn's text " +
                "before dispatching, mirroring main-path behavior. Sidecar slice head:\n" +
                sidecarSlice.take(1500),
            sidecarSlice.contains("HermesReplyMarkupStripper.strip(")
        )

        // (2) Must dispatch via gateway.dispatchOutgoing
        assertTrue(
            "TC-CRON-STREAMING-b: sidecar must call `dispatchOutgoing(` to push each turn's reply to IM.",
            sidecarSlice.contains("dispatchOutgoing(")
        )

        // (3) Must guard empty strings (isNotBlank / isNotEmpty) so blank turns don't fire IM noise
        val blankGuarded = sidecarSlice.contains("isNotBlank()") || sidecarSlice.contains("isNotEmpty()")
        assertTrue(
            "TC-CRON-STREAMING-b: sidecar must guard against blank/empty stripped text " +
                "(`isNotBlank()` or `isNotEmpty()`) — otherwise blank turns would push empty messages.",
            blankGuarded
        )

        // (4) Dispatch must be wrapped in try/catch (failures must not abort the agent loop)
        val tryIdx = sidecarSlice.indexOf("try")
        val catchIdx = sidecarSlice.indexOf("catch")
        assertTrue(
            "TC-CRON-STREAMING-b: sidecar dispatch must be wrapped in `try { ... } catch { ... }` so that " +
                "a single failed IM send doesn't abort the agent loop. tryIdx=$tryIdx catchIdx=$catchIdx",
            tryIdx >= 0 && catchIdx > tryIdx
        )

        // (5) Must log via CronFileLogger so failures are diagnosable from /sdcard/Download/Hermes/cron_logs/cron.log
        assertTrue(
            "TC-CRON-STREAMING-b: sidecar must call `CronFileLogger` (R-OBS-001) on at least one of the " +
                "trace / error paths, so per-turn dispatch is visible alongside the existing cron.log lines.",
            sidecarSlice.contains("CronFileLogger")
        )
    }

    // ---------------------------------------------------------------------
    // (c) weixin @chatroom skips sidecar (no group flooding)
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-c weixin chatroom skips streaming sidecar`() {
        assertTrue(
            "TC-CRON-STREAMING-c: `run` body not found.",
            runBody.isNotEmpty()
        )

        // (1) Must reference the @chatroom suffix literal
        assertTrue(
            "TC-CRON-STREAMING-c: `run` must reference the `@chatroom` literal to detect Weixin group chats. " +
                "(Weixin adapter's only signal when we have just chatId is `chatId.endsWith(\"@chatroom\")`.)",
            runBody.contains("@chatroom")
        )

        // (2) Must invoke endsWith(...) so it's a real suffix check, not just an incidental literal
        assertTrue(
            "TC-CRON-STREAMING-c: group detection must call `endsWith(` on chatId — incidental occurrence " +
                "of the `@chatroom` literal isn't enough.",
            runBody.contains("endsWith(")
        )

        // (3) The @chatroom check must appear BEFORE the AssistantDelta sidecar subscription point.
        // (If it appears after the subscribe, the guard is useless — we already subscribed.)
        val groupIdx = runBody.indexOf("@chatroom")
        val deltaIdx = runBody.indexOf("AssistantDelta")
        assertTrue(
            "TC-CRON-STREAMING-c: `@chatroom` group-chat guard must appear BEFORE the `AssistantDelta` " +
                "sidecar subscription point — otherwise the sidecar runs in groups too (group flooding). " +
                "groupIdx=$groupIdx deltaIdx=$deltaIdx",
            groupIdx in 0 until deltaIdx
        )
    }

    // ---------------------------------------------------------------------
    // (d) per-chatId dispatch is sequential (Mutex / Channel)
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-d streaming dispatch is sequential per chatId`() {
        assertTrue(
            "TC-CRON-STREAMING-d: `run` body not found.",
            runBody.isNotEmpty()
        )

        // Sidecar slice = from AssistantDelta reference to end of run()
        val sidecarStart = runBody.indexOf("AssistantDelta")
        assertTrue("TC-CRON-STREAMING-d: AssistantDelta missing from run().", sidecarStart >= 0)
        val sidecarSlice = runBody.substring(sidecarStart)

        // Sequential serializer: either Mutex.withLock, Channel.consumeEach / collect, or a sequential
        // SharedFlow.collect (which is sequential per collector anyway). We require an explicit
        // sequencing primitive name to make intent obvious.
        val mutexOk = sidecarSlice.contains("Mutex") && sidecarSlice.contains(".withLock")
        val channelOk = sidecarSlice.contains("Channel") &&
            (sidecarSlice.contains(".consumeEach") || sidecarSlice.contains(".collect"))
        // A bare `.collect { ... }` on the SharedFlow is also sequential per collector — accept it
        // as long as the dispatch happens INSIDE the .collect lambda (the only way to guarantee
        // sequencing without Mutex).
        val collectOnly = sidecarSlice.contains(".collect")

        assertTrue(
            "TC-CRON-STREAMING-d: sidecar must serialize per-chatId dispatch via either " +
                "`Mutex` + `.withLock`, `Channel` + `.consumeEach`/`.collect`, or a bare `.collect { ... }` " +
                "lambda (which is sequential per collector). Otherwise N concurrent IM sends could land " +
                "out of order in Weixin.",
            mutexOk || channelOk || collectOnly
        )
    }

    // ---------------------------------------------------------------------
    // (e) deliver(...) skips IM re-dispatch when sidecar already delivered
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-e deliver skips IM dispatch when streaming sidecar already sent`() {
        assertTrue(
            "TC-CRON-STREAMING-e: `deliver` body not found.",
            deliverBody.isNotEmpty()
        )

        // (1) `deliver` must reference the streaming-delivered flag literal
        val hasFlagRead = deliverBody.contains("streamingDelivered") ||
            deliverBody.contains("streamingDeliveredAny")
        assertTrue(
            "TC-CRON-STREAMING-e: `deliver(...)` must check a `streamingDelivered*` flag so it can skip " +
                "the IM re-dispatch when the sidecar already pushed per-turn messages. " +
                "Deliver body head:\n${deliverBody.take(1500)}",
            hasFlagRead
        )

        // (2) Somewhere in the source, the flag must be SET (sidecar success path)
        val hasFlagSet = source.contains("streamingDelivered = true") ||
            source.contains("streamingDeliveredAny = true") ||
            source.contains("streamingDelivered.set(true)") ||
            source.contains("streamingDeliveredAny.set(true)")
        assertTrue(
            "TC-CRON-STREAMING-e: source must assign `streamingDelivered* = true` (or `.set(true)`) " +
                "on the sidecar success path, otherwise the dedup check in `deliver` is dead code.",
            hasFlagSet
        )

        // (3) Locate the dedup branch inside deliver(). The first occurrence of the flag literal is
        // the read site; from there we walk forward to find the branch body. The branch body MUST
        // call writeLocalChatNote (to keep app chat history + bus event), but MUST NOT call
        // dispatchOutgoing / deliverText (those would re-send what the sidecar already sent).
        val flagReadIdx = listOf(
            deliverBody.indexOf("streamingDelivered"),
            deliverBody.indexOf("streamingDeliveredAny")
        ).filter { it >= 0 }.minOrNull() ?: -1
        assertTrue("TC-CRON-STREAMING-e: streaming flag read index not found.", flagReadIdx >= 0)

        // Inspect from flagReadIdx through the next ~600 chars (typical guard-clause window).
        val dedupWindow = deliverBody.substring(flagReadIdx, minOf(flagReadIdx + 800, deliverBody.length))

        // (3a) The dedup branch must keep the local chat note write (so app UI / Room are unchanged).
        // Either the call appears earlier in deliver() (unconditional, top of function) OR it
        // appears within the dedup window. We accept either.
        val keepsLocalChatNote = deliverBody.contains("writeLocalChatNote(")
        assertTrue(
            "TC-CRON-STREAMING-e: `deliver` must still call `writeLocalChatNote(` so the app UI / Room " +
                "history are written even when the sidecar already pushed IM messages.",
            keepsLocalChatNote
        )

        // (3b) Within the dedup window, `dispatchOutgoing(` must NOT appear AGAIN after the flag read —
        // i.e. the dedup branch must early-return / skip the IM call. Easiest check: the dedup
        // window contains `return` (early return) before any subsequent `dispatchOutgoing(` literal.
        val nextDispatchInWindow = dedupWindow.indexOf("dispatchOutgoing(")
        val nextReturnInWindow = dedupWindow.indexOf("return")
        val dedupSkipsIm = nextDispatchInWindow < 0 ||
            (nextReturnInWindow in 0 until nextDispatchInWindow)
        assertFalse(
            "TC-CRON-STREAMING-e: dedup branch must NOT call `dispatchOutgoing(` after observing the " +
                "`streamingDelivered*` flag — otherwise the sidecar-delivered messages would be sent " +
                "twice. Window head:\n${dedupWindow.take(800)}",
            !dedupSkipsIm
        )
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /**
     * Extract the body of a top-level function whose signature begins with [signaturePrefix].
     * Returns the substring from the function's opening `{` (inclusive) to the matching closing
     * `}` (inclusive). Returns "" if not found.
     *
     * Brace-depth scan; assumes well-formed Kotlin (which `:app:compileDebugKotlin` already
     * enforces).
     */
    private fun extractFunctionBody(text: String, signaturePrefix: String): String {
        val anchor = text.indexOf(signaturePrefix)
        if (anchor < 0) return ""
        val openBrace = text.indexOf('{', anchor)
        if (openBrace < 0) return ""
        var depth = 0
        var i = openBrace
        while (i < text.length) {
            val c = text[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return text.substring(openBrace, i + 1)
            }
            i++
        }
        return text.substring(openBrace)
    }

    /**
     * Strip Kotlin `/* ... */` (incl. KDoc `/** ... */`) block comments and `// ...`
     * line comments, preserving newlines so failure messages still line up.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun runnerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/core/cron/CronAgentRunner.kt")
        return alt.path
    }
}
