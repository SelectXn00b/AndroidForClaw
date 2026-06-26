package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-002 source-scan wiring tests for `GatewayOutboundRegistry`
 * (`app/.../hermes/gateway/GatewayOutboundRegistry.kt`) AND the matching
 * register / unregister wiring inside `HermesGatewayController.runHermesAgent`.
 *
 * Covers TC-GW-STREAMING-002-c: controller registers and unregisters
 * per-chatId dispatcher.
 *
 * The registry is the bridge between the agent loop (which sees only a
 * `send_message` tool) and the per-turn outbound dispatch lambda owned by
 * `HermesGatewayController`. It MUST be per-chatId (concurrent gateway runs
 * for different chats must not bleed dispatchers into each other) and MUST
 * be wrapped in a `try { register } finally { unregister }` window so a
 * mid-run exception doesn't leak the dispatcher for the next invocation.
 */
class GatewayOutboundRegistryWiringTest {

    private val registrySource: String by lazy {
        stripKotlinComments(File(registryPath()).readText())
    }
    private val controllerSource: String by lazy {
        stripKotlinComments(File(controllerPath()).readText())
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-c (part 1): registry exists with required surface
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-c registry exposes register unregister dispatch surface`() {
        // (1) ConcurrentHashMap backing store — per-chatId isolation.
        assertTrue(
            "TC-GW-STREAMING-002-c: `GatewayOutboundRegistry.kt` must use " +
                "`ConcurrentHashMap` as backing store. A plain `MutableMap` " +
                "is unsafe under concurrent gateway runs.",
            registrySource.contains("ConcurrentHashMap")
        )

        // (2) Three public methods: register, unregister, dispatch.
        assertTrue(
            "TC-GW-STREAMING-002-c: registry must expose a `register(` method " +
                "for HermesGatewayController to install a per-chatId dispatcher.",
            registrySource.contains("register(") || registrySource.contains("fun register")
        )
        assertTrue(
            "TC-GW-STREAMING-002-c: registry must expose an `unregister(` " +
                "method for `finally`-block cleanup.",
            registrySource.contains("unregister(") || registrySource.contains("fun unregister")
        )
        assertTrue(
            "TC-GW-STREAMING-002-c: registry must expose a `dispatch(` method " +
                "(called by the `send_message` tool executor) to forward text " +
                "to the registered dispatcher keyed by chatId.",
            registrySource.contains("dispatch(") || registrySource.contains("fun dispatch")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-c (part 2): controller drives register/unregister
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-c HermesGatewayController registers and unregisters dispatcher`() {
        // (1) Controller must reference the registry.
        assertTrue(
            "TC-GW-STREAMING-002-c: `HermesGatewayController.kt` must reference " +
                "`GatewayOutboundRegistry` to install/uninstall a per-chatId " +
                "outbound dispatch lambda.",
            controllerSource.contains("GatewayOutboundRegistry")
        )

        // (2) Controller must call `.register(` and `.unregister(`.
        assertTrue(
            "TC-GW-STREAMING-002-c: `runHermesAgent` must call `.register(` on " +
                "the registry to install the per-chatId dispatcher BEFORE the " +
                "agent runs.",
            controllerSource.contains(".register(")
        )
        assertTrue(
            "TC-GW-STREAMING-002-c: `runHermesAgent` must call `.unregister(` " +
                "to clean up the dispatcher entry after the agent loop ends. " +
                "Without this, the dispatcher leaks across invocations.",
            controllerSource.contains(".unregister(")
        )

        // (3) `finally` block — guarantees unregister runs even if the agent
        //     throws mid-loop.
        assertTrue(
            "TC-GW-STREAMING-002-c: `runHermesAgent` must contain a `finally` " +
                "block (the unregister cleanup MUST run even if the agent " +
                "throws mid-run).",
            controllerSource.contains("finally")
        )

        // (4) Ordering: register → sendUserMessage → unregister.
        //     This is the three-phase contract: install before triggering the
        //     agent, leave the dispatcher available throughout the agent loop,
        //     remove it after the loop completes.
        val registerIdx = controllerSource.indexOf(".register(")
        val sendUserMsgIdx = controllerSource.indexOf("core.sendUserMessage")
        val unregisterIdx = controllerSource.indexOf(".unregister(")
        assertTrue(
            "TC-GW-STREAMING-002-c: `.register(` (idx=$registerIdx) must appear " +
                "in source BEFORE `core.sendUserMessage` (idx=$sendUserMsgIdx) — " +
                "the dispatcher must be installed before the agent runs.",
            registerIdx in 0 until sendUserMsgIdx
        )
        assertTrue(
            "TC-GW-STREAMING-002-c: `.unregister(` (idx=$unregisterIdx) must " +
                "appear in source AFTER `core.sendUserMessage` (idx=$sendUserMsgIdx) — " +
                "the dispatcher cleanup must follow the agent loop.",
            unregisterIdx > sendUserMsgIdx
        )
    }

    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun registryPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/GatewayOutboundRegistry.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/GatewayOutboundRegistry.kt")
        return alt.path
    }

    private fun controllerPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayController.kt")
        return alt.path
    }
}
