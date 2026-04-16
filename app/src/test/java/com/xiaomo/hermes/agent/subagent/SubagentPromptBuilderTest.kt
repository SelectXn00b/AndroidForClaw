package com.xiaomo.hermes.agent.subagent

import org.junit.Assert.*
import org.junit.Test

class SubagentPromptBuilderTest {

    // ===== isSilentReplyText =====

    @Test
    fun `exact NO_REPLY is silent`() {
        assertTrue(SubagentPromptBuilder.isSilentReplyText("NO_REPLY"))
    }

    @Test
    fun `NO_REPLY with trailing space prefix is silent`() {
        assertTrue(SubagentPromptBuilder.isSilentReplyText("NO_REPLY additional context"))
    }

    @Test
    fun `NO_REPLY with surrounding whitespace is silent`() {
        assertTrue(SubagentPromptBuilder.isSilentReplyText("  NO_REPLY  "))
    }

    @Test
    fun `null is not silent`() {
        assertFalse(SubagentPromptBuilder.isSilentReplyText(null))
    }

    @Test
    fun `empty string is not silent`() {
        assertFalse(SubagentPromptBuilder.isSilentReplyText(""))
    }

    @Test
    fun `other text is not silent`() {
        assertFalse(SubagentPromptBuilder.isSilentReplyText("hello world"))
    }

    @Test
    fun `NO_REPLY embedded in text is not silent`() {
        assertFalse(SubagentPromptBuilder.isSilentReplyText("prefix NO_REPLY"))
    }

    // ===== buildAnnounceReplyInstruction =====

    @Test
    fun `subagent requester gets orchestration instruction`() {
        val result = SubagentPromptBuilder.buildAnnounceReplyInstruction(
            requesterIsSubagent = true
        )
        assertTrue(result.contains("orchestration"))
        assertTrue(result.contains(SubagentPromptBuilder.SILENT_REPLY_TOKEN))
    }

    @Test
    fun `non-subagent requester with completion message gets delivery instruction`() {
        val result = SubagentPromptBuilder.buildAnnounceReplyInstruction(
            requesterIsSubagent = false,
            expectsCompletionMessage = true
        )
        assertTrue(result.contains("user-facing"))
        assertFalse(result.contains(SubagentPromptBuilder.SILENT_REPLY_TOKEN))
    }

    @Test
    fun `non-subagent requester without completion message includes NO_REPLY option`() {
        val result = SubagentPromptBuilder.buildAnnounceReplyInstruction(
            requesterIsSubagent = false,
            expectsCompletionMessage = false
        )
        assertTrue(result.contains(SubagentPromptBuilder.SILENT_REPLY_TOKEN))
    }

    // ===== buildChildCompletionFindings =====

    @Test
    fun `empty list returns null`() {
        assertNull(SubagentPromptBuilder.buildChildCompletionFindings(emptyList()))
    }

    @Test
    fun `single child produces numbered finding`() {
        val child = makeRecord(
            label = "research",
            task = "find docs",
            outcome = SubagentRunOutcome(SubagentRunStatus.OK),
            frozenResultText = "Found 5 relevant docs"
        )
        val result = SubagentPromptBuilder.buildChildCompletionFindings(listOf(child))!!
        assertTrue(result.contains("Child completion results:"))
        assertTrue(result.contains("1. research"))
        assertTrue(result.contains("status: done"))
        assertTrue(result.contains("Found 5 relevant docs"))
    }

    @Test
    fun `multiple children sorted by createdAt`() {
        val child1 = makeRecord(label = "first", createdAt = 100L, outcome = SubagentRunOutcome(SubagentRunStatus.OK))
        val child2 = makeRecord(label = "second", createdAt = 50L, outcome = SubagentRunOutcome(SubagentRunStatus.OK))
        val result = SubagentPromptBuilder.buildChildCompletionFindings(listOf(child1, child2))!!
        val idx1 = result.indexOf("second")
        val idx2 = result.indexOf("first")
        assertTrue("second (createdAt=50) should come before first (createdAt=100)", idx1 < idx2)
    }

    @Test
    fun `silent result text is excluded`() {
        val child = makeRecord(
            label = "quiet",
            outcome = SubagentRunOutcome(SubagentRunStatus.OK),
            frozenResultText = "NO_REPLY"
        )
        val result = SubagentPromptBuilder.buildChildCompletionFindings(listOf(child))!!
        assertFalse(result.contains("BEGIN_UNTRUSTED_CHILD_RESULT"))
    }

    // ===== build =====

    @Test
    fun `prompt contains task and label`() {
        val caps = resolveSubagentCapabilities(1, 3)
        val prompt = SubagentPromptBuilder.build(
            task = "Analyze the logs",
            label = "log-analyzer",
            capabilities = caps,
            parentSessionKey = "parent-1",
            childSessionKey = "child-1"
        )
        assertTrue(prompt.contains("Analyze the logs"))
        assertTrue(prompt.contains("log-analyzer"))
        assertTrue(prompt.contains("parent-1"))
        assertTrue(prompt.contains("child-1"))
    }

    @Test
    fun `LEAF prompt says cannot spawn`() {
        val caps = resolveSubagentCapabilities(2, 2)
        val prompt = SubagentPromptBuilder.build(
            task = "simple task",
            label = "worker",
            capabilities = caps,
            parentSessionKey = "p",
            childSessionKey = "c"
        )
        assertTrue(prompt.contains("CANNOT spawn"))
    }

    @Test
    fun `spawnable agent prompt says CAN spawn`() {
        val caps = resolveSubagentCapabilities(0, 2)
        val prompt = SubagentPromptBuilder.build(
            task = "orchestrate",
            label = "orchestrator",
            capabilities = caps,
            parentSessionKey = "p",
            childSessionKey = "c"
        )
        assertTrue(prompt.contains("CAN spawn"))
    }

    @Test
    fun `prompt includes depth and role`() {
        val caps = resolveSubagentCapabilities(1, 3)
        val prompt = SubagentPromptBuilder.build(
            task = "task",
            label = "label",
            capabilities = caps,
            parentSessionKey = "p",
            childSessionKey = "c"
        )
        assertTrue(prompt.contains("**Depth:** 1"))
        assertTrue(prompt.contains("role: orchestrator"))
    }

    // ===== buildAnnouncement =====

    @Test
    fun `announcement contains key sections`() {
        val record = makeRecord(
            label = "my-agent",
            task = "do stuff",
            outcome = SubagentRunOutcome(SubagentRunStatus.OK),
            frozenResultText = "All done successfully"
        )
        val announcement = SubagentPromptBuilder.buildAnnouncement(
            record = record,
            outcome = SubagentRunOutcome(SubagentRunStatus.OK)
        )
        assertTrue(announcement.contains("[Subagent Complete]"))
        assertTrue(announcement.contains("my-agent"))
        assertTrue(announcement.contains("completed successfully"))
        assertTrue(announcement.contains("All done successfully"))
        assertTrue(announcement.contains("BEGIN_UNTRUSTED_SUBAGENT_RESULT"))
    }

    @Test
    fun `announcement for error outcome shows failed`() {
        val record = makeRecord(
            outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "crash")
        )
        val announcement = SubagentPromptBuilder.buildAnnouncement(
            record = record,
            outcome = SubagentRunOutcome(SubagentRunStatus.ERROR, "crash")
        )
        assertTrue(announcement.contains("failed"))
        assertTrue(announcement.contains("crash"))
    }

    // ===== Helpers =====

    private fun makeRecord(
        label: String = "test-label",
        task: String = "test-task",
        childSessionKey: String = "child-1",
        outcome: SubagentRunOutcome? = null,
        frozenResultText: String? = null,
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
        this.outcome = outcome
        this.frozenResultText = frozenResultText
        this.startedAt = createdAt
        this.endedAt = createdAt + 1000
    }
}
