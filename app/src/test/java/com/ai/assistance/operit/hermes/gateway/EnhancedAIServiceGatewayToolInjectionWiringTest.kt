package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-002 source-scan wiring test for
 * `EnhancedAIService.runAgentLoopViaHermes` (`app/.../api/chat/EnhancedAIService.kt`).
 *
 * Covers TC-GW-STREAMING-002-b: gateway-only injection gate guards
 * `send_message` tool.
 *
 * The gate is `isSubTask && chatId?.startsWith("gw:") == true` — only when
 * BOTH are true should the `send_message` tool be injected into the
 * `OperitToolDispatcher.extraExecutors` and the OpenAI tool schemas.  APP-UI
 * path (`chatId` is a normal Room chat id, no `gw:` prefix) MUST NOT see
 * this tool — otherwise users in the in-app chat would observe the agent
 * "sending itself a WeChat message".
 *
 * **Source-scan rationale**: `EnhancedAIService.runAgentLoopViaHermes` is a
 * 200+-line orchestration function with Room DB, ConversationService,
 * SharedFlow plumbing, and HermesAgentLoop coupling — driving an actual
 * behavior test would need a full Android instrumentation harness.
 * Literal-level wiring assertions are sufficient to lock in the gate.
 */
class EnhancedAIServiceGatewayToolInjectionWiringTest {

    private val source: String by lazy { stripKotlinComments(File(servicePath()).readText()) }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-b: gateway-only injection gate guards send_message
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-b gateway-only injection gate guards send_message tool`() {
        // (1) The gate must contain the `gw:` prefix literal — that's the
        //     de-facto signal for "this run came from HermesGatewayController".
        assertTrue(
            "TC-GW-STREAMING-002-b: `EnhancedAIService.kt` must contain a " +
                "`startsWith(\"gw:\")` literal as part of the gateway-only " +
                "injection gate. Gateway chatIds are tagged `gw:<sessionKey>:<chatId>` " +
                "by HermesGatewayController; this prefix is the de-facto signal " +
                "that the tool should be injected.",
            source.contains("startsWith(\"gw:\")")
        )

        // (2) The gate must include the `isSubTask` flag — APP-UI may also
        //     trigger sub-tasks but they shouldn't get the gateway tool, so
        //     `isSubTask` alone isn't enough.  Gate is the AND of both.
        assertTrue(
            "TC-GW-STREAMING-002-b: `EnhancedAIService.kt` must contain " +
                "`isSubTask` literal — the gateway-only gate is the AND of " +
                "`isSubTask` and `chatId.startsWith(\"gw:\")`.",
            source.contains("isSubTask")
        )

        // (3) Same logical expression: a window of ±300 chars AROUND the
        //     `startsWith("gw:")` literal must contain `isSubTask`. We can't
        //     use `indexOf("isSubTask")` to compare distances because the
        //     function signature on line ~200 has a parameter named
        //     `isSubTask` which is unrelated to the gate. Instead, look at
        //     the local neighborhood of the prefix check itself.
        val gwIdx = source.indexOf("startsWith(\"gw:\")")
        val windowStart = (gwIdx - 300).coerceAtLeast(0)
        val windowEnd = (gwIdx + 300).coerceAtMost(source.length)
        val gateNeighborhood = source.substring(windowStart, windowEnd)
        assertTrue(
            "TC-GW-STREAMING-002-b: `isSubTask` must appear within ±300 chars " +
                "of `startsWith(\"gw:\")` (idx=$gwIdx). The gate must be the AND " +
                "of both, not two unrelated checks scattered across the file. " +
                "Looked at neighborhood [$windowStart..$windowEnd].",
            gateNeighborhood.contains("isSubTask")
        )

        // (4) The `send_message` tool name (or constant) must appear in
        //     `EnhancedAIService.kt` source — that's evidence the tool is
        //     actually injected somewhere in the gate's true branch.
        assertTrue(
            "TC-GW-STREAMING-002-b: `EnhancedAIService.kt` must reference " +
                "`GATEWAY_SEND_MESSAGE_TOOL_NAME` or the literal `\"send_message\"` — " +
                "the gateway-only injection gate's true branch must inject the " +
                "tool, and that injection requires referring to the tool name.",
            source.contains("GATEWAY_SEND_MESSAGE_TOOL_NAME") ||
                source.contains("\"send_message\"")
        )

        // (5) Injection point: the OperitToolDispatcher must be aware of
        //     `extraExecutors` (the channel through which the gateway tool is
        //     plumbed into dispatch). If this literal is missing, the tool
        //     can't be invoked from inside the agent loop even if it appears
        //     in the schema list.
        assertTrue(
            "TC-GW-STREAMING-002-b: `EnhancedAIService.kt` must contain " +
                "`extraExecutors` literal — that's the channel through which " +
                "the gateway-only `send_message` executor is plumbed into " +
                "`OperitToolDispatcher.dispatch`.",
            source.contains("extraExecutors")
        )
    }

    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments while
     * preserving newlines.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun servicePath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt")
        return alt.path
    }
}
