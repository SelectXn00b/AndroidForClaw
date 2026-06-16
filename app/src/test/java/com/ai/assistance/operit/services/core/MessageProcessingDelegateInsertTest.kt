package com.ai.assistance.operit.services.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-061: `MessageProcessingDelegate.sendUserMessage` cancel-then-resend
 * when `chatRuntime.isLoading == true`.
 *
 * **Background**: previous code silently dropped messages typed during agent
 * processing — user UX = "I sent a message but nothing happened". The new
 * behavior: cancel the current turn, wait for it to settle, then re-enter
 * sendUserMessage with the same args. Mirrors the gateway-side default
 * `_busyInputMode="interrupt"` (R-GATEWAY-035) so app-internal chat and
 * gateway IM chats have the same mid-turn接力 UX.
 *
 * No Python upstream — purely an Android UI behavior change. We validate
 * via source-scan because instantiating MessageProcessingDelegate requires
 * a full Android Service + dozens of dependencies, way more setup than
 * needed to lock in the structural change.
 */
class MessageProcessingDelegateInsertTest {

    private val source: String by lazy { File(delegatePath()).readText() }

    /** Locate the body of `fun sendUserMessage(`. */
    private fun extractSendUserMessageBody(): String {
        val anchor = Regex("""fun\s+sendUserMessage\s*\(""").find(source)?.range?.first
            ?: error("Cannot find sendUserMessage in MessageProcessingDelegate.kt")
        // First `{` after the param-list closing `)`.
        // We can't easily find the param-list end without a parser, but the
        // first `{` after `anchor` whose preceding non-whitespace char is `)`
        // is the function body opener. Simpler: just take the first `{`
        // after the `:Unit?` (functions don't have explicit return type
        // here — fall back to first `{` after `anchor` after at least 50
        // chars to skip the param list itself).
        var i = source.indexOf('{', anchor + 50)
        require(i >= 0) { "Cannot find sendUserMessage opening brace" }
        var depth = 0
        val start = i
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(start, i + 1)
            }
            i++
        }
        return source.substring(start)
    }

    /** Locate the busy branch body inside sendUserMessage. */
    private fun extractBusyBranchBody(): String {
        val body = extractSendUserMessageBody()
        val busyAnchor = body.indexOf("isLoading.value")
        require(busyAnchor >= 0) { "Cannot find isLoading.value busy guard in sendUserMessage" }
        // Walk to the `{` that opens the `if (chatRuntime.isLoading.value) {` branch.
        var i = body.indexOf('{', busyAnchor)
        require(i >= 0) { "Cannot find busy branch opening brace" }
        val start = i
        var depth = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return body.substring(start, i + 1)
            }
            i++
        }
        return body.substring(start)
    }

    // -------- TC-UI-061-a: idle path unchanged --------
    /**
     * TC-UI-061-a: When `isLoading=false`, sendUserMessage proceeds normally:
     *   - clears `_userMessage`
     *   - sets `isLoading=true`
     *   - launches sendJob
     * The cancel-then-resend code path must be GUARDED by the busy check —
     * NOT unconditionally invoked. We assert: the `cancelMessageInternal`
     * call appears INSIDE the `isLoading.value` if-branch, not outside.
     */
    @Test
    fun `TC-UI-061-a idle path unchanged`() {
        val body = extractSendUserMessageBody()
        val busyBranch = extractBusyBranchBody()

        // Body must still mark `isLoading.value = true` (idle path side effect).
        assertTrue(
            "TC-UI-061-a: idle path must still set isLoading=true",
            body.contains("chatRuntime.isLoading.value = true"),
        )
        // Body must still set `_userMessage.value = TextFieldValue(\"\")` for the
        // override == null case (idle path clears the input).
        assertTrue(
            "TC-UI-061-a: idle path must still clear _userMessage on null override",
            body.contains("_userMessage.value = TextFieldValue(\"\")"),
        )
        // The cancelMessageInternal call MUST be inside the busy branch — guard
        // structural correctness so the idle path doesn't accidentally cancel.
        assertTrue(
            "TC-UI-061-a: cancelMessageInternal must live inside busy branch",
            busyBranch.contains("cancelMessageInternal"),
        )
    }

    // -------- TC-UI-061-b: busy triggers cancel then resend --------
    /**
     * TC-UI-061-b: The busy branch must contain (in order):
     *   1. `cancelMessageInternal(chatId, keepPartialResponse = true)` — drop the
     *      ongoing run but keep partial response visible.
     *   2. A re-entrant `sendUserMessage(` call — recursive resend.
     *   3. Forward all 23 named parameters so the resend is byte-for-byte
     *      equivalent to the original call.
     */
    @Test
    fun `TC-UI-061-b busy triggers cancel then resend`() {
        val branch = extractBusyBranchBody()

        // 1. cancelMessageInternal call.
        assertTrue(
            "TC-UI-061-b: busy branch must call cancelMessageInternal(...). branch=$branch",
            Regex("""cancelMessageInternal\s*\(\s*chatId\s*,\s*keepPartialResponse\s*=\s*true\s*\)""")
                .containsMatchIn(branch),
        )

        // 2. Recursive sendUserMessage call.
        assertTrue(
            "TC-UI-061-b: busy branch must recursively call sendUserMessage(...)",
            Regex("""sendUserMessage\s*\(""").containsMatchIn(branch),
        )

        // 3. All 23 named params forwarded — assert each named-arg occurrence.
        val requiredParams = listOf(
            "attachments = attachments",
            "chatId = chatId",
            "messageTextOverride = messageTextOverride",
            "proxySenderNameOverride = proxySenderNameOverride",
            "workspacePath = workspacePath",
            "workspaceEnv = workspaceEnv",
            "promptFunctionType = promptFunctionType",
            "roleCardId = roleCardId",
            "enableThinking = enableThinking",
            "thinkingGuidance = thinkingGuidance",
            "enableMemoryQuery = enableMemoryQuery",
            "enableWorkspaceAttachment = enableWorkspaceAttachment",
            "maxTokens = maxTokens",
            "tokenUsageThreshold = tokenUsageThreshold",
            "replyToMessage = replyToMessage",
            "isAutoContinuation = isAutoContinuation",
            "enableSummary = enableSummary",
            "chatModelConfigIdOverride = chatModelConfigIdOverride",
            "chatModelIndexOverride = chatModelIndexOverride",
            "suppressUserMessageInHistory = suppressUserMessageInHistory",
            "isGroupOrchestrationTurn = isGroupOrchestrationTurn",
            "groupParticipantNamesText = groupParticipantNamesText",
            "isSubTask = isSubTask",
        )
        for (p in requiredParams) {
            assertTrue(
                "TC-UI-061-b: resend must forward `$p`",
                branch.contains(p),
            )
        }

        // 4. Cancel must precede resend in source order — otherwise we'd send
        //    before the previous run is wound down.
        val cancelIdx = branch.indexOf("cancelMessageInternal")
        val resendIdx = branch.indexOf("sendUserMessage(", startIndex = cancelIdx + 1)
        assertTrue(
            "TC-UI-061-b: cancelMessageInternal must precede recursive sendUserMessage",
            cancelIdx >= 0 && resendIdx > cancelIdx,
        )
    }

    // -------- TC-UI-061-c: cancel timeout drops resend --------
    /**
     * TC-UI-061-c: The cancel call must be wrapped in `withTimeoutOrNull`
     * with a 10s timeout (or some bounded number of milliseconds) so a stuck
     * job cannot hang the whole resend path. On timeout, the resend must be
     * SKIPPED (not invoked) — otherwise we'd risk concurrent sends.
     */
    @Test
    fun `TC-UI-061-c cancel timeout drops resend`() {
        val branch = extractBusyBranchBody()
        // withTimeoutOrNull around the cancel.
        assertTrue(
            "TC-UI-061-c: cancel must be guarded by withTimeoutOrNull",
            Regex("""withTimeoutOrNull\s*\(\s*10_?000""").containsMatchIn(branch),
        )
        // On timeout (null result), branch must `return` (or `return@launch`)
        // — i.e. NOT proceed to the recursive sendUserMessage.
        assertTrue(
            "TC-UI-061-c: timeout branch must early-return (return@launch) before resend",
            Regex("""return@launch""").containsMatchIn(branch),
        )
        // Sanity: timeout path log warn for debuggability.
        assertTrue(
            "TC-UI-061-c: timeout path should log a warning so dropped resends are debuggable",
            Regex("""AppLogger\.w\s*\(""").containsMatchIn(branch),
        )
    }

    // -------- TC-UI-061-d: empty message still early returns --------
    /**
     * TC-UI-061-d: The early-return for blank message + empty attachments
     * + non-autoContinuation + non-group must still happen BEFORE the busy
     * check. Cancel-then-resend must NOT trigger for empty messages —
     * otherwise we'd cancel the running turn over a no-op.
     */
    @Test
    fun `TC-UI-061-d empty message still early returns`() {
        val body = extractSendUserMessageBody()

        val emptyEarlyReturnIdx = body.indexOf("rawMessageText.isBlank()")
        val busyCheckIdx = body.indexOf("isLoading.value")

        assertTrue("empty-message early-return must exist", emptyEarlyReturnIdx >= 0)
        assertTrue("busy guard must exist", busyCheckIdx >= 0)
        assertTrue(
            "TC-UI-061-d: empty-message early-return must precede the busy guard (otherwise we'd cancel over a no-op)",
            emptyEarlyReturnIdx < busyCheckIdx,
        )

        // Also: empty-message path must NOT mention cancel — it just returns.
        val earlyReturnSlice = body.substring(emptyEarlyReturnIdx, busyCheckIdx)
        assertFalse(
            "TC-UI-061-d: empty-message branch must not call cancelMessageInternal",
            earlyReturnSlice.contains("cancelMessageInternal"),
        )
    }

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageProcessingDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
