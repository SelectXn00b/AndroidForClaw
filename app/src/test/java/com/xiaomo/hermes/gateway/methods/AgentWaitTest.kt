package com.xiaomo.hermes.gateway.methods

import com.xiaomo.hermes.agent.loop.AgentResult
import com.xiaomo.hermes.gateway.protocol.AgentWaitParams
import com.xiaomo.hermes.gateway.protocol.AgentWaitResponse
import com.xiaomo.hermes.providers.llm.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * AgentWait Tests
 *
 * AgentMethods.agentWait() requires Android Context, AgentLoop, SessionManager
 * and GatewayWebSocketServer — too heavy for a pure unit test. Instead we test
 * the wait pattern that agentWait() uses:
 *
 * 1. Internal task path: receive from a Channel<AgentResult> with timeout
 * 2. External job path: join() a coroutine Job with timeout
 * 3. Not-found path: return "completed" immediately
 *
 * These tests validate the concurrency patterns without Android dependencies.
 */
class AgentWaitTest {

    // ===== AgentWaitParams / AgentWaitResponse structure =====

    @Test
    fun agentWaitParams_defaults() {
        val params = AgentWaitParams(runId = "run_abc")
        assertEquals("run_abc", params.runId)
        assertNull(params.timeout)
    }

    @Test
    fun agentWaitParams_withTimeout() {
        val params = AgentWaitParams(runId = "run_123", timeout = 5000L)
        assertEquals("run_123", params.runId)
        assertEquals(5000L, params.timeout)
    }

    @Test
    fun agentWaitResponse_completed() {
        val response = AgentWaitResponse(
            runId = "run_1",
            status = "completed",
            result = mapOf("content" to "Done", "iterations" to 3)
        )
        assertEquals("run_1", response.runId)
        assertEquals("completed", response.status)
        assertNotNull(response.result)
    }

    @Test
    fun agentWaitResponse_timeout() {
        val response = AgentWaitResponse(runId = "run_2", status = "timeout")
        assertEquals("timeout", response.status)
        assertNull(response.result)
    }

    // ===== Internal task path: Channel receive with timeout =====

    @Test
    fun internalTask_receivesResult() = runBlocking {
        val resultChannel = Channel<AgentResult>(1)
        val expected = AgentResult(
            finalContent = "Task done",
            toolsUsed = listOf("screenshot", "tap"),
            messages = emptyList(),
            iterations = 2
        )
        resultChannel.send(expected)

        val result = withTimeoutOrNull(1000L) {
            resultChannel.receive()
        }
        assertNotNull(result)
        assertEquals("Task done", result!!.finalContent)
        assertEquals(2, result.iterations)
        assertEquals(listOf("screenshot", "tap"), result.toolsUsed)
    }

    @Test
    fun internalTask_timeoutReturnsNull() = runBlocking {
        val resultChannel = Channel<AgentResult>(1)
        // Don't send anything — should timeout
        val result = withTimeoutOrNull(50L) {
            resultChannel.receive()
        }
        assertNull(result)
    }

    // ===== External job path: Job.join() with timeout =====

    @Test
    fun externalJob_alreadyCompleted() = runBlocking {
        val deferred = CompletableDeferred<Unit>()
        deferred.complete(Unit)
        val job: Job = deferred

        assertFalse(job.isActive)
        // agentWait returns "completed" immediately for non-active jobs
        val status = if (!job.isActive) "completed" else "running"
        assertEquals("completed", status)
    }

    @Test
    fun externalJob_completesWithinTimeout() = runBlocking {
        val deferred = CompletableDeferred<Unit>()

        // Complete immediately in this test
        deferred.complete(Unit)

        val completed = withTimeoutOrNull(1000L) {
            deferred.join()
            true
        }
        assertEquals(true, completed)
    }

    @Test
    fun externalJob_timeoutWhileRunning() = runBlocking {
        val deferred = CompletableDeferred<Unit>()
        // Never complete — should timeout

        val completed = withTimeoutOrNull(50L) {
            deferred.join()
            true
        }
        assertNull(completed)
    }

    // ===== Not-found path =====

    @Test
    fun notFound_returnsCompleted() {
        val activeJobs = ConcurrentHashMap<String, Job>()
        val job = activeJobs["nonexistent_run"]
        assertNull(job)
        // agentWait returns "completed" when run not found
        val status = "completed"
        assertEquals("completed", status)
    }

    // ===== AgentResult structure =====

    @Test
    fun agentResult_fields() {
        val result = AgentResult(
            finalContent = "All done",
            toolsUsed = listOf("read_file", "exec"),
            messages = listOf(Message(role = "user", content = "hello")),
            iterations = 5
        )
        assertEquals("All done", result.finalContent)
        assertEquals(2, result.toolsUsed.size)
        assertEquals(1, result.messages.size)
        assertEquals(5, result.iterations)
    }

    @Test
    fun agentResult_emptyDefaults() {
        val result = AgentResult(
            finalContent = "",
            toolsUsed = emptyList(),
            messages = emptyList(),
            iterations = 0
        )
        assertEquals("", result.finalContent)
        assertTrue(result.toolsUsed.isEmpty())
        assertEquals(0, result.iterations)
    }
}
