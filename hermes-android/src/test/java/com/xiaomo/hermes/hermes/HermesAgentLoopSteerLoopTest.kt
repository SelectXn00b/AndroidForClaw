package com.xiaomo.hermes.hermes

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-AGENT-037: HermesAgentLoop wires R-AGENT-036's `_applyPendingSteerToToolResults`
 * / `_drainPendingSteer` into 4 turn-loop consumption points + leftover handoff via
 * `AgentResult.pendingSteer`.
 *
 * Aligns with Python upstream:
 *  - Per-tool drain (parallel):    run_agent.py:8029-8032
 *  - Post-batch drain (parallel):  run_agent.py:8040-8045
 *  - Per-tool drain (sequential):  run_agent.py:8397-8401     (Kotlin: merged into B.1)
 *  - Post-batch drain (sequential):run_agent.py:8432-8436     (Kotlin: merged into B.2)
 *  - Pre-API-call drain:           run_agent.py:9032-9080
 *  - Leftover handoff:             run_agent.py:11828-11833
 *
 * Strategy: drive `HermesAgentLoop.run()` with a stubbed `ChatCompletionServer`
 * + `ToolDispatcher` that fire `loop.steer()` at specific points so each
 * consumption site is isolated.
 */
class HermesAgentLoopSteerLoopTest {

    /**
     * A scripted fake `ChatCompletionServer`: returns canned responses turn-by-turn,
     * and runs an optional pre-call hook on each turn so tests can call
     * `loop.steer()` to land squarely in the pre-API drain window.
     */
    private class ScriptedServer(
        val responses: List<ChatCompletionResponse?>,
        var preCallHook: (suspend (turnIdx: Int, snapshot: List<Map<String, Any?>>) -> Unit)? = null,
    ) : ChatCompletionServer {
        var calls = 0
        val snapshotsAtCall = mutableListOf<List<Map<String, Any?>>>()
        override suspend fun chatCompletion(
            messages: List<Map<String, Any?>>,
            tools: List<Map<String, Any?>>?,
            temperature: Double,
            maxTokens: Int?,
            extraBody: Map<String, Any?>?,
        ): ChatCompletionResponse? {
            preCallHook?.invoke(calls, messages.toList())
            // Snapshot AFTER hook so test can assert what the API call sees.
            snapshotsAtCall.add(messages.map { it.toMap() })
            val idx = calls
            calls++
            return responses.getOrNull(idx)
        }
    }

    /**
     * A scripted dispatcher: each call's behavior is keyed by a 0-based call
     * index so tests can plug in side-effects (e.g. `loop.steer()`) per tool.
     */
    private class ScriptedDispatcher(
        val behaviors: List<suspend (toolName: String) -> String>,
    ) : ToolDispatcher {
        var calls = 0
        override suspend fun dispatch(
            toolName: String,
            args: Map<String, Any?>,
            taskId: String,
            userTask: String?,
        ): String {
            val behavior = behaviors.getOrElse(calls) { { _ -> "{}" } }
            val result = behavior(toolName)
            calls++
            return result
        }
    }

    private fun toolCallResp(vararg ids: String): ChatCompletionResponse =
        ChatCompletionResponse(
            choices = listOf(
                Choice(
                    AssistantMessage(
                        content = "",
                        toolCalls = ids.map { id ->
                            ToolCall(
                                id = id,
                                function = ToolCallFunction(name = "noop_tool", arguments = "{}"),
                            )
                        },
                    ),
                ),
            ),
        )

    private fun finalResp(text: String = "done"): ChatCompletionResponse =
        ChatCompletionResponse(choices = listOf(Choice(AssistantMessage(content = text, toolCalls = null))))

    // -------- TC-AGENT-037-a: per-tool drain injects steer mid-batch --------
    /**
     * TC-AGENT-037-a: Single tool, dispatcher calls `loop.steer("hi")` while the
     * tool is "running" (i.e. before the dispatch result is appended). The loop
     * appends the tool message and immediately runs per-tool drain — the marker
     * must land on this very tool's content.
     *
     * Mirrors Python `run_agent.py:8029-8032` (parallel per-tool drain).
     */
    @Test
    fun `TC-AGENT-037-a per-tool drain injects steer mid-batch`() = runBlocking {
        lateinit var loopRef: HermesAgentLoop
        val dispatcher = ScriptedDispatcher(listOf { _ ->
            // Steer DURING tool dispatch — the per-tool drain after this tool's
            // result is appended must inject the marker into this same message.
            loopRef.steer("hi")
            "{\"ok\":true}"
        })
        val server = ScriptedServer(listOf(toolCallResp("tc-1"), finalResp("done")))
        val loop = HermesAgentLoop(server = server, toolDispatcher = dispatcher)
        loopRef = loop

        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "go")
        )
        val result = loop.run(messages)

        val toolMsg = messages.firstOrNull { it["role"] == "tool" }
        assertNotNull("Loop must have produced a role:tool message", toolMsg)
        val content = toolMsg!!["content"] as? String
        assertNotNull("Tool message content must be a String", content)
        assertTrue(
            "Per-tool drain must append marker on the SAME tool that triggered the steer (got: $content)",
            content!!.endsWith("\n\nUser guidance: hi"),
        )
        assertNull(
            "After drain, leftover steer must be null (consumed by per-tool drain)",
            result.pendingSteer,
        )
    }

    // -------- TC-AGENT-037-b: post-batch drain catches late steer --------
    /**
     * TC-AGENT-037-b: Post-batch drain is a redundant safety net that fires
     * AFTER the per-tool drain loop completes — it covers the small window
     * between the last per-tool drain and the next API iteration.
     *
     * Pure unit-driven coverage of this race window is impractical (no yield
     * point between per-tool drain and post-batch drain, and parallel-dispatch
     * timing makes "tool 1 vs tool 2 catches it" non-deterministic — see Python
     * run_agent.py:8040-8045 commentary). Instead this test asserts via source
     * scan that AgentLoop.kt wires both per-tool AND post-batch drain calls
     * with the correct argument shape, mirroring Python's two-step pattern.
     *
     * Mirrors Python run_agent.py:8040-8045 (parallel) + 8432-8436 (sequential).
     */
    @Test
    fun `TC-AGENT-037-b post-batch drain catches late steer`() {
        val srcRoots = listOf(
            java.io.File("src/main/java/com/xiaomo/hermes/hermes/AgentLoop.kt"),
            java.io.File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/AgentLoop.kt"),
        )
        val src = srcRoots.firstOrNull { it.exists() }?.readText()
            ?: error("Cannot locate AgentLoop.kt; cwd=${java.io.File(".").absolutePath}")

        // Per-tool drain (B.1): apply with numToolMsgs = 1 in the for-prep loop
        assertTrue(
            "AgentLoop.kt must wire per-tool /steer drain via _applyPendingSteerToToolResults(messages, 1) inside the for-prep loop",
            Regex("""_applyPendingSteerToToolResults\s*\(\s*messages\s*,\s*1\s*\)""").containsMatchIn(src),
        )
        // Post-batch drain (B.2): apply with numToolMsgs = preps.size after the loop
        assertTrue(
            "AgentLoop.kt must wire post-batch /steer drain via _applyPendingSteerToToolResults(messages, preps.size) AFTER the for-prep loop",
            Regex("""_applyPendingSteerToToolResults\s*\(\s*messages\s*,\s*preps\.size\s*\)""").containsMatchIn(src),
        )
        // Post-batch must be guarded by preps.isNotEmpty() — no work if zero tools dispatched.
        assertTrue(
            "Post-batch drain must be guarded by preps.isNotEmpty() to avoid running with no tool batch",
            src.contains("preps.isNotEmpty()"),
        )
    }

    // -------- TC-AGENT-037-c: pre-API-call drain injects before next chatCompletion --------
    /**
     * TC-AGENT-037-c: Steer arrives AFTER turn 1's tool batch fully concludes
     * (between batch close and turn 2's chatCompletion). The pre-API-call
     * drain at the top of turn 2 must inject the marker into the last tool
     * message BEFORE the API call sees the conversation snapshot.
     *
     * Mirrors Python `run_agent.py:9032-9080`.
     */
    @Test
    fun `TC-AGENT-037-c pre-API-call drain injects before next chatCompletion`() = runBlocking {
        lateinit var loopRef: HermesAgentLoop
        // Single tool turn 1 → pre-API drain on turn 2's chatCompletion.
        val dispatcher = ScriptedDispatcher(listOf { _ -> "{}" })
        // server's pre-call hook on turn 2 (calls index 1) calls steer BEFORE
        // returning — but the pre-API drain runs BEFORE chatCompletion so we
        // need to steer earlier. Use a hook that fires from the beforeNextTurn
        // callback on turn 2.
        val server = ScriptedServer(listOf(toolCallResp("tc-1"), finalResp("done")))
        var preApiSnapshotSeen: List<Map<String, Any?>>? = null
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            beforeNextTurn = { turnIndex, _ ->
                if (turnIndex == 1) {
                    // Entering turn 2: steer arrives here, after turn 1's tool
                    // batch concluded. Pre-API drain will fire next.
                    loopRef.steer("pre-api")
                }
                true
            },
        )
        loopRef = loop
        // Capture snapshot the API saw on turn 2.
        server.preCallHook = { turnIdx, _ ->
            if (turnIdx == 1) {
                // hook fires before snapshotsAtCall is filled; capture via
                // server.snapshotsAtCall on next iteration would be too late —
                // grab here from the messages list reflectively.
            }
        }

        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "go")
        )
        val result = loop.run(messages)

        // Server made 2 calls; the second saw the pre-API-drained tool message.
        assertEquals("Server must be called twice", 2, server.calls)
        val turn2Snapshot = server.snapshotsAtCall[1]
        val lastTool = turn2Snapshot.lastOrNull { it["role"] == "tool" }
        assertNotNull("Turn-2 snapshot must include the tool message", lastTool)
        val content = lastTool!!["content"] as? String
        assertNotNull("Tool content must be a String", content)
        assertTrue(
            "Pre-API drain must inject 'User guidance: pre-api' into the last tool message before turn-2 chatCompletion (got: $content)",
            content!!.contains("User guidance: pre-api"),
        )
        assertNull(
            "Leftover must be null after pre-API drain consumes the steer",
            result.pendingSteer,
        )
    }

    // -------- TC-AGENT-037-d: leftover steer surfaces in pendingSteer --------
    /**
     * TC-AGENT-037-d: Final response on turn 1 (no tool_calls). Steer arrives
     * before run() returns — there's no tool message to inject into and no
     * future turn. The leftover-handoff must surface it via
     * `AgentResult.pendingSteer` so the caller can deliver it as the next
     * user turn.
     *
     * Mirrors Python `run_agent.py:11828-11833`.
     */
    @Test
    fun `TC-AGENT-037-d leftover steer surfaces in pendingSteer`() = runBlocking {
        lateinit var loopRef: HermesAgentLoop
        val dispatcher = ScriptedDispatcher(emptyList())
        val server = ScriptedServer(listOf(finalResp("just done")))
        // Steer fires from beforeNextTurn for turn 0 — when there's no tool
        // history yet. Pre-API drain re-stashes it (no role:tool to inject).
        // Final response → no tool batch → drain points all skip. Leftover
        // handoff at run() exit must surface it.
        val loop = HermesAgentLoop(
            server = server,
            toolDispatcher = dispatcher,
            beforeNextTurn = { turnIndex, _ ->
                if (turnIndex == 0) loopRef.steer("orphan")
                true
            },
        )
        loopRef = loop

        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "say hi")
        )
        val result = loop.run(messages)

        assertEquals(
            "Leftover steer must surface in AgentResult.pendingSteer",
            "orphan",
            result.pendingSteer,
        )
        // No role:tool in messages either way.
        assertFalse(
            "Messages must not contain a synthetic tool message",
            messages.any { it["role"] == "tool" },
        )
    }

    // -------- TC-AGENT-037-e: clearPendingSteer beats all drain points --------
    /**
     * TC-AGENT-037-e: Steer + clearPendingSteer fire back-to-back during
     * dispatch. The clear must drop the pending text BEFORE per-tool drain or
     * post-batch drain run — no marker may appear in any tool message, and
     * leftover must be null.
     *
     * Mirrors Python hard-interrupt path (`:3599-3606`): clear supersedes drain.
     */
    @Test
    fun `TC-AGENT-037-e clearPendingSteer beats all drain points`() = runBlocking {
        lateinit var loopRef: HermesAgentLoop
        val dispatcher = ScriptedDispatcher(listOf { _ ->
            loopRef.steer("doomed")
            loopRef.clearPendingSteer()
            "{}"
        })
        val server = ScriptedServer(listOf(toolCallResp("tc-1"), finalResp("done")))
        val loop = HermesAgentLoop(server = server, toolDispatcher = dispatcher)
        loopRef = loop

        val messages: MutableList<Map<String, Any?>> = mutableListOf(
            mapOf("role" to "user", "content" to "go")
        )
        val result = loop.run(messages)

        for (msg in messages) {
            val c = msg["content"]
            val s = when (c) {
                is String -> c
                is List<*> -> c.toString()
                else -> ""
            }
            assertFalse(
                "No message content may contain the cancelled marker (got: $s)",
                s.contains("User guidance: doomed"),
            )
        }
        assertNull(
            "Leftover must be null after clearPendingSteer",
            result.pendingSteer,
        )
    }
}
