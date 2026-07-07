package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-033 / R-AGENT-045 / R-CRON-STREAMING-001
 * — TC-CRON-STREAMING-l / TC-CRON-STREAMING-m
 *
 * Bug context (2026-07-07): user reported "after the WeChat streaming wire-chatId
 * fix, the cron timer stopped working." Real-device cron.log at 11:50 showed all
 * 5 sidecar dispatch attempts failing with `dispatchSuccess=0` and the WeChat
 * iLink returning `errcode=-3` — the same wire-vs-history chatId conflation that
 * commit 99df3979 fixed on the gateway path.
 *
 * Root cause (code review, not data-dependent):
 *   `CronAgentRunner.run()` writes `resolvedChatId` (a Room chat UUID or a
 *   `gw:$sessionKey:$chatId` history form) into `HERMES_SESSION_CHAT_ID` +
 *   `HERMES_CRON_AUTO_DELIVER_CHAT_ID` via `setSessionVars` / `setCronAutoDeliverVars`.
 *   The KDoc of `setCronAutoDeliverVars` (SessionContext.kt:100-107) makes the
 *   contract explicit — `chat_id` in those ThreadLocals is "the inbound IM origin
 *   chat_id so the Scheduler can deliver the result back to that same chat", i.e.
 *   the WIRE form. If any `cronjob(action="create")` fires from within the cron
 *   run (e.g. renewal / linked reminders), `_originFromEnv()` snapshots the
 *   wrong ThreadLocal value into the new job's `origin.chat_id`, which then
 *   feeds `WeixinAdapter.send(to_user_id=...)` verbatim on every subsequent run —
 *   iLink rejects it with `errcode=-3` forever.
 *
 * Fix expected:
 *   1. `setSessionVars(chatId = originChatId, ...)`  — wire form
 *   2. `setCronAutoDeliverVars(chatId = originChatId, ...)`  — wire form
 *   3. `resolvedChatId` is retained for what it legitimately does — the sidecar's
 *      `AgentEventBus` filter and `EnhancedAIService.sendMessage(chatId=...)`
 *      Room lookup — but MUST NOT bleed into ThreadLocal session vars.
 *
 * Defense in depth (TC-CRON-STREAMING-m):
 *   Before every `dispatchOutgoing(...)` call, `CronAgentRunner` must reject
 *   chatIds that violate the wire-form contract (`gw:` prefix or more than one
 *   `@im.wechat` segment). Log an error line and skip dispatch — do not feed
 *   dirty data to `WeixinAdapter.send` just to watch iLink return `errcode=-3`.
 *
 * Why source-scan only: same reason as `CronStreamingDispatchWiringTest` — live
 * AlarmManager + AgentEventBus + HermesGatewayController are not JVM-testable.
 * Literal-level wiring assertions are consistent with the existing style.
 */
class CronAgentRunnerWireChatIdWiringTest {

    /** Comment-stripped source so literal `indexOf` doesn't false-positive on KDoc. */
    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }

    /** Comment-stripped body of the `run` function only. */
    private val runBody: String by lazy { extractFunctionBody(source, "suspend fun run(") }

    /** Comment-stripped body of the `deliver` function only. */
    private val deliverBody: String by lazy { extractFunctionBody(source, "suspend fun deliver(") }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-l : session vars carry wire originChatId, not resolvedChatId
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-l session vars carry wire originChatId not resolvedChatId`() {
        assertTrue(
            "TC-CRON-STREAMING-l: `CronAgentRunner.run` body not found — structure changed.",
            runBody.isNotEmpty()
        )

        // (1) setSessionVars must be called with chatId = originChatId (wire form).
        //     Regex accepts arbitrary whitespace + interleaving named args.
        val setSessionVarsWithWire = Regex(
            """setSessionVars\s*\((?:[^()]|\([^)]*\))*?chatId\s*=\s*originChatId\b"""
        )
        assertTrue(
            "TC-CRON-STREAMING-l (1): `setSessionVars(...)` must be called with `chatId = originChatId` " +
                "(the WIRE form captured from `origin.chat_id`), NOT `resolvedChatId`. " +
                "Reason: `HERMES_SESSION_CHAT_ID` is the ThreadLocal that `_originFromEnv()` will read " +
                "when the agent calls `cronjob(action=\"create\")` within this cron run — it MUST hold " +
                "a value that can later be fed to `WeixinAdapter.send(to_user_id=...)`. " +
                "run() body head:\n${runBody.take(2500)}",
            setSessionVarsWithWire.containsMatchIn(runBody)
        )

        // (2) setCronAutoDeliverVars must be called with chatId = originChatId (wire form).
        val setCronAutoDeliverVarsWithWire = Regex(
            """setCronAutoDeliverVars\s*\((?:[^()]|\([^)]*\))*?chatId\s*=\s*originChatId\b"""
        )
        assertTrue(
            "TC-CRON-STREAMING-l (2): `setCronAutoDeliverVars(...)` must be called with " +
                "`chatId = originChatId` (WIRE form). The KDoc of this function (SessionContext.kt:100-107) " +
                "explicitly documents `chat_id` as \"the inbound IM origin... so Scheduler can deliver " +
                "the result back to that same chat\" — Room UUIDs or history-prefixed forms MUST NOT " +
                "flow through here.",
            setCronAutoDeliverVarsWithWire.containsMatchIn(runBody)
        )

        // (3) Regression guard: neither call site may pass `chatId = resolvedChatId`.
        val setSessionVarsWithResolved = Regex(
            """setSessionVars\s*\((?:[^()]|\([^)]*\))*?chatId\s*=\s*resolvedChatId\b"""
        )
        val setCronAutoDeliverVarsWithResolved = Regex(
            """setCronAutoDeliverVars\s*\((?:[^()]|\([^)]*\))*?chatId\s*=\s*resolvedChatId\b"""
        )
        assertFalse(
            "TC-CRON-STREAMING-l (3a): `setSessionVars(...)` MUST NOT pass `chatId = resolvedChatId`. " +
                "`resolvedChatId` is a Room chat UUID (or `gw:` history form), NOT a wire chatId — " +
                "feeding it into `HERMES_SESSION_CHAT_ID` corrupts the origin of any cron created " +
                "from within this run. This is the 2026-07-07 bug.",
            setSessionVarsWithResolved.containsMatchIn(runBody)
        )
        assertFalse(
            "TC-CRON-STREAMING-l (3b): `setCronAutoDeliverVars(...)` MUST NOT pass " +
                "`chatId = resolvedChatId`. Same reason as (3a).",
            setCronAutoDeliverVarsWithResolved.containsMatchIn(runBody)
        )

        // (4) `resolvedChatId` must still exist in run() — the sidecar AgentEventBus filter and
        //     `EnhancedAIService.sendMessage(chatId=...)` (Room lookup) legitimately use it.
        //     Guard against accidental deletion.
        assertTrue(
            "TC-CRON-STREAMING-l (4): `resolvedChatId` reference must still exist in `run()` — " +
                "it is legitimately used by the sidecar AgentEventBus filter and " +
                "`EnhancedAIService.sendMessage(chatId=...)`. Do NOT delete it, just stop passing " +
                "it to session vars.",
            runBody.contains("resolvedChatId")
        )
    }

    // ---------------------------------------------------------------------
    // TC-CRON-STREAMING-m : dispatch sanitizes chatId before adapter call
    // ---------------------------------------------------------------------
    @Test
    fun `TC-CRON-STREAMING-m dispatch sanitizes chatId before adapter call`() {
        assertTrue(
            "TC-CRON-STREAMING-m: `CronAgentRunner.run` body not found.",
            runBody.isNotEmpty()
        )

        // (1) A wire-chatId sanitizer must exist in the source — either a private helper
        //     function or a top-level constant / property whose name signals intent.
        val sanitizerNamePattern = Regex(
            """\b(?:isWireChatId|isDirtyChatId|sanitizeWireChatId|assertWireChatId|""" +
                """validateWireChatId|isSuspiciousChatId)\b"""
        )
        assertTrue(
            "TC-CRON-STREAMING-m (1): source must expose a chatId sanitizer helper — a private fun / " +
                "top-level property named one of: isWireChatId / isDirtyChatId / sanitizeWireChatId / " +
                "assertWireChatId / validateWireChatId / isSuspiciousChatId. This is the defense-in-depth " +
                "layer that stops `errcode=-3` from ever reaching `WeixinAdapter.send`. " +
                "Source head (2500 chars):\n${source.take(2500)}",
            sanitizerNamePattern.containsMatchIn(source)
        )

        // (2) The sanitizer must recognize the `gw:` prefix (the classic history-chatId leak signal).
        assertTrue(
            "TC-CRON-STREAMING-m (2): sanitizer must reject `gw:`-prefixed values — that's the history " +
                "chatId form that was polluting sidecar dispatch (commit 99df3979 fixed the same bug " +
                "on the gateway path). Look for a literal `\"gw:\"` + `startsWith(` combination.",
            source.contains("\"gw:\"") && source.contains("startsWith(")
        )

        // (3) The sanitizer must also detect multi-segment `@im.wechat` chains (the triple-repeat
        //     signature from 99df3979's real-device log).
        assertTrue(
            "TC-CRON-STREAMING-m (3): sanitizer must detect multi-segment `@im.wechat` chains " +
                "(triple-repeat signature from the 2026-06-27 real-device log). Look for a literal " +
                "`\"@im.wechat\"` combined with `.split(` or a count-based check.",
            source.contains("\"@im.wechat\"") &&
                (source.contains(".split(") || source.contains("count("))
        )

        // (4) Sidecar dispatch (streaming path) must be guarded by the sanitizer. Locate the sidecar
        //     block by anchoring on `AssistantDelta`.
        val sidecarStart = runBody.indexOf("AssistantDelta")
        assertTrue(
            "TC-CRON-STREAMING-m (4a): AssistantDelta anchor missing — TC-CRON-STREAMING-a should " +
                "have caught this first.",
            sidecarStart >= 0
        )
        val sidecarSlice = runBody.substring(sidecarStart)
        val sanitizerCallInSidecar = sanitizerNamePattern.containsMatchIn(sidecarSlice)
        assertTrue(
            "TC-CRON-STREAMING-m (4b): sidecar dispatch block (from `AssistantDelta` anchor onwards) " +
                "must reference the sanitizer helper before calling `dispatchOutgoing(...)`. " +
                "Sidecar slice head:\n${sidecarSlice.take(1500)}",
            sanitizerCallInSidecar
        )

        // (5) `deliver(...)` fallback must also be guarded — the non-streaming path is another dispatch
        //     door and must not become a bypass.
        assertTrue(
            "TC-CRON-STREAMING-m (5a): `deliver(...)` body not found.",
            deliverBody.isNotEmpty()
        )
        val sanitizerCallInDeliver = sanitizerNamePattern.containsMatchIn(deliverBody)
        assertTrue(
            "TC-CRON-STREAMING-m (5b): `deliver(...)` fallback path must also reference the sanitizer " +
                "before `dispatchOutgoing(...)`. Otherwise a dirty chatId that skipped streaming would " +
                "still hit `WeixinAdapter.send` via the fallback and produce `errcode=-3`.",
            sanitizerCallInDeliver
        )

        // (6) Rejection path must log an error line — `errcode=-3` used to be the only evidence a
        //     bad chatId slipped through; now we want an in-app `CronFileLogger` breadcrumb.
        val logsDirty = source.contains("dirty") || source.contains("invalid wire") ||
            source.contains("suspicious chat")
        assertTrue(
            "TC-CRON-STREAMING-m (6): rejection path must call `CronFileLogger.e(...)` / `.w(...)` with " +
                "a message containing `dirty` / `invalid wire` / `suspicious chat` so the failure is " +
                "greppable in `/sdcard/Download/Hermes/cron_logs/cron.log`.",
            source.contains("CronFileLogger") && logsDirty
        )
    }

    // =====================================================================
    // helpers  (same as CronStreamingDispatchWiringTest — kept local for grepability)
    // =====================================================================

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
