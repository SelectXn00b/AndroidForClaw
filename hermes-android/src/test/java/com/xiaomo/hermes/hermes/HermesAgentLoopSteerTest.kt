package com.xiaomo.hermes.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * R-AGENT-036: HermesAgentLoop.steer() interface (mid-turn user-guidance kernel).
 *
 * Aligns with Python upstream:
 *  - Field declarations:                        run_agent.py:945-953
 *  - `steer()` method:                          run_agent.py:3608-3642
 *  - `_drain_pending_steer()`:                  run_agent.py:3644-3658
 *  - `_apply_pending_steer_to_tool_results()`:  run_agent.py:3660-3721
 *  - `clear_interrupt()` drops pending steer:   run_agent.py:3599-3606
 *
 * **Why this exists**: Python `AIAgent.steer(text)` lets a user inject guidance
 * mid-turn — without interrupting — by stashing text and appending it to the
 * last `role:"tool"` content as `"\n\nUser guidance: {text}"` once the current
 * tool batch finishes. Message-role alternation is preserved (no new user
 * message inserted). This R only adds the interface and field; the 6
 * consumption sites in the agent loop are wired in R-AGENT-037.
 */
class HermesAgentLoopSteerTest {

    // ---- minimal fakes (consumption points not exercised here) ----

    private class FakeDispatcher : ToolDispatcher {
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?
        ): String = "{}"
    }

    private class FakeServer : ChatCompletionServer {
        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?
        ): ChatCompletionResponse? = ChatCompletionResponse(
            choices = listOf(Choice(AssistantMessage(content = "done", toolCalls = null)))
        )
    }

    private fun newLoop(): HermesAgentLoop = HermesAgentLoop(
        server = FakeServer(),
        toolDispatcher = FakeDispatcher(),
    )

    // ---- TC-AGENT-036-a: empty / whitespace steer is rejected ----
    /**
     * TC-AGENT-036-a: `steer("")` and `steer("   ")` must both return false
     * and leave `_pendingSteer` unchanged. Mirrors Python `:3626-3627`.
     */
    @Test fun `TC-AGENT-036-a empty or whitespace steer is rejected`() {
        val loop = newLoop()
        assertFalse(
            "steer with empty string must return false",
            loop.steer("")
        )
        assertFalse(
            "steer with whitespace-only string must return false",
            loop.steer("   ")
        )
        assertFalse(
            "steer with newline-only string must return false",
            loop.steer("\n\t  \n")
        )
        assertNull(
            "_pendingSteer must remain unset after rejected steers",
            loop._drainPendingSteer()
        )
    }

    // ---- TC-AGENT-036-b: basic steer stores trimmed text ----
    /**
     * TC-AGENT-036-b: `steer("hello")` must return true and stash the trimmed
     * text in `_pendingSteer`. Mirrors Python `:3628, 3637-3641`.
     */
    @Test fun `TC-AGENT-036-b basic steer stores trimmed text`() {
        val loop = newLoop()
        assertTrue(loop.steer("  hello  "))
        assertEquals("hello", loop._drainPendingSteer())
    }

    // ---- TC-AGENT-036-c: multiple steers concatenate with newline ----
    /**
     * TC-AGENT-036-c: Sequential `steer()` calls must concatenate with `"\n"`.
     * Mirrors Python `:3637-3641`.
     */
    @Test fun `TC-AGENT-036-c multiple steers concatenate with newline`() {
        val loop = newLoop()
        assertTrue(loop.steer("a"))
        assertTrue(loop.steer("b"))
        assertTrue(loop.steer("c"))
        assertEquals("a\nb\nc", loop._drainPendingSteer())
    }

    // ---- TC-AGENT-036-d: drain returns and clears atomically ----
    /**
     * TC-AGENT-036-d: First `_drainPendingSteer()` returns stashed text;
     * second call returns null. Mirrors Python `:3650-3658`.
     */
    @Test fun `TC-AGENT-036-d drain returns and clears atomically`() {
        val loop = newLoop()
        loop.steer("x")
        assertEquals("x", loop._drainPendingSteer())
        assertNull(
            "Second drain on empty slot must return null",
            loop._drainPendingSteer()
        )
    }

    // ---- TC-AGENT-036-e: concurrent steer is thread-safe ----
    /**
     * TC-AGENT-036-e: 100 concurrent `steer()` calls (different texts) — drain
     * must contain all 100 texts (no character loss / interleave); line count
     * must be 100. Mirrors Python `_pending_steer_lock` (:953, :3637-3641).
     */
    @Test fun `TC-AGENT-036-e concurrent steer is thread-safe`() {
        val loop = newLoop()
        val n = 100
        val pool = Executors.newFixedThreadPool(16)
        val gate = CountDownLatch(1)
        val done = CountDownLatch(n)
        for (i in 0 until n) {
            pool.submit {
                gate.await()
                loop.steer("msg-$i")
                done.countDown()
            }
        }
        gate.countDown()
        assertTrue(
            "All concurrent steers must complete within 5s",
            done.await(5, TimeUnit.SECONDS)
        )
        pool.shutdown()
        val drained = loop._drainPendingSteer()
        assertNotNull("drain must return non-null after concurrent steers", drained)
        val lines = drained!!.split("\n")
        assertEquals(
            "Drained text must contain exactly $n lines (one per steer)",
            n, lines.size
        )
        // Every msg-i must appear exactly once
        for (i in 0 until n) {
            val needle = "msg-$i"
            val count = lines.count { it == needle }
            assertEquals("msg-$i must appear exactly once in concurrent drain", 1, count)
        }
    }

    // ---- TC-AGENT-036-f: apply injects to last role tool with marker ----
    /**
     * TC-AGENT-036-f: `_applyPendingSteerToToolResults(messages, 1)` must
     * append `"\n\nUser guidance: hi"` to the last `role:"tool"` content.
     * Mirrors Python `:3703, 3716`.
     */
    @Test fun `TC-AGENT-036-f apply injects to last role tool with marker`() {
        val loop = newLoop()
        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "do X"),
            mapOf(
                "role" to "assistant",
                "content" to "",
                "tool_calls" to listOf(mapOf("id" to "tc-1"))
            ),
            mapOf("role" to "tool", "tool_call_id" to "tc-1", "content" to "r1")
        )
        loop.steer("hi")
        loop._applyPendingSteerToToolResults(messages, 1)
        assertEquals(
            "Last tool message content must have marker appended",
            "r1\n\nUser guidance: hi",
            messages[2]["content"]
        )
        assertNull(
            "After successful apply, pending must be drained",
            loop._drainPendingSteer()
        )
    }

    // ---- TC-AGENT-036-g: apply with no tool tail re-stashes text ----
    /**
     * TC-AGENT-036-g: When the recent tail has no `role:"tool"` message,
     * the steer text must be re-stashed in `_pendingSteer` so the caller's
     * fallback can deliver it as a normal next-turn user message.
     * Mirrors Python `:3688-3702`.
     */
    @Test fun `TC-AGENT-036-g apply with no tool tail re-stashes text`() {
        val loop = newLoop()
        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "do X"),
            mapOf("role" to "assistant", "content" to "ok")
        )
        loop.steer("hi")
        // numToolMsgs = 0 → early return (no tool batch to inject into)
        loop._applyPendingSteerToToolResults(messages, 0)
        assertEquals(
            "messages must be untouched when no tool tail",
            "ok", messages[1]["content"]
        )
        assertEquals(
            "Pending must still hold steer text after no-op apply",
            "hi", loop._drainPendingSteer()
        )
    }

    // ---- TC-AGENT-036-h: apply preserves multimodal content blocks ----
    /**
     * TC-AGENT-036-h: When tool message content is `List<Map>` (multimodal),
     * apply must preserve the block list and append a text block with the
     * marker (lstripped per Python `:3710`).
     */
    @Test fun `TC-AGENT-036-h apply preserves multimodal content blocks`() {
        val loop = newLoop()
        val initialBlocks = listOf(
            mapOf("type" to "text", "text" to "r1")
        )
        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "x"),
            mapOf(
                "role" to "assistant",
                "content" to "",
                "tool_calls" to listOf(mapOf("id" to "tc-1"))
            ),
            mapOf("role" to "tool", "tool_call_id" to "tc-1", "content" to initialBlocks)
        )
        loop.steer("hi")
        loop._applyPendingSteerToToolResults(messages, 1)
        @Suppress("UNCHECKED_CAST")
        val newContent = messages[2]["content"] as? List<Map<String, Any?>>
        assertNotNull("Multimodal content must remain a List", newContent)
        assertEquals(
            "Original block + appended text block = 2",
            2, newContent!!.size
        )
        assertEquals(
            "First block must be unchanged",
            "r1", newContent[0]["text"]
        )
        assertEquals(
            "Appended block must be type=text",
            "text", newContent[1]["type"]
        )
        assertEquals(
            "Appended block text must be 'User guidance: hi' (lstripped marker)",
            "User guidance: hi", newContent[1]["text"]
        )
    }

    // ---- TC-AGENT-036-i: clearPendingSteer drops pending text ----
    /**
     * TC-AGENT-036-i: `clearPendingSteer()` must drop any pending steer.
     * This is what `EnhancedAIService.cancelConversation` calls on hard
     * cancel (Python `:3599-3606` — hard interrupt supersedes pending steer).
     */
    @Test fun `TC-AGENT-036-i clearPendingSteer drops pending text`() {
        val loop = newLoop()
        loop.steer("x")
        loop.clearPendingSteer()
        assertNull(
            "After clearPendingSteer, drain must return null",
            loop._drainPendingSteer()
        )
    }
}
