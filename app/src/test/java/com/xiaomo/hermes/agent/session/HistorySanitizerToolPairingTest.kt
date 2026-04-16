package com.xiaomo.hermes.agent.session

import com.xiaomo.hermes.providers.llm.Message
import com.xiaomo.hermes.providers.llm.ToolCall
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HistorySanitizer.repairToolUseResultPairing
 * Aligned with OpenClaw c3972982b5 (sanitize malformed replay tool calls)
 *
 * Key behaviors tested:
 * 1. Aborted assistant retains real tool results (preserveErroredAssistantResults)
 * 2. Malformed tool calls (blank id) are filtered
 * 3. Normal pairing still works (synthetic results for missing)
 */
class HistorySanitizerToolPairingTest {

    @Test
    fun `aborted assistant retains real tool results for surviving calls`() {
        val messages = listOf(
            Message(role = "user", content = "do something"),
            Message(
                role = "assistant",
                content = "[error] partial failure",
                toolCalls = listOf(
                    ToolCall(id = "tc_1", name = "read", arguments = """{"path":"a.txt"}"""),
                    ToolCall(id = "tc_2", name = "write", arguments = """{"path":"b.txt"}""")
                )
            ),
            Message(role = "tool", content = "file content", toolCallId = "tc_1", name = "read"),
            // tc_2 has no result — should NOT be synthesized for aborted turn
            Message(role = "user", content = "next")
        )

        val result = HistorySanitizer.sanitize(messages)

        // Should contain: user, assistant[error], tool(tc_1), user
        // Should NOT contain synthetic tool result for tc_2
        val toolResults = result.filter { it.role == "tool" }
        assertEquals(1, toolResults.size)
        assertEquals("tc_1", toolResults[0].toolCallId)
        assertFalse(
            "Should not synthesize result for aborted turn",
            result.any { it.role == "tool" && it.toolCallId == "tc_2" }
        )
    }

    @Test
    fun `aborted assistant with no real results emits no tool results`() {
        val messages = listOf(
            Message(role = "user", content = "do something"),
            Message(
                role = "assistant",
                content = "[aborted] stopped early",
                toolCalls = listOf(
                    ToolCall(id = "tc_1", name = "read", arguments = """{"path":"a.txt"}""")
                )
            ),
            Message(role = "user", content = "next")
        )

        val result = HistorySanitizer.sanitize(messages)

        val toolResults = result.filter { it.role == "tool" }
        assertEquals(0, toolResults.size)
    }

    @Test
    fun `non-aborted assistant gets synthetic results for missing tool calls`() {
        val messages = listOf(
            Message(role = "user", content = "do something"),
            Message(
                role = "assistant",
                content = "I'll read and write",
                toolCalls = listOf(
                    ToolCall(id = "tc_1", name = "read", arguments = """{"path":"a.txt"}"""),
                    ToolCall(id = "tc_2", name = "write", arguments = """{"path":"b.txt"}""")
                )
            ),
            Message(role = "tool", content = "file content", toolCallId = "tc_1", name = "read"),
            // tc_2 missing — should be synthesized
            Message(role = "user", content = "next")
        )

        val result = HistorySanitizer.sanitize(messages)

        val toolResults = result.filter { it.role == "tool" }
        assertEquals(2, toolResults.size)
        assertEquals("tc_1", toolResults[0].toolCallId)
        assertEquals("tc_2", toolResults[1].toolCallId)
        assertTrue(toolResults[1].content.contains("missing tool result"))
    }

    @Test
    fun `malformed tool calls with blank id are filtered out`() {
        val messages = listOf(
            Message(role = "user", content = "do something"),
            Message(
                role = "assistant",
                content = "calling tools",
                toolCalls = listOf(
                    ToolCall(id = "tc_1", name = "read", arguments = """{"path":"a.txt"}"""),
                    ToolCall(id = "", name = "broken", arguments = "{}"),  // blank id
                    ToolCall(id = "  ", name = "also_broken", arguments = "{}")  // whitespace id
                )
            ),
            Message(role = "tool", content = "result", toolCallId = "tc_1", name = "read"),
            Message(role = "user", content = "next")
        )

        val result = HistorySanitizer.sanitize(messages)

        val toolResults = result.filter { it.role == "tool" }
        // Only tc_1 should have a result; blank-id calls should be ignored (no synthetic)
        assertEquals(1, toolResults.size)
        assertEquals("tc_1", toolResults[0].toolCallId)
    }

    @Test
    fun `displaced tool results are moved back to assistant turn`() {
        val messages = listOf(
            Message(role = "user", content = "do something"),
            Message(
                role = "assistant",
                content = "calling read",
                toolCalls = listOf(
                    ToolCall(id = "tc_1", name = "read", arguments = """{"path":"a.txt"}""")
                )
            ),
            Message(role = "user", content = "interruption"),
            Message(role = "tool", content = "file content", toolCallId = "tc_1", name = "read"),
            Message(role = "user", content = "continue")
        )

        val result = HistorySanitizer.sanitize(messages)

        // Tool result should be right after assistant, before user messages
        val assistantIdx = result.indexOfFirst { it.role == "assistant" }
        val toolIdx = result.indexOfFirst { it.role == "tool" }
        assertTrue("Tool result should follow assistant", toolIdx == assistantIdx + 1)
    }
}
