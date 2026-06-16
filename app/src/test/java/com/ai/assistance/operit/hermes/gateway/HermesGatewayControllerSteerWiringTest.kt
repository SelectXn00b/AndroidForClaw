package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-062: HermesGatewayController.start() wires the two GatewayRunner
 * callbacks (`steerActiveAgent` / `cancelActiveAgent`) to the GATEWAY-slot
 * ChatServiceCore so that gateway `/steer` and `/stop` commands hit the
 * currently-running HermesAgentLoop.
 *
 * Source-scan because driving the real `start()` would require Application
 * Context + a real platform adapter + GatewayRunner.start() + Room DB.
 */
class HermesGatewayControllerSteerWiringTest {

    private val source: String by lazy { File(controllerPath()).readText() }

    /** Brace-walked body of `suspend fun start():`. */
    private fun extractStartBody(): String {
        val anchor = Regex("""suspend\s+fun\s+start\s*\(""").find(source)?.range?.first
            ?: error("Cannot find start() in HermesGatewayController.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find start() opening brace" }
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

    // -------- TC-UI-062-e --------
    /**
     * TC-UI-062-e: HermesGatewayController.start() body must:
     *   (1) Assign `instance.steerActiveAgent = { sessionKey, text -> ... }`
     *   (2) Assign `instance.cancelActiveAgent = { sessionKey -> ... }`
     *   (3) Both assignments live BEFORE `runner = instance` (otherwise a
     *       race where the runner is published before its callbacks are
     *       wired could let an early /steer fire into a still-null callback)
     *   (4) Bodies route through GATEWAY-slot ChatServiceCore via
     *       `ChatRuntimeHolder` + `ChatRuntimeSlot.GATEWAY`
     *   (5) Use `steerActiveLoop(...)` / `cancelMessage(...)` on the core
     */
    @Test
    fun `TC-UI-062-e start wires both callbacks`() {
        val body = extractStartBody()

        // (1) + (2) — both callback assignments present.
        val steerIdx = body.indexOf("instance.steerActiveAgent")
        val cancelIdx = body.indexOf("instance.cancelActiveAgent")
        assertTrue(
            "TC-UI-062-e: start() must assign instance.steerActiveAgent",
            steerIdx >= 0,
        )
        assertTrue(
            "TC-UI-062-e: start() must assign instance.cancelActiveAgent",
            cancelIdx >= 0,
        )
        assertTrue(
            "TC-UI-062-e: instance.steerActiveAgent must be assigned (= follows)",
            Regex("""instance\.steerActiveAgent\s*=""").containsMatchIn(body),
        )
        assertTrue(
            "TC-UI-062-e: instance.cancelActiveAgent must be assigned (= follows)",
            Regex("""instance\.cancelActiveAgent\s*=""").containsMatchIn(body),
        )

        // (3) — both assignments must precede `runner = instance`.
        val runnerAssignIdx = Regex("""\brunner\s*=\s*instance\b""").find(body)?.range?.first ?: -1
        assertTrue(
            "TC-UI-062-e: start() must assign runner = instance somewhere",
            runnerAssignIdx > 0,
        )
        assertTrue(
            "TC-UI-062-e: instance.steerActiveAgent assignment must precede runner = instance",
            steerIdx in 0 until runnerAssignIdx,
        )
        assertTrue(
            "TC-UI-062-e: instance.cancelActiveAgent assignment must precede runner = instance",
            cancelIdx in 0 until runnerAssignIdx,
        )

        // (4) + (5) — bodies route through GATEWAY-slot ChatServiceCore.
        // Walk from steerIdx to cancelIdx + a bit, capturing both callback
        // bodies, and assert the routing primitives appear at least once.
        val callbackSlice = body.substring(steerIdx, body.length.coerceAtMost(cancelIdx + 800))
        assertTrue(
            "TC-UI-062-e: callbacks must use ChatRuntimeHolder",
            callbackSlice.contains("ChatRuntimeHolder"),
        )
        assertTrue(
            "TC-UI-062-e: callbacks must target ChatRuntimeSlot.GATEWAY",
            callbackSlice.contains("ChatRuntimeSlot.GATEWAY"),
        )
        assertTrue(
            "TC-UI-062-e: steer callback must call core.steerActiveLoop(...)",
            Regex("""\.steerActiveLoop\s*\(""").containsMatchIn(callbackSlice),
        )
        assertTrue(
            "TC-UI-062-e: cancel callback must call core.cancelMessage(...)",
            Regex("""\.cancelMessage\s*\(""").containsMatchIn(callbackSlice),
        )
    }

    // -------- TC-UI-062-e-2 (session-key guard) --------
    /**
     * Defensive: the steer callback must only fire when the GATEWAY-slot's
     * currentChatId actually corresponds to the inbound sessionKey.
     * Otherwise a `/steer` for session A could accidentally hit session B's
     * loop (the GATEWAY core can only drive one chat at a time, but a
     * race could swap currentChatId mid-callback).
     *
     * We assert the prefix-match guard `"gw:${sessionKey}:"` appears in
     * the callback body — exact regex is flexible to allow `gw:$sessionKey:`
     * or `"gw:" + sessionKey + ":"` styles.
     */
    @Test
    fun `TC-UI-062-e-2 callback guards against session mismatch`() {
        val body = extractStartBody()
        assertTrue(
            "TC-UI-062-e-2: callback must check historyChatId prefix `gw:<sessionKey>:`",
            Regex("""gw:\${'$'}sessionKey:|gw:" \+ sessionKey \+ ":""").containsMatchIn(body),
        )
    }

    private fun controllerPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"),
            File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate HermesGatewayController.kt — cwd=${File(".").absolutePath}")
    }
}
