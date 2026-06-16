package com.xiaomo.hermes.hermes.gateway

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.platforms.MessageEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * R-GATEWAY-036: GatewayRunner active-session command routing + Commands.kt parser.
 *
 * Aligns with Python upstream:
 *  - ACTIVE_SESSION_BYPASS_COMMANDS:    `hermes_cli/commands.py:267-284`
 *  - resolve_command:                   `hermes_cli/commands.py:resolve_command`
 *  - Steer dispatch:                    `gateway/run.py:3290-3334`
 *  - Stop dispatch:                     `gateway/run.py:3225-3245`
 *  - Queue dispatch:                    `gateway/run.py:3261-3282`
 *
 * **What this R covers**: parser + 3 concrete handlers (`/steer`, `/queue`, `/stop`)
 * + polite reject for other recognized commands. Caller-side wiring of
 * `steerActiveAgent` / `cancelActiveAgent` weakrefs lands in R-UI-062.
 */
class GatewayCommandRoutingTest {

    private fun newRunner(): GatewayRunner {
        val ctx: Context = mock()
        val cfg = GatewayConfig(
            hermesHome = "",
            platforms = emptyMap(),
            maxConcurrentSessions = 4,
            extra = emptyMap(),
        )
        return GatewayRunner(ctx, cfg)
    }

    private fun event(text: String, platform: String = "test", chatId: String = "chat", userId: String = "user"): MessageEvent {
        // sessionKey format is "platform:chatId:userId" (Session.kt:142).
        val source = SessionSource(platform = platform, chatId = chatId, userId = userId)
        return MessageEvent(text = text, source = source)
    }

    /** Invoke `_handleBypassCommand(event, cmd, argText)` synchronously via runBlocking. */
    private fun callHandle(runner: GatewayRunner, event: MessageEvent, cmd: String, arg: String) {
        runBlocking { runner._handleBypassCommand(event, cmd, arg) }
    }

    /** Reflectively read private `_pendingEvents` map size & contents. */
    @Suppress("UNCHECKED_CAST")
    private fun pendingEvents(runner: GatewayRunner): Map<String, MessageEvent> {
        val field = runner.javaClass.getDeclaredField("_pendingEvents")
        field.isAccessible = true
        return field.get(runner) as Map<String, MessageEvent>
    }

    // -------- TC-GATEWAY-036-a: resolveCommand parses slash plus args --------
    /**
     * TC-GATEWAY-036-a: `resolveCommand("/steer hello world")` → ("steer","hello world").
     * Mirrors Python `hermes_cli/commands.py:resolve_command`.
     */
    @Test
    fun `TC-GATEWAY-036-a resolveCommand parses slash plus args`() {
        assertEquals("steer" to "hello world", resolveCommand("/steer hello world"))
        assertEquals("stop" to "", resolveCommand("/stop"))
        assertEquals("queue" to "later please", resolveCommand("/queue later please"))
    }

    // -------- TC-GATEWAY-036-b: resolveCommand rejects non-bypass tokens --------
    /**
     * TC-GATEWAY-036-b: text not starting with `/`, empty text, or unrecognized
     * command names all return null so the caller falls through to the regular
     * busy path.
     */
    @Test
    fun `TC-GATEWAY-036-b resolveCommand rejects non-bypass tokens`() {
        assertNull("plain text must not be parsed as command", resolveCommand("hello /steer x"))
        assertNull("empty text must return null", resolveCommand(""))
        assertNull("whitespace-only text must return null", resolveCommand("   "))
        assertNull("unknown commands must not match the bypass set", resolveCommand("/unknownCmd hello"))
        assertNull("bare slash must not match", resolveCommand("/"))
        assertNull("slash with whitespace only", resolveCommand("/   "))
    }

    // -------- TC-GATEWAY-036-c: resolveCommand normalizes case and whitespace --------
    /**
     * TC-GATEWAY-036-c: leading whitespace + uppercase command + multi-space gap
     * are all normalized: cmd lowercased + trimmed, arg trimmed.
     */
    @Test
    fun `TC-GATEWAY-036-c resolveCommand normalizes case and whitespace`() {
        assertEquals("steer" to "Hello", resolveCommand("  /STEER   Hello  "))
        assertEquals("stop" to "", resolveCommand("\t/Stop\n"))
        assertEquals("queue" to "later", resolveCommand("/Queue   later"))
    }

    // -------- TC-GATEWAY-036-d: steer dispatches to callback --------
    /**
     * TC-GATEWAY-036-d: `/steer 加个限制` invokes the registered
     * `steerActiveAgent` callback with the trimmed arg text and the event's
     * sessionKey. Must NOT touch `_pendingEvents` or set interrupt flag.
     */
    @Test
    fun `TC-GATEWAY-036-d steer dispatches to callback`() {
        val runner = newRunner()
        val captured = mutableListOf<Pair<String, String>>()
        runner.steerActiveAgent = { sk, text ->
            captured.add(sk to text)
            true
        }
        val ev = event("/steer 加个限制", platform = "telegram", chatId = "c1", userId = "u1")
        callHandle(runner, ev, "steer", "加个限制")

        assertEquals("steer callback must be invoked exactly once", 1, captured.size)
        assertEquals("telegram:c1:u1" to "加个限制", captured[0])
        assertTrue(
            "_pendingEvents must NOT be populated by /steer",
            pendingEvents(runner).isEmpty(),
        )
    }

    // -------- TC-GATEWAY-036-e: queue merges into pending events --------
    /**
     * TC-GATEWAY-036-e: `/queue ...` populates `_pendingEvents` keyed by
     * sessionKey with the arg text replacing the event text. Must NOT invoke
     * `steerActiveAgent` or `cancelActiveAgent`.
     */
    @Test
    fun `TC-GATEWAY-036-e queue merges into pending events`() {
        val runner = newRunner()
        var steerCalled = false
        var stopCalled = false
        runner.steerActiveAgent = { _, _ -> steerCalled = true; true }
        runner.cancelActiveAgent = { _ -> stopCalled = true; true }

        val ev = event("/queue 待会儿处理", platform = "telegram", chatId = "c2", userId = "u2")
        callHandle(runner, ev, "queue", "待会儿处理")

        val pending = pendingEvents(runner)
        assertEquals("Pending must contain exactly one entry", 1, pending.size)
        val pendingEv = pending["telegram:c2:u2"]
        assertNotNull("Pending must be keyed by sessionKey", pendingEv)
        assertEquals(
            "Pending event text must be the queued arg (not the original /queue ...)",
            "待会儿处理", pendingEv!!.text,
        )
        assertFalse("steer callback must NOT be invoked by /queue", steerCalled)
        assertFalse("stop callback must NOT be invoked by /queue", stopCalled)
    }

    // -------- TC-GATEWAY-036-f: stop dispatches to callback --------
    /**
     * TC-GATEWAY-036-f: `/stop` invokes `cancelActiveAgent(sessionKey)` once.
     * Must NOT populate `_pendingEvents` or call steer.
     */
    @Test
    fun `TC-GATEWAY-036-f stop dispatches to callback`() {
        val runner = newRunner()
        val captured = mutableListOf<String>()
        runner.cancelActiveAgent = { sk ->
            captured.add(sk)
            true
        }
        val ev = event("/stop", platform = "wechat", chatId = "c3", userId = "u3")
        callHandle(runner, ev, "stop", "")

        assertEquals("cancel callback must be invoked exactly once", 1, captured.size)
        assertEquals("wechat:c3:u3", captured[0])
        assertTrue(
            "_pendingEvents must NOT be populated by /stop",
            pendingEvents(runner).isEmpty(),
        )
    }

    // -------- TC-GATEWAY-036-g: unhandled bypass commands are rejected --------
    /**
     * TC-GATEWAY-036-g: A recognized but unwired command (e.g. `/agents`) must
     * NOT call any callback, NOT populate `_pendingEvents`, NOT throw — it
     * just sends a polite-reject ack and returns. Verifies the dispatch is a
     * total function over the bypass set.
     */
    @Test
    fun `TC-GATEWAY-036-g unhandled bypass commands are rejected`() {
        val runner = newRunner()
        var anyCallback = false
        runner.steerActiveAgent = { _, _ -> anyCallback = true; true }
        runner.cancelActiveAgent = { _ -> anyCallback = true; true }

        for (cmd in listOf("agents", "approve", "deny", "help", "new", "profile", "restart", "status", "update", "background", "commands")) {
            val ev = event("/$cmd whatever")
            // Should not throw.
            callHandle(runner, ev, cmd, "whatever")
        }

        assertFalse(
            "Unhandled bypass commands must NOT invoke any side-effect callback",
            anyCallback,
        )
        assertTrue(
            "Unhandled bypass commands must NOT populate _pendingEvents",
            pendingEvents(runner).isEmpty(),
        )
    }
}
