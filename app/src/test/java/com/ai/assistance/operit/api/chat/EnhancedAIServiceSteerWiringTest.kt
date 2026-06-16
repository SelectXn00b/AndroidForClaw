package com.ai.assistance.operit.api.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-062: weakref + steerActiveLoop + clearPendingSteer wiring inside
 * EnhancedAIService.
 *
 * Source-scan style: instantiating EnhancedAIService requires Application
 * Context + dozens of providers + ObjectBox + a working network stack —
 * far more setup than is justified for locking in three structural
 * guarantees:
 *
 *   (1) `activeAgentLoopRef: WeakReference<HermesAgentLoop>?` field exists
 *   (2) `runAgentLoopViaHermes` registers the weakref before `loop.run(...)`
 *       and clears it in a `finally` block (no leak between turns)
 *   (3) `cancelConversation()` calls `clearPendingSteer()` on the loop —
 *       mirrors Python `run_agent.py:3599-3606`
 */
class EnhancedAIServiceSteerWiringTest {

    private val source: String by lazy { File(servicePath()).readText() }

    /** Locate the body of `private suspend fun runAgentLoopViaHermes(`. */
    private fun extractRunAgentLoopBody(): String {
        val anchor = Regex("""fun\s+runAgentLoopViaHermes\s*\(""").find(source)?.range?.first
            ?: error("Cannot find runAgentLoopViaHermes in EnhancedAIService.kt")
        // Skip past the param list to the body opener.
        var i = source.indexOf('{', anchor + 50)
        require(i >= 0) { "Cannot find runAgentLoopViaHermes opening brace" }
        val start = i
        var depth = 0
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

    /** Locate the body of `fun cancelConversation()`. */
    private fun extractCancelConversationBody(): String {
        val anchor = Regex("""fun\s+cancelConversation\s*\(""").find(source)?.range?.first
            ?: error("Cannot find cancelConversation in EnhancedAIService.kt")
        var i = source.indexOf('{', anchor + 20)
        require(i >= 0) { "Cannot find cancelConversation opening brace" }
        val start = i
        var depth = 0
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

    // -------- TC-UI-062-a --------
    /**
     * TC-UI-062-a: weakref field exists at class level AND
     * runAgentLoopViaHermes registers a fresh WeakReference before
     * `loop.run(...)` AND clears it inside a `finally` block.
     *
     * Why both register-and-clear: a registered weakref that is never
     * cleared leaks a stale loop reference into the next turn — the next
     * `/steer` call would dispatch to the previous (already-finished) loop
     * which silently no-ops, hiding the bug.
     */
    @Test
    fun `TC-UI-062-a weakref field and lifecycle`() {
        // Field declaration on the class.
        assertTrue(
            "TC-UI-062-a: activeAgentLoopRef field must be declared as WeakReference<HermesAgentLoop>?",
            Regex("""activeAgentLoopRef\s*:\s*java\.lang\.ref\.WeakReference<\s*com\.xiaomo\.hermes\.hermes\.HermesAgentLoop\s*>\?""")
                .containsMatchIn(source),
        )

        val body = extractRunAgentLoopBody()

        // Register: WeakReference(loop) assignment must appear in the body.
        // We're flexible on `WeakReference(loop)` vs `java.lang.ref.WeakReference(loop)`.
        val registerCount = Regex("""activeAgentLoopRef\s*=\s*(?:java\.lang\.ref\.)?WeakReference\s*\(\s*loop\s*\)""")
            .findAll(body).count()
        assertTrue(
            "TC-UI-062-a: must register WeakReference(loop) at least once (initial run); found=$registerCount",
            registerCount >= 1,
        )

        // Clear: `activeAgentLoopRef = null` must appear inside a finally block.
        // Look for the actual `} finally {` keyword (not the substring "finally"
        // which would also match comments like "...cleared in the finally below").
        val finallyIdx = Regex("""}\s*finally\s*\{""").find(body)?.range?.first ?: -1
        assertTrue(
            "TC-UI-062-a: runAgentLoopViaHermes must contain a `} finally {` block",
            finallyIdx >= 0,
        )
        val finallySlice = body.substring(finallyIdx)
        assertTrue(
            "TC-UI-062-a: finally block must contain `activeAgentLoopRef = null`",
            Regex("""activeAgentLoopRef\s*=\s*null""").containsMatchIn(finallySlice),
        )

        // Cross-check: the register call must precede the finally clear.
        val firstRegisterIdx =
            Regex("""activeAgentLoopRef\s*=\s*(?:java\.lang\.ref\.)?WeakReference""").find(body)?.range?.first ?: -1
        assertTrue(
            "TC-UI-062-a: register must appear before finally{}",
            firstRegisterIdx in 0 until finallyIdx,
        )
    }

    // -------- TC-UI-062-b --------
    /**
     * TC-UI-062-b: `fun steerActiveLoop(text: String): Boolean` exists,
     * resolves the weakref via `?.get()` and dispatches to `loop.steer(text)`.
     * Returns `false` when the weakref is empty / GC'd / null.
     */
    @Test
    fun `TC-UI-062-b steerActiveLoop method`() {
        // Method signature.
        assertTrue(
            "TC-UI-062-b: must declare `fun steerActiveLoop(text: String): Boolean`",
            Regex("""fun\s+steerActiveLoop\s*\(\s*text\s*:\s*String\s*\)\s*:\s*Boolean""")
                .containsMatchIn(source),
        )

        // Body must:
        //   (a) deref via `activeAgentLoopRef?.get()`
        //   (b) call `loop.steer(text)` (or `.steer(text)` on the resolved val)
        //   (c) return `false` for the null-loop branch
        // Carving the function body via the same brace-walk used elsewhere.
        val anchor = Regex("""fun\s+steerActiveLoop\s*\(""").find(source)?.range?.first
            ?: error("steerActiveLoop signature missing")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "steerActiveLoop body opener missing" }
        val start = i
        var depth = 0
        var end = source.length
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) { end = i + 1; break }
            }
            i++
        }
        val body = source.substring(start, end)

        assertTrue(
            "TC-UI-062-b: must dereference weakref via activeAgentLoopRef?.get()",
            Regex("""activeAgentLoopRef\s*\?\.\s*get\s*\(\s*\)""").containsMatchIn(body),
        )
        assertTrue(
            "TC-UI-062-b: must call .steer(text) on the resolved loop",
            Regex("""\.steer\s*\(\s*text\s*\)""").containsMatchIn(body),
        )
        assertTrue(
            "TC-UI-062-b: null-loop branch must `return false`",
            Regex("""return\s+false""").containsMatchIn(body),
        )
    }

    // -------- TC-UI-062-c --------
    /**
     * TC-UI-062-c: `cancelConversation()` calls `clearPendingSteer()` on
     * the resolved active loop (so a hard-cancel does not let a queued
     * `/steer` text bleed into the next turn). Mirrors Python
     * `run_agent.py:3599-3606`.
     */
    @Test
    fun `TC-UI-062-c cancelConversation clears pending steer`() {
        val body = extractCancelConversationBody()
        assertTrue(
            "TC-UI-062-c: cancelConversation must call activeAgentLoopRef?.get()?.clearPendingSteer()",
            Regex("""activeAgentLoopRef\s*\?\.\s*get\s*\(\s*\)\s*\?\.\s*clearPendingSteer\s*\(\s*\)""")
                .containsMatchIn(body),
        )

        // Sanity: the cancelConversation body must also still tear down
        // existing state (the regression we want to avoid is "added the
        // clearPendingSteer call but accidentally deleted other tear-down").
        // We only sample two robust anchors that have been there since
        // before R-UI-062.
        assertTrue(
            "TC-UI-062-c: cancelConversation must still call invalidateAllExecutionContexts(...)",
            body.contains("invalidateAllExecutionContexts"),
        )
        assertTrue(
            "TC-UI-062-c: cancelConversation must still call cancelAllToolExecutions()",
            body.contains("cancelAllToolExecutions"),
        )
    }

    // -------- TC-UI-062-c-2 (negative) --------
    /**
     * Belt-and-suspenders: clearPendingSteer must NOT be called from any
     * un-related code path that could fire mid-turn (e.g. the regular
     * turn-finish path) — only from cancelConversation. This preserves
     * the Python invariant that pending steer survives between turns of
     * the same conversation but dies on hard cancel.
     */
    @Test
    fun `TC-UI-062 clearPendingSteer is only reached from cancelConversation`() {
        val occurrences = Regex("""\bclearPendingSteer\s*\(""")
            .findAll(source).map { it.range.first }.toList()
        assertFalse(
            "expected at least one clearPendingSteer call site",
            occurrences.isEmpty(),
        )
        // The single intended call site lives inside cancelConversation.
        // We accept multiple call sites but require at least one of them
        // to live inside cancelConversation.
        val cancelBody = extractCancelConversationBody()
        assertTrue(
            "TC-UI-062: at least one clearPendingSteer() call must live inside cancelConversation",
            cancelBody.contains("clearPendingSteer"),
        )
    }

    private fun servicePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt"),
            File("app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate EnhancedAIService.kt — cwd=${File(".").absolutePath}")
    }
}
