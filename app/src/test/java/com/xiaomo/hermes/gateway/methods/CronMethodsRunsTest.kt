package com.xiaomo.hermes.gateway.methods

import com.xiaomo.hermes.cron.*
import org.junit.Assert.*
import org.junit.Test

/**
 * CronMethods.runs() Data Model and RunStatus Tests
 *
 * CronMethods is a singleton that depends on CronService (Android Context) and
 * uses android.util.Log internally, making it unusable in pure JVM unit tests.
 * org.json.JSONObject is also an Android stub that does not work in unit tests.
 *
 * We test the data models and enum logic that runs() relies on:
 * 1. CronRunLogEntry data class structure and defaults
 * 2. RunStatus enum values and parsing logic
 * 3. CronRunResult structure
 */
class CronMethodsRunsTest {

    // ===== CronRunLogEntry structure =====

    @Test
    fun runLogEntry_defaultAction() {
        val entry = CronRunLogEntry(
            ts = 1000L,
            jobId = "job_1"
        )
        assertEquals("finished", entry.action)
        assertNull(entry.status)
        assertNull(entry.error)
        assertNull(entry.summary)
        assertNull(entry.runAtMs)
        assertNull(entry.durationMs)
        assertNull(entry.nextRunAtMs)
    }

    @Test
    fun runLogEntry_allFields() {
        val entry = CronRunLogEntry(
            ts = 1710000000000L,
            jobId = "job_abc",
            action = "finished",
            status = RunStatus.OK,
            error = null,
            summary = "Completed successfully",
            runAtMs = 1709999990000L,
            durationMs = 10000L,
            nextRunAtMs = 1710003600000L
        )
        assertEquals(1710000000000L, entry.ts)
        assertEquals("job_abc", entry.jobId)
        assertEquals("finished", entry.action)
        assertEquals(RunStatus.OK, entry.status)
        assertNull(entry.error)
        assertEquals("Completed successfully", entry.summary)
        assertEquals(1709999990000L, entry.runAtMs)
        assertEquals(10000L, entry.durationMs)
        assertEquals(1710003600000L, entry.nextRunAtMs)
    }

    @Test
    fun runLogEntry_errorStatus() {
        val entry = CronRunLogEntry(
            ts = 2000L,
            jobId = "job_err",
            status = RunStatus.ERROR,
            error = "Timeout exceeded"
        )
        assertEquals(RunStatus.ERROR, entry.status)
        assertEquals("Timeout exceeded", entry.error)
    }

    @Test
    fun runLogEntry_skippedStatus() {
        val entry = CronRunLogEntry(
            ts = 3000L,
            jobId = "job_skip",
            status = RunStatus.SKIPPED
        )
        assertEquals(RunStatus.SKIPPED, entry.status)
    }

    @Test
    fun runLogEntry_equality() {
        val a = CronRunLogEntry(ts = 1000L, jobId = "j1", status = RunStatus.OK)
        val b = CronRunLogEntry(ts = 1000L, jobId = "j1", status = RunStatus.OK)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun runLogEntry_copy() {
        val original = CronRunLogEntry(ts = 1000L, jobId = "j1", status = RunStatus.OK)
        val copied = original.copy(status = RunStatus.ERROR, error = "failed")
        assertEquals(RunStatus.ERROR, copied.status)
        assertEquals("failed", copied.error)
        // original unchanged
        assertEquals(RunStatus.OK, original.status)
        assertNull(original.error)
    }

    // ===== RunStatus enum =====

    @Test
    fun runStatus_allValues() {
        val values = RunStatus.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(RunStatus.OK))
        assertTrue(values.contains(RunStatus.ERROR))
        assertTrue(values.contains(RunStatus.SKIPPED))
    }

    @Test
    fun runStatus_valueOfFromUppercase() {
        // CronMethods.runs() parses status filter via RunStatus.valueOf(s.uppercase())
        assertEquals(RunStatus.OK, RunStatus.valueOf("OK"))
        assertEquals(RunStatus.ERROR, RunStatus.valueOf("ERROR"))
        assertEquals(RunStatus.SKIPPED, RunStatus.valueOf("SKIPPED"))
    }

    @Test
    fun runStatus_lowercaseName() {
        // CronMethods.runs() serializes status as it.name.lowercase()
        assertEquals("ok", RunStatus.OK.name.lowercase())
        assertEquals("error", RunStatus.ERROR.name.lowercase())
        assertEquals("skipped", RunStatus.SKIPPED.name.lowercase())
    }

    @Test
    fun runStatus_invalidValueThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            RunStatus.valueOf("INVALID")
        }
    }

    // ===== CronRunResult structure =====

    @Test
    fun runResult_okWithSummary() {
        val result = CronRunResult(
            status = RunStatus.OK,
            summary = "Task completed",
            delivered = true,
            deliveryStatus = DeliveryStatus.DELIVERED,
            model = "openai/gpt-4o"
        )
        assertEquals(RunStatus.OK, result.status)
        assertEquals("Task completed", result.summary)
        assertEquals(true, result.delivered)
        assertEquals(DeliveryStatus.DELIVERED, result.deliveryStatus)
        assertEquals("openai/gpt-4o", result.model)
        assertNull(result.deliveryError)
    }

    @Test
    fun runResult_errorWithDeliveryFailure() {
        val result = CronRunResult(
            status = RunStatus.ERROR,
            summary = null,
            delivered = false,
            deliveryStatus = DeliveryStatus.NOT_DELIVERED,
            deliveryError = "Channel unavailable"
        )
        assertEquals(RunStatus.ERROR, result.status)
        assertEquals(false, result.delivered)
        assertEquals("Channel unavailable", result.deliveryError)
    }

    @Test
    fun runResult_defaults() {
        val result = CronRunResult(status = RunStatus.OK)
        assertNull(result.summary)
        assertNull(result.delivered)
        assertNull(result.deliveryStatus)
        assertNull(result.deliveryError)
        assertNull(result.model)
    }

    // ===== DeliveryStatus enum =====

    @Test
    fun deliveryStatus_allValues() {
        val values = DeliveryStatus.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(DeliveryStatus.DELIVERED))
        assertTrue(values.contains(DeliveryStatus.NOT_DELIVERED))
        assertTrue(values.contains(DeliveryStatus.UNKNOWN))
        assertTrue(values.contains(DeliveryStatus.NOT_REQUESTED))
    }

    // ===== CronJobState defaults =====

    @Test
    fun cronJobState_defaults() {
        val state = CronJobState()
        assertNull(state.nextRunAtMs)
        assertNull(state.runningAtMs)
        assertNull(state.lastRunAtMs)
        assertNull(state.lastRunStatus)
        assertNull(state.lastError)
        assertNull(state.lastDurationMs)
        assertEquals(0, state.consecutiveErrors)
        assertNull(state.lastFailureAlertAtMs)
        assertEquals(0, state.scheduleErrorCount)
        assertNull(state.lastDeliveryStatus)
        assertNull(state.lastDelivered)
    }

    @Test
    fun cronJobState_mutableFields() {
        val state = CronJobState()
        state.lastRunStatus = RunStatus.OK
        state.consecutiveErrors = 3
        state.lastError = "connection timeout"
        assertEquals(RunStatus.OK, state.lastRunStatus)
        assertEquals(3, state.consecutiveErrors)
        assertEquals("connection timeout", state.lastError)
    }
}
