package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-002 source-scan wiring test for the new
 * `GatewaySendMessageTool` (`app/.../hermes/gateway/GatewaySendMessageTool.kt`).
 *
 * Covers TC-GW-STREAMING-002-a: tool exposes `send_message` schema with
 * text-only params.
 *
 * **Source-scan rationale**: the tool plugs into `OperitToolDispatcher`'s
 * `extraExecutors` map and is consumed by a live `HermesAgentLoop`; the
 * end-to-end behavior is covered by TC-GW-STREAMING-002-f (manual E2E).
 * Literal-level assertions on the file lock in the proven shape — name,
 * params list, bilingual hint keywords — and guard against regressions
 * (e.g. someone adds `action` / `target` params and accidentally collides
 * with Python upstream's cross-channel semantics).
 */
class GatewaySendMessageToolShapeWiringTest {

    private val source: String by lazy { stripKotlinComments(File(toolPath()).readText()) }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-a: tool exposes send_message schema with text-only params
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-a tool exposes send_message schema with text-only params`() {
        // (1) The constant carrying the tool name lives in this file.
        assertTrue(
            "TC-GW-STREAMING-002-a: `GatewaySendMessageTool.kt` must declare " +
                "`GATEWAY_SEND_MESSAGE_TOOL_NAME` carrying the canonical tool name.",
            source.contains("GATEWAY_SEND_MESSAGE_TOOL_NAME")
        )

        // (2) Tool name must be the literal `send_message` (parallel to Python
        //     upstream `tools/send_message_tool.py` so the prompt-side LLM
        //     reasoning carries across; Android v1 simplifies semantics but the
        //     surface name matches).
        assertTrue(
            "TC-GW-STREAMING-002-a: `GATEWAY_SEND_MESSAGE_TOOL_NAME` must resolve to " +
                "`\"send_message\"` (parallel to Python upstream tool name).",
            source.contains("\"send_message\"")
        )

        // (3) Schema must declare a `text` parameter.
        assertTrue(
            "TC-GW-STREAMING-002-a: schema must declare a `\"text\"` parameter " +
                "(the only required field on Android v1).",
            source.contains("\"text\"")
        )

        // (4) Schema MUST NOT declare `action` or `target` parameters — that's
        //     Python upstream's cross-channel semantics, which Android v1 does
        //     not implement (target is implicitly the current gateway chat).
        assertFalse(
            "TC-GW-STREAMING-002-a: schema MUST NOT declare an `\"action\"` param " +
                "(Python upstream cross-channel semantics not supported on Android v1).",
            source.contains("\"action\"")
        )
        assertFalse(
            "TC-GW-STREAMING-002-a: schema MUST NOT declare a `\"target\"` param " +
                "(target is implicitly the current gateway chat on Android v1).",
            source.contains("\"target\"")
        )

        // (5) Bilingual hint: description must mention `bubble` (English) AND
        //     `气泡` (Chinese) so the model reliably understands the per-call
        //     UX in both languages.
        assertTrue(
            "TC-GW-STREAMING-002-a: tool description must mention `bubble` " +
                "(English) to instruct the model that each call surfaces as a " +
                "separate IM bubble.",
            source.contains("bubble")
        )
        assertTrue(
            "TC-GW-STREAMING-002-a: tool description must mention `气泡` " +
                "(Chinese) — bilingual support for CN-leaning models.",
            source.contains("气泡")
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

    private fun toolPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/GatewaySendMessageTool.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/GatewaySendMessageTool.kt")
        return alt.path
    }
}
