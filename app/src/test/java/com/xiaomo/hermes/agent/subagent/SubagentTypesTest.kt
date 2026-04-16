package com.xiaomo.hermes.agent.subagent

import org.junit.Assert.*
import org.junit.Test

class SubagentTypesTest {

    // ===== resolveSubagentRole =====

    @Test
    fun `depth 0 resolves to MAIN`() {
        assertEquals(SubagentSessionRole.MAIN, resolveSubagentRole(0, 2))
    }

    @Test
    fun `negative depth resolves to MAIN`() {
        assertEquals(SubagentSessionRole.MAIN, resolveSubagentRole(-1, 2))
    }

    @Test
    fun `depth between 0 and maxSpawnDepth resolves to ORCHESTRATOR`() {
        assertEquals(SubagentSessionRole.ORCHESTRATOR, resolveSubagentRole(1, 3))
        assertEquals(SubagentSessionRole.ORCHESTRATOR, resolveSubagentRole(2, 3))
    }

    @Test
    fun `depth equal to maxSpawnDepth resolves to LEAF`() {
        assertEquals(SubagentSessionRole.LEAF, resolveSubagentRole(2, 2))
    }

    @Test
    fun `depth greater than maxSpawnDepth resolves to LEAF`() {
        assertEquals(SubagentSessionRole.LEAF, resolveSubagentRole(5, 2))
    }

    @Test
    fun `maxSpawnDepth 1 means depth 1 is LEAF`() {
        assertEquals(SubagentSessionRole.MAIN, resolveSubagentRole(0, 1))
        assertEquals(SubagentSessionRole.LEAF, resolveSubagentRole(1, 1))
    }

    // ===== resolveSubagentControlScope =====

    @Test
    fun `MAIN role gets CHILDREN scope`() {
        assertEquals(SubagentControlScope.CHILDREN, resolveSubagentControlScope(SubagentSessionRole.MAIN))
    }

    @Test
    fun `ORCHESTRATOR role gets CHILDREN scope`() {
        assertEquals(SubagentControlScope.CHILDREN, resolveSubagentControlScope(SubagentSessionRole.ORCHESTRATOR))
    }

    @Test
    fun `LEAF role gets NONE scope`() {
        assertEquals(SubagentControlScope.NONE, resolveSubagentControlScope(SubagentSessionRole.LEAF))
    }

    // ===== resolveSubagentCapabilities =====

    @Test
    fun `depth 0 can spawn and control children`() {
        val caps = resolveSubagentCapabilities(0, 2)
        assertEquals(SubagentSessionRole.MAIN, caps.role)
        assertEquals(SubagentControlScope.CHILDREN, caps.controlScope)
        assertTrue(caps.canSpawn)
        assertTrue(caps.canControlChildren)
        assertEquals(0, caps.depth)
    }

    @Test
    fun `LEAF cannot spawn or control children`() {
        val caps = resolveSubagentCapabilities(2, 2)
        assertEquals(SubagentSessionRole.LEAF, caps.role)
        assertEquals(SubagentControlScope.NONE, caps.controlScope)
        assertFalse(caps.canSpawn)
        assertFalse(caps.canControlChildren)
    }

    @Test
    fun `ORCHESTRATOR can spawn and control children`() {
        val caps = resolveSubagentCapabilities(1, 3)
        assertEquals(SubagentSessionRole.ORCHESTRATOR, caps.role)
        assertTrue(caps.canSpawn)
        assertTrue(caps.canControlChildren)
    }

    // ===== resolveLifecycleOutcome =====

    @Test
    fun `ERROR outcome maps to ERROR lifecycle outcome`() {
        val outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "something failed")
        assertEquals(SubagentLifecycleEndedOutcome.ERROR, resolveLifecycleOutcome(outcome))
    }

    @Test
    fun `TIMEOUT outcome maps to TIMEOUT lifecycle outcome`() {
        val outcome = SubagentRunOutcome(SubagentRunStatus.TIMEOUT)
        assertEquals(SubagentLifecycleEndedOutcome.TIMEOUT, resolveLifecycleOutcome(outcome))
    }

    @Test
    fun `OK outcome maps to OK lifecycle outcome`() {
        val outcome = SubagentRunOutcome(SubagentRunStatus.OK)
        assertEquals(SubagentLifecycleEndedOutcome.OK, resolveLifecycleOutcome(outcome))
    }

    @Test
    fun `UNKNOWN outcome maps to OK lifecycle outcome`() {
        val outcome = SubagentRunOutcome(SubagentRunStatus.UNKNOWN)
        assertEquals(SubagentLifecycleEndedOutcome.OK, resolveLifecycleOutcome(outcome))
    }

    @Test
    fun `null outcome maps to OK lifecycle outcome`() {
        assertEquals(SubagentLifecycleEndedOutcome.OK, resolveLifecycleOutcome(null))
    }

    // ===== capFrozenResultText =====

    @Test
    fun `null text returns null`() {
        assertNull(capFrozenResultText(null))
    }

    @Test
    fun `blank text returns null`() {
        assertNull(capFrozenResultText("   "))
    }

    @Test
    fun `short text returned trimmed`() {
        assertEquals("hello world", capFrozenResultText("  hello world  "))
    }

    @Test
    fun `text within limit returned as-is`() {
        val text = "a".repeat(1000)
        assertEquals(text, capFrozenResultText(text))
    }

    @Test
    fun `text exceeding 100KB is truncated with notice`() {
        val text = "x".repeat(FROZEN_RESULT_TEXT_MAX_BYTES + 1000)
        val result = capFrozenResultText(text)!!
        assertTrue(result.contains("[truncated:"))
        assertTrue(result.contains("exceeds ${FROZEN_RESULT_TEXT_MAX_BYTES / 1024}KB limit"))
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= FROZEN_RESULT_TEXT_MAX_BYTES + 200)
    }

    // ===== computeAnnounceRetryDelayMs =====

    @Test
    fun `retry index 0 returns first delay`() {
        assertEquals(5_000L, computeAnnounceRetryDelayMs(0))
    }

    @Test
    fun `retry index 1 returns second delay`() {
        assertEquals(10_000L, computeAnnounceRetryDelayMs(1))
    }

    @Test
    fun `retry index 2 returns third delay`() {
        assertEquals(20_000L, computeAnnounceRetryDelayMs(2))
    }

    @Test
    fun `retry index beyond table returns null`() {
        assertNull(computeAnnounceRetryDelayMs(3))
        assertNull(computeAnnounceRetryDelayMs(100))
    }

    // ===== resolveSubagentLabel =====

    @Test
    fun `label is used when non-blank`() {
        val record = makeRecord(label = "my-label", task = "my-task")
        assertEquals("my-label", resolveSubagentLabel(record))
    }

    @Test
    fun `task is used when label is blank`() {
        val record = makeRecord(label = "", task = "do something")
        assertEquals("do something", resolveSubagentLabel(record))
    }

    @Test
    fun `long task is truncated to 48 chars`() {
        val longTask = "a".repeat(100)
        val record = makeRecord(label = "", task = longTask)
        assertEquals(48, resolveSubagentLabel(record).length)
    }

    @Test
    fun `task newlines replaced with spaces`() {
        val record = makeRecord(label = "", task = "line1\nline2")
        assertEquals("line1 line2", resolveSubagentLabel(record))
    }

    @Test
    fun `childSessionKey used when label and task blank`() {
        val record = makeRecord(label = "", task = "", childSessionKey = "sess-123")
        assertEquals("sess-123", resolveSubagentLabel(record))
    }

    @Test
    fun `fallback to subagent when all blank`() {
        val record = makeRecord(label = "", task = "", childSessionKey = "")
        assertEquals("subagent", resolveSubagentLabel(record))
    }

    // ===== resolveSubagentSessionStatus =====

    @Test
    fun `null record returns unknown`() {
        assertEquals("unknown", resolveSubagentSessionStatus(null))
    }

    @Test
    fun `active record returns running`() {
        val record = makeRecord(endedAt = null)
        assertEquals("running", resolveSubagentSessionStatus(record))
    }

    @Test
    fun `killed record returns killed`() {
        val record = makeRecord(
            endedAt = 100L,
            endedReason = SubagentLifecycleEndedReason.SUBAGENT_KILLED
        )
        assertEquals("killed", resolveSubagentSessionStatus(record))
    }

    @Test
    fun `error outcome returns failed`() {
        val record = makeRecord(
            endedAt = 100L,
            outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "oops")
        )
        assertEquals("failed", resolveSubagentSessionStatus(record))
    }

    @Test
    fun `timeout outcome returns timeout`() {
        val record = makeRecord(
            endedAt = 100L,
            outcome = SubagentRunOutcome(SubagentRunStatus.TIMEOUT)
        )
        assertEquals("timeout", resolveSubagentSessionStatus(record))
    }

    @Test
    fun `OK outcome returns done`() {
        val record = makeRecord(
            endedAt = 100L,
            outcome = SubagentRunOutcome(SubagentRunStatus.OK)
        )
        assertEquals("done", resolveSubagentSessionStatus(record))
    }

    // ===== runOutcomesEqual =====

    @Test
    fun `both null are equal`() {
        assertTrue(runOutcomesEqual(null, null))
    }

    @Test
    fun `one null one not are not equal`() {
        assertFalse(runOutcomesEqual(SubagentRunOutcome(SubagentRunStatus.OK), null))
        assertFalse(runOutcomesEqual(null, SubagentRunOutcome(SubagentRunStatus.OK)))
    }

    @Test
    fun `same status are equal`() {
        assertTrue(runOutcomesEqual(
            SubagentRunOutcome(SubagentRunStatus.OK),
            SubagentRunOutcome(SubagentRunStatus.OK)
        ))
    }

    @Test
    fun `different status are not equal`() {
        assertFalse(runOutcomesEqual(
            SubagentRunOutcome(SubagentRunStatus.OK),
            SubagentRunOutcome(SubagentRunStatus.ERROR, "err")
        ))
    }

    @Test
    fun `ERROR with different error messages are not equal`() {
        assertFalse(runOutcomesEqual(
            SubagentRunOutcome(SubagentRunStatus.ERROR, "a"),
            SubagentRunOutcome(SubagentRunStatus.ERROR, "b")
        ))
    }

    @Test
    fun `ERROR with same error messages are equal`() {
        assertTrue(runOutcomesEqual(
            SubagentRunOutcome(SubagentRunStatus.ERROR, "same"),
            SubagentRunOutcome(SubagentRunStatus.ERROR, "same")
        ))
    }

    // ===== isActiveSubagentRun =====

    @Test
    fun `active record with no endedAt is active`() {
        val record = makeRecord(endedAt = null)
        assertTrue(isActiveSubagentRun(record) { 0 })
    }

    @Test
    fun `ended record with no pending descendants is not active`() {
        val record = makeRecord(endedAt = 100L)
        assertFalse(isActiveSubagentRun(record) { 0 })
    }

    @Test
    fun `ended record with pending descendants is active`() {
        val record = makeRecord(endedAt = 100L)
        assertTrue(isActiveSubagentRun(record) { 2 })
    }

    // ===== resolveCleanupCompletionReason =====

    @Test
    fun `endedReason is returned when set`() {
        val record = makeRecord(endedReason = SubagentLifecycleEndedReason.SUBAGENT_KILLED)
        assertEquals(SubagentLifecycleEndedReason.SUBAGENT_KILLED, resolveCleanupCompletionReason(record))
    }

    @Test
    fun `fallback to SUBAGENT_COMPLETE when endedReason is null`() {
        val record = makeRecord(endedReason = null)
        assertEquals(SubagentLifecycleEndedReason.SUBAGENT_COMPLETE, resolveCleanupCompletionReason(record))
    }

    // ===== resolveSubagentSessionEndedOutcome =====

    @Test
    fun `SESSION_RESET maps to RESET`() {
        assertEquals(
            SubagentLifecycleEndedOutcome.RESET,
            resolveSubagentSessionEndedOutcome(SubagentLifecycleEndedReason.SESSION_RESET)
        )
    }

    @Test
    fun `SESSION_DELETE maps to DELETED`() {
        assertEquals(
            SubagentLifecycleEndedOutcome.DELETED,
            resolveSubagentSessionEndedOutcome(SubagentLifecycleEndedReason.SESSION_DELETE)
        )
    }

    @Test
    fun `SUBAGENT_COMPLETE maps to DELETED`() {
        assertEquals(
            SubagentLifecycleEndedOutcome.DELETED,
            resolveSubagentSessionEndedOutcome(SubagentLifecycleEndedReason.SUBAGENT_COMPLETE)
        )
    }

    // ===== getSubagentSessionStartedAt =====

    @Test
    fun `sessionStartedAt preferred`() {
        val record = makeRecord(sessionStartedAt = 10L, startedAt = 20L, createdAt = 30L)
        assertEquals(10L, getSubagentSessionStartedAt(record))
    }

    @Test
    fun `startedAt used when sessionStartedAt null`() {
        val record = makeRecord(sessionStartedAt = null, startedAt = 20L, createdAt = 30L)
        assertEquals(20L, getSubagentSessionStartedAt(record))
    }

    @Test
    fun `createdAt used as last resort`() {
        val record = makeRecord(sessionStartedAt = null, startedAt = null, createdAt = 30L)
        assertEquals(30L, getSubagentSessionStartedAt(record))
    }

    // ===== resolveDeferredCleanupDecision =====

    @Test
    fun `completion flow with active descendants defers`() {
        val record = makeRecord(
            endedAt = System.currentTimeMillis(),
            expectsCompletionMessage = true,
            announceRetryCount = 0
        )
        val decision = resolveDeferredCleanupDecision(
            entry = record,
            now = System.currentTimeMillis(),
            activeDescendantRuns = 2
        )
        assertTrue(decision is DeferredCleanupDecision.DeferDescendants)
        assertEquals(DEFER_DESCENDANT_DELAY_MS, (decision as DeferredCleanupDecision.DeferDescendants).delayMs)
    }

    @Test
    fun `completion flow with active descendants gives up after hard expiry`() {
        val now = System.currentTimeMillis()
        val record = makeRecord(
            endedAt = now - ANNOUNCE_COMPLETION_HARD_EXPIRY_MS - 1000,
            expectsCompletionMessage = true,
            announceRetryCount = 0
        )
        val decision = resolveDeferredCleanupDecision(
            entry = record,
            now = now,
            activeDescendantRuns = 2
        )
        assertTrue(decision is DeferredCleanupDecision.GiveUp)
        assertEquals("expiry", (decision as DeferredCleanupDecision.GiveUp).reason)
    }

    @Test
    fun `retry limit reached gives up`() {
        val now = System.currentTimeMillis()
        val record = makeRecord(
            endedAt = now - 1000,
            expectsCompletionMessage = false,
            announceRetryCount = MAX_ANNOUNCE_RETRY_COUNT - 1
        )
        val decision = resolveDeferredCleanupDecision(
            entry = record,
            now = now,
            activeDescendantRuns = 0
        )
        assertTrue(decision is DeferredCleanupDecision.GiveUp)
        assertEquals("retry-limit", (decision as DeferredCleanupDecision.GiveUp).reason)
    }

    @Test
    fun `non-completion flow retries when under limits`() {
        val now = System.currentTimeMillis()
        val record = makeRecord(
            endedAt = now - 1000,
            expectsCompletionMessage = false,
            announceRetryCount = 0
        )
        val decision = resolveDeferredCleanupDecision(
            entry = record,
            now = now,
            activeDescendantRuns = 0
        )
        assertTrue(decision is DeferredCleanupDecision.Retry)
        assertEquals(1, (decision as DeferredCleanupDecision.Retry).retryCount)
    }

    @Test
    fun `non-completion flow gives up after expiry`() {
        val now = System.currentTimeMillis()
        val record = makeRecord(
            endedAt = now - ANNOUNCE_EXPIRY_MS - 1000,
            expectsCompletionMessage = false,
            announceRetryCount = 0
        )
        val decision = resolveDeferredCleanupDecision(
            entry = record,
            now = now,
            activeDescendantRuns = 0
        )
        assertTrue(decision is DeferredCleanupDecision.GiveUp)
        assertEquals("expiry", (decision as DeferredCleanupDecision.GiveUp).reason)
    }

    // ===== Wire values =====

    @Test
    fun `SpawnMode wire values`() {
        assertEquals("run", SpawnMode.RUN.wireValue)
        assertEquals("session", SpawnMode.SESSION.wireValue)
    }

    @Test
    fun `SubagentLifecycleEndedReason wire values use hyphens`() {
        assertEquals("subagent-complete", SubagentLifecycleEndedReason.SUBAGENT_COMPLETE.wireValue)
        assertEquals("session-reset", SubagentLifecycleEndedReason.SESSION_RESET.wireValue)
    }

    // ===== Helpers =====

    private fun makeRecord(
        label: String = "test-label",
        task: String = "test-task",
        childSessionKey: String = "child-1",
        endedAt: Long? = null,
        endedReason: SubagentLifecycleEndedReason? = null,
        outcome: SubagentRunOutcome? = null,
        expectsCompletionMessage: Boolean = true,
        announceRetryCount: Int = 0,
        sessionStartedAt: Long? = null,
        startedAt: Long? = null,
        createdAt: Long = System.currentTimeMillis(),
    ) = SubagentRunRecord(
        runId = "run-1",
        childSessionKey = childSessionKey,
        requesterSessionKey = "parent-1",
        task = task,
        label = label,
        model = null,
        spawnMode = SpawnMode.RUN,
        createdAt = createdAt,
    ).apply {
        this.endedAt = endedAt
        this.endedReason = endedReason
        this.outcome = outcome
        this.expectsCompletionMessage = expectsCompletionMessage
        this.announceRetryCount = announceRetryCount
        this.sessionStartedAt = sessionStartedAt
        this.startedAt = startedAt
    }
}
