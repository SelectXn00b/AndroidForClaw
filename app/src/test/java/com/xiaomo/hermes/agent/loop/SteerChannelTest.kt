package com.xiaomo.hermes.agent.loop

import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

/**
 * Steer Channel Tests
 *
 * AgentLoop exposes a `steerChannel = Channel<String>(capacity = 16)` that
 * MessageQueueManager uses to inject mid-run user messages via trySend().
 * The agent loop drains it with tryReceive() each iteration.
 *
 * Since AgentLoop requires heavy Android dependencies (LLM providers, tool
 * registries), we test the channel pattern directly with the same capacity
 * and semantics used in production.
 */
class SteerChannelTest {

    /** Same capacity as AgentLoop.steerChannel */
    private val capacity = 16

    @Test
    fun steerChannel_sendAndReceive() {
        val channel = Channel<String>(capacity = capacity)
        assertTrue(channel.trySend("hello").isSuccess)
        assertTrue(channel.trySend("world").isSuccess)
        assertEquals("hello", channel.tryReceive().getOrNull())
        assertEquals("world", channel.tryReceive().getOrNull())
        assertNull(channel.tryReceive().getOrNull())
    }

    @Test
    fun steerChannel_fifoOrder() {
        val channel = Channel<String>(capacity = capacity)
        val messages = (1..5).map { "msg_$it" }
        messages.forEach { assertTrue(channel.trySend(it).isSuccess) }

        val received = mutableListOf<String>()
        while (true) {
            val msg = channel.tryReceive().getOrNull() ?: break
            received.add(msg)
        }
        assertEquals(messages, received)
    }

    @Test
    fun steerChannel_capacityLimit() {
        val channel = Channel<String>(capacity = capacity)
        // Fill to capacity
        repeat(capacity) { i ->
            assertTrue("Send #$i should succeed", channel.trySend("m$i").isSuccess)
        }
        // 17th send should fail (buffer full)
        assertFalse(channel.trySend("overflow").isSuccess)
    }

    @Test
    fun steerChannel_emptyReceiveReturnsNull() {
        val channel = Channel<String>(capacity = capacity)
        assertNull(channel.tryReceive().getOrNull())
    }

    @Test
    fun steerChannel_drainLoop() {
        // Simulates the drain pattern in AgentLoop: `while (true) { val msg = steerChannel.tryReceive().getOrNull() ?: break ... }`
        val channel = Channel<String>(capacity = capacity)
        channel.trySend("steer1")
        channel.trySend("steer2")
        channel.trySend("steer3")

        val collected = mutableListOf<String>()
        while (true) {
            val msg = channel.tryReceive().getOrNull() ?: break
            collected.add(msg)
        }
        assertEquals(3, collected.size)
        assertEquals("steer1", collected[0])
        assertEquals("steer2", collected[1])
        assertEquals("steer3", collected[2])
    }

    @Test
    fun steerChannel_interleaveWriteAndDrain() {
        val channel = Channel<String>(capacity = capacity)

        // First batch
        channel.trySend("a1")
        channel.trySend("a2")
        assertEquals("a1", channel.tryReceive().getOrNull())

        // Second batch before draining
        channel.trySend("b1")
        assertEquals("a2", channel.tryReceive().getOrNull())
        assertEquals("b1", channel.tryReceive().getOrNull())
        assertNull(channel.tryReceive().getOrNull())
    }
}
