package com.xiaomo.hermes.hermes.gateway

import android.content.Context
import com.xiaomo.hermes.hermes.gateway.platforms.BasePlatformAdapter
import com.xiaomo.hermes.hermes.gateway.platforms.MessageEvent
import com.xiaomo.hermes.hermes.gateway.platforms.SendResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * R-GATEWAY-037: drain-time reject / queue behavior + `_draining` guard +
 * `_restart_requested` AND `mode==queue` consumer.
 *
 * Aligns with Python upstream:
 *  - Predicate: `gateway/run.py:1230-1231`
 *  - Drain branch: `gateway/run.py:1515-1533`
 *  - Set sites: `gateway/run.py:2549, 1892, 2536`
 *
 * **What this R covers**: drain-branch ack (queue接力 vs reject), `_draining`
 * gate placement (before R-036 command routing), and the AND-guard on
 * `queueDuringDrainEnabled()`. Caller-side `_draining=true` happens in
 * `stop()` — covered by `TC-GATEWAY-037-stop` via reflection.
 */
class GatewayDrainBehaviorTest {

    /** Records outbound send() calls so we can assert the drain ack text. */
    private data class SendCall(val chatId: String, val content: String, val replyTo: String?)

    private class FakeAdapter(
        platform: Platform,
        config: PlatformConfig,
    ) : BasePlatformAdapter(config, platform) {
        val sent: CopyOnWriteArrayList<SendCall> = CopyOnWriteArrayList()
        override suspend fun connect(): Boolean { markConnected(); return true }
        override suspend fun disconnect() { markDisconnected() }
        override suspend fun send(
            chatId: String,
            content: String,
            replyTo: String?,
            metadata: JSONObject?,
        ): SendResult {
            sent += SendCall(chatId, content, replyTo)
            return SendResult(success = true, messageId = "m-${sent.size}")
        }
    }

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

    /** Build a runner pre-wired with a fake adapter and registered _adapters entry. */
    private fun newRunnerWithFakeAdapter(platform: Platform = Platform.TELEGRAM): Pair<GatewayRunner, FakeAdapter> {
        val runner = newRunner()
        val cfg = PlatformConfig(platform = platform, enabled = true)
        val adapter = FakeAdapter(platform, cfg)
        // Register into private `_adapters` map so `_sendCommandAck` finds it
        // (it does `_adapters[event.source.platform]`, keyed by `Platform.value`).
        @Suppress("UNCHECKED_CAST")
        val adaptersField = runner.javaClass.getDeclaredField("_adapters").apply { isAccessible = true }
        val adapters = adaptersField.get(runner) as MutableMap<String, BasePlatformAdapter>
        adapters[platform.value] = adapter
        return runner to adapter
    }

    private fun event(
        text: String = "anything",
        platform: String = "telegram",
        chatId: String = "c1",
        userId: String = "u1",
        msgId: String = "mid-1",
    ): MessageEvent {
        val src = SessionSource(platform = platform, chatId = chatId, userId = userId)
        return MessageEvent(text = text, source = src, message_id = msgId)
    }

    /** Reflectively set `_draining`. */
    private fun setDraining(runner: GatewayRunner, value: Boolean) {
        val f = runner.javaClass.getDeclaredField("_draining")
        f.isAccessible = true
        f.setBoolean(runner, value)
    }

    /** Reflectively set `_restartRequested`. */
    private fun setRestartRequested(runner: GatewayRunner, value: Boolean) {
        val f = runner.javaClass.getDeclaredField("_restartRequested")
        f.isAccessible = true
        f.setBoolean(runner, value)
    }

    /** Reflectively set `_busyInputMode`. */
    private fun setBusyInputMode(runner: GatewayRunner, mode: String) {
        val f = runner.javaClass.getDeclaredField("_busyInputMode")
        f.isAccessible = true
        f.set(runner, mode)
    }

    /** Reflectively read `_pendingEvents`. */
    @Suppress("UNCHECKED_CAST")
    private fun pendingEvents(runner: GatewayRunner): Map<String, MessageEvent> {
        val f = runner.javaClass.getDeclaredField("_pendingEvents")
        f.isAccessible = true
        return f.get(runner) as Map<String, MessageEvent>
    }

    /** Run the drain handler synchronously. */
    private fun callDrain(runner: GatewayRunner, ev: MessageEvent) {
        runBlocking { runner._handleDrainBusyMessage(ev) }
    }

    // -------- TC-GATEWAY-037-a --------
    /**
     * TC-GATEWAY-037-a: restart drain + mode=queue → message gets queued
     * into `_pendingEvents` AND a "queued for the next turn" ack flows
     * through the platform adapter. Mirrors Python `:1521-1523`.
     */
    @Test
    fun `TC-GATEWAY-037-a restart with queue mode queues and acks`() {
        val (runner, adapter) = newRunnerWithFakeAdapter()
        setDraining(runner, true)
        setRestartRequested(runner, true)
        setBusyInputMode(runner, "queue")

        val ev = event(text = "follow up", platform = "telegram", chatId = "c1", userId = "u1")
        callDrain(runner, ev)

        // _pendingEvents got the message keyed by sessionKey.
        val pend = pendingEvents(runner)
        assertEquals(1, pend.size)
        val keyed = pend["telegram:c1:u1"]
        assertNotNull("must be keyed by sessionKey", keyed)
        assertEquals("follow up", keyed!!.text)

        // Ack delivered with restart gerund + queued semantics.
        assertEquals(1, adapter.sent.size)
        val ack = adapter.sent[0].content
        assertTrue("ack must mention 'restarting': $ack", ack.contains("restarting"))
        assertTrue("ack must mention 'queued for the next turn': $ack", ack.contains("queued for the next turn"))
    }

    // -------- TC-GATEWAY-037-b --------
    /**
     * TC-GATEWAY-037-b: plain shutdown (no restart) + mode=queue must NOT
     * queue, must reject. Mirrors Python `:1230-1231` AND-guard: even if
     * mode=queue, lack of `_restart_requested` means "queue接力" is off
     * because there's no next gateway run to deliver the queued message.
     */
    @Test
    fun `TC-GATEWAY-037-b plain stop with queue mode rejects`() {
        val (runner, adapter) = newRunnerWithFakeAdapter()
        setDraining(runner, true)
        setRestartRequested(runner, false)
        setBusyInputMode(runner, "queue")

        callDrain(runner, event())

        assertTrue("must NOT enqueue for plain shutdown", pendingEvents(runner).isEmpty())
        assertEquals(1, adapter.sent.size)
        val ack = adapter.sent[0].content
        assertTrue("ack must mention 'shutting down': $ack", ack.contains("shutting down"))
        assertTrue("ack must mention 'not accepting': $ack", ack.contains("not accepting"))
    }

    // -------- TC-GATEWAY-037-c --------
    /**
     * TC-GATEWAY-037-c: restart + mode=interrupt → rejection (no queue).
     * Mirrors Python `:1230-1231`: predicate is AND of restart + queue mode.
     */
    @Test
    fun `TC-GATEWAY-037-c restart with interrupt mode rejects`() {
        val (runner, adapter) = newRunnerWithFakeAdapter()
        setDraining(runner, true)
        setRestartRequested(runner, true)
        setBusyInputMode(runner, "interrupt")

        callDrain(runner, event())

        assertTrue("must NOT enqueue for interrupt mode", pendingEvents(runner).isEmpty())
        assertEquals(1, adapter.sent.size)
        val ack = adapter.sent[0].content
        // gerund follows _restartRequested (true), so still "restarting".
        assertTrue("ack must mention 'restarting': $ack", ack.contains("restarting"))
        assertTrue("ack must mention 'not accepting': $ack", ack.contains("not accepting"))
    }

    // -------- TC-GATEWAY-037-d --------
    /**
     * TC-GATEWAY-037-d: structural assertion — when `_draining=false`, the
     * drain branch falls through and the existing R-036 command routing
     * runs. We can't easily drive `_handleMessage` end-to-end here without
     * setting up `_processingSessions`, so we lock the structural ordering
     * via source scan: the `_draining` check appears INSIDE the
     * `_processingSessions.add(...)` busy branch and BEFORE the
     * `resolveCommand(event.text)` call.
     */
    @Test
    fun `TC-GATEWAY-037-d non draining lets command routing work`() {
        val srcRoots = listOf(
            java.io.File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            java.io.File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
        )
        val src = srcRoots.firstOrNull { it.exists() }?.readText()
            ?: error("Cannot locate Run.kt; cwd=${java.io.File(".").absolutePath}")

        val busyGuardIdx = src.indexOf("if (!_processingSessions.add(event.sessionKey))")
        val drainCheckIdx = src.indexOf("if (_draining)", startIndex = busyGuardIdx)
        val cmdRouteIdx = src.indexOf("resolveCommand(event.text)", startIndex = busyGuardIdx)

        assertTrue("busy guard must exist", busyGuardIdx >= 0)
        assertTrue("drain check must exist after busy guard", drainCheckIdx > busyGuardIdx)
        assertTrue("command routing must exist after busy guard", cmdRouteIdx > busyGuardIdx)
        assertTrue(
            "drain check must precede command routing inside busy branch",
            drainCheckIdx < cmdRouteIdx,
        )
    }

    // -------- TC-GATEWAY-037-e --------
    /**
     * TC-GATEWAY-037-e: `queueDuringDrainEnabled()` truth table over
     * (`_restartRequested`, `_busyInputMode`). Only (true, "queue") → true.
     * Mirrors Python `:1230-1231`.
     */
    @Test
    fun `TC-GATEWAY-037-e queueDuringDrainEnabled requires both flags`() {
        val cases = listOf(
            Triple(false, "interrupt", false),
            Triple(false, "queue", false),
            Triple(true, "interrupt", false),
            Triple(true, "queue", true),
        )
        for ((restart, mode, expected) in cases) {
            val r = newRunner()
            setRestartRequested(r, restart)
            setBusyInputMode(r, mode)
            assertEquals(
                "queueDuringDrainEnabled(restart=$restart, mode=$mode) must be $expected",
                expected, r.queueDuringDrainEnabled(),
            )
        }
    }

    // -------- TC-GATEWAY-037-stop --------
    /**
     * TC-GATEWAY-037-stop: `stop()` entry must set `_draining=true` so any
     * inbound message arriving during adapter teardown hits the drain
     * branch. Validated by source scan — driving the real `stop()` would
     * cancel the runner's internal scope and complicate test isolation.
     */
    @Test
    fun `TC-GATEWAY-037-stop sets draining flag at entry`() {
        val srcRoots = listOf(
            java.io.File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
            java.io.File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt"),
        )
        val src = srcRoots.firstOrNull { it.exists() }?.readText()
            ?: error("Cannot locate Run.kt; cwd=${java.io.File(".").absolutePath}")

        // Find the `suspend fun stop(` block and verify _draining=true sits
        // BEFORE the disconnect loop.
        val stopIdx = src.indexOf("suspend fun stop(")
        assertTrue("stop() must exist", stopIdx >= 0)
        val drainSet = src.indexOf("_draining = true", startIndex = stopIdx)
        val disconnectLoop = src.indexOf("for ((name, adapter) in _adapters)", startIndex = stopIdx)
        assertTrue("_draining=true must be set in stop()", drainSet > stopIdx)
        assertTrue("disconnect loop must exist in stop()", disconnectLoop > stopIdx)
        assertTrue(
            "_draining must be set BEFORE adapter disconnect (so racing inbound msgs hit drain branch)",
            drainSet < disconnectLoop,
        )

        // Also assert: `if (restart) _restartRequested = true` is in stop().
        val restartTag = src.indexOf("if (restart) _restartRequested = true", startIndex = stopIdx)
        assertTrue(
            "stop() must propagate `restart` arg into _restartRequested for queue接力",
            restartTag > stopIdx,
        )
    }

    // -------- Sanity: assertNull anchor for IDE auto-import --------
    @Test
    fun `sanity assertNull is imported`() {
        assertNull(null)
    }
}
