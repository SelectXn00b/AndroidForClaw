package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TC-CRON-STREAMING-f (R-CRON-STREAMING-001): subscription-readiness guarantee.
 *
 * Background: `AgentEventBus.events` is a `SharedFlow(replay=0)`. `launch { collect {...} }`
 * only enqueues the collector coroutine; the actual subscription registration with
 * the SharedFlow happens asynchronously when the collector runs. If
 * `enhancedService.sendMessage(...)` triggers the HermesAgentLoop and the loop emits
 * all per-turn `AssistantDelta` events BEFORE the sidecar collector finishes
 * registering, every early event is silently dropped (replay=0), the sidecar
 * dispatches 0 messages, and `deliver(...)` falls back to the bundled main-path
 * send — manifested as the user-reported "messages didn't come out one by one,
 * they all came at once" bug (2026-06-25).
 *
 * The fix: route the sidecar collector through `onSubscription { ready.complete(Unit) }`
 * (Kotlin coroutines `Flow.onSubscription` runs AFTER the subscription is
 * registered with the upstream SharedFlow) and have the main path `await()` that
 * `CompletableDeferred` before invoking `sendMessage`.
 *
 * Source-scan only — same rationale as `CronStreamingDispatchWiringTest`:
 * the real timing race needs a live `EnhancedAIService` + Android coroutines.
 * Literal-level wiring (`CompletableDeferred`, `onSubscription`, `.await()`
 * before `sendMessage(`) is sufficient to prevent regression.
 */
class CronStreamingSubscriptionReadyWiringTest {

    private val source: String by lazy { stripKotlinComments(File(runnerPath()).readText()) }

    @Test
    fun `TC-CRON-STREAMING-f run awaits sidecar subscription before triggering agent`() {
        // (1) Must reference CompletableDeferred (the ready signal carrier).
        assertTrue(
            "TC-CRON-STREAMING-f: `CronAgentRunner.kt` must reference `CompletableDeferred` " +
                "to carry the subscription-ready signal from sidecar collector to main path.",
            source.contains("CompletableDeferred")
        )

        // (2) Must reference onSubscription (the only place Kotlin Flow lets us know
        //     the subscription is actually registered with the upstream SharedFlow).
        assertTrue(
            "TC-CRON-STREAMING-f: `CronAgentRunner.kt` must reference `onSubscription` " +
                "so the sidecar can signal readiness AFTER the SharedFlow subscription " +
                "is registered (not just after `launch { collect }` enqueues the coroutine).",
            source.contains("onSubscription")
        )

        // (3) Must call `.await()` on the ready signal.
        assertTrue(
            "TC-CRON-STREAMING-f: `CronAgentRunner.kt` must call `.await()` on the ready " +
                "signal — otherwise the main path won't actually wait for the sidecar " +
                "subscription to be live before triggering the agent.",
            source.contains(".await()")
        )

        // (4) The `.await()` must occur BEFORE `enhancedService.sendMessage(`.
        //     Otherwise the wait is pointless — sendMessage triggers the agent loop
        //     which in turn emits AssistantDelta into the SharedFlow.
        //     Source comments are stripped via `stripKotlinComments` so we hit the
        //     real call site, not docstring references.
        val awaitIdx = source.indexOf(".await()")
        val sendIdx = source.indexOf("enhancedService.sendMessage(")
        assertTrue(
            "TC-CRON-STREAMING-f: `.await()` (idx=$awaitIdx) must appear BEFORE " +
                "`enhancedService.sendMessage(` (idx=$sendIdx) in source order. Otherwise " +
                "the SharedFlow(replay=0) race window stays open and the sidecar drops " +
                "early `AssistantDelta` events — manifesting as the 'all messages bundled " +
                "into one' bug.",
            awaitIdx in 0 until sendIdx
        )
    }

    /**
     * Strip Kotlin `/* ... */` (incl. KDoc `/** ... */`) block comments and `// ...`
     * line comments, preserving newlines so that line numbers in failure messages
     * remain meaningful.  Naive — does not honor string literals — but sufficient
     * for this source-scan test because no string literal in CronAgentRunner.kt
     * contains the literals we search for.
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
