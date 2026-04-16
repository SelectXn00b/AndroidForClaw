/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-announce.ts (buildSubagentSystemPrompt, output extraction, findings)
 * - ../openclaw/src/auto-reply/tokens.ts (SILENT_REPLY_TOKEN)
 *
 * Hermes adaptation: builds multi-section Markdown system prompt for subagent sessions.
 * Also handles output text extraction, silent reply detection, and announce message building.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.providers.llm.Message

/**
 * Snapshot of subagent output history, capturing fragments and metadata.
 * Aligned with OpenClaw SubagentOutputSnapshot.
 */
data class SubagentOutputSnapshot(
    val assistantFragments: MutableList<String> = mutableListOf(),
    var toolCallCount: Int = 0,
    var latestAssistantText: String? = null,
    var latestSilentText: String? = null,
    var latestRawText: String? = null,
)

/**
 * Builds the system prompt injected into a subagent session.
 * Also provides announce message building and output text selection.
 * Aligned with OpenClaw buildSubagentSystemPrompt + announce helpers.
 */
object SubagentPromptBuilder {

    /** Aligned with OpenClaw SILENT_REPLY_TOKEN */
    const val SILENT_REPLY_TOKEN = "NO_REPLY"

    /**
     * Check if text is a silent reply (should be suppressed from output).
     * Aligned with OpenClaw isSilentReplyText.
     */
    fun isSilentReplyText(text: String?): Boolean {
        if (text == null) return false
        val trimmed = text.trim()
        return trimmed == SILENT_REPLY_TOKEN || trimmed.startsWith("$SILENT_REPLY_TOKEN ")
    }

    /**
     * Count tool_use blocks in an assistant message content.
     * Aligned with OpenClaw countAssistantToolCalls.
     */
    private fun countToolCalls(message: Message): Int {
        // On Android, tool calls are tracked as separate fields in Message
        // Count tool_use entries if present in the content array
        return message.toolCalls?.size ?: 0
    }

    /**
     * Extract text content from a message.
     * Aligned with OpenClaw extractSubagentOutputText.
     */
    private fun extractOutputText(message: Message): String {
        return message.content.trim()
    }

    /**
     * Scan conversation messages and build a snapshot.
     * Tracks fragments, tool calls, and latest text states.
     * Aligned with OpenClaw summarizeSubagentOutputHistory (full fragment accumulation).
     */
    fun summarizeSubagentOutputHistory(messages: List<Message>): SubagentOutputSnapshot {
        val snapshot = SubagentOutputSnapshot()

        for (msg in messages) {
            if (msg.role == "assistant") {
                snapshot.toolCallCount += countToolCalls(msg)
                val text = extractOutputText(msg)
                if (text.isBlank()) continue

                if (isSilentReplyText(text)) {
                    snapshot.latestSilentText = text
                    snapshot.latestAssistantText = null
                    snapshot.assistantFragments.clear()
                    continue
                }

                snapshot.latestSilentText = null
                snapshot.latestAssistantText = text
                snapshot.assistantFragments.add(text)
                continue
            }

            // Non-assistant messages: capture raw text
            val text = extractOutputText(msg)
            if (text.isNotBlank()) {
                snapshot.latestRawText = text
            }
        }
        return snapshot
    }

    /**
     * Format partial progress for timeout/error cases.
     * Aligned with OpenClaw formatSubagentPartialProgress.
     */
    private fun formatPartialProgress(
        snapshot: SubagentOutputSnapshot,
        outcome: SubagentRunOutcome?,
    ): String? {
        // Only show partial progress for non-OK outcomes
        if (outcome?.status == SubagentRunStatus.OK) return null
        if (snapshot.assistantFragments.isEmpty() && snapshot.toolCallCount == 0) return null

        return buildString {
            if (snapshot.assistantFragments.isNotEmpty()) {
                append("Partial output (${snapshot.assistantFragments.size} fragment(s)):\n")
                for (fragment in snapshot.assistantFragments) {
                    appendLine(fragment.take(500))
                }
            }
            if (snapshot.toolCallCount > 0) {
                appendLine("(${snapshot.toolCallCount} tool call(s) made before termination)")
            }
        }.trimEnd()
    }

    /**
     * Select the best output text for announce, using snapshot-based selection.
     * Priority: latestSilentText → latestAssistantText → partial progress → latestRawText
     *           → frozenResultText → fallback frozen.
     * Aligned with OpenClaw selectSubagentOutputText.
     */
    fun selectSubagentOutputText(
        record: SubagentRunRecord,
        messages: List<Message>?,
        outcome: SubagentRunOutcome? = null,
    ): String? {
        // 1. Primary: frozenResultText (already captured at completion)
        record.frozenResultText?.let { text ->
            if (!isSilentReplyText(text)) return text
        }

        // 2. Snapshot-based selection from live history
        if (messages != null) {
            val snapshot = summarizeSubagentOutputHistory(messages)

            // Silent reply takes priority (aligned with OpenClaw)
            snapshot.latestSilentText?.let { return it }

            // Latest non-silent assistant text
            snapshot.latestAssistantText?.let { return it }

            // Partial progress for timeout/error (aligned with OpenClaw formatSubagentPartialProgress)
            formatPartialProgress(snapshot, outcome)?.let { return it }

            // Raw text fallback
            snapshot.latestRawText?.let { return it }
        }

        // 3. Fallback frozen result
        record.fallbackFrozenResultText?.let { text ->
            if (!isSilentReplyText(text)) return text
        }
        return null
    }

    /**
     * Build the complete subagent system prompt.
     * Aligned with OpenClaw buildSubagentSystemPrompt.
     *
     * @param task The assigned task description
     * @param label Display label for this subagent
     * @param capabilities Resolved capabilities (role, depth, canSpawn)
     * @param parentSessionKey The parent/requester session key
     * @param childSessionKey This subagent's session key
     */
    fun build(
        task: String,
        label: String,
        capabilities: SubagentCapabilities,
        parentSessionKey: String,
        childSessionKey: String,
    ): String {
        val parentLabel = if (capabilities.depth >= 2) "parent orchestrator" else "main agent"
        val displayLabel = label.ifBlank { task.take(48).replace('\n', ' ') }

        return buildString {
            appendLine("# Subagent Context")
            appendLine()

            // == Your Role ==
            appendLine("## Your Role")
            appendLine()
            appendLine("You are a **subagent** spawned to handle a specific task. You are NOT the $parentLabel — you are an isolated worker session.")
            appendLine()
            appendLine("**Your task:** $task")
            appendLine()

            // == Rules ==
            appendLine("## Rules")
            appendLine()
            appendLine("1. **Stay focused on your assigned task.** Do not deviate, explore unrelated topics, or take actions outside your task scope.")
            appendLine("2. **Complete the task fully.** Provide a clear, comprehensive result when done.")
            appendLine("3. **Do not initiate conversations with users.** You have no direct user interaction — your output goes back to your parent agent.")
            appendLine("4. **Be ephemeral.** Your session exists solely for this task. Once complete, your result is announced to the parent and this session ends.")
            appendLine("5. **Trust push-based completion.** Your final output is automatically delivered to the parent. Do not poll, sleep, or check status — just do the work and reply with your findings.")
            appendLine("6. **If you see compacted output from a previous context window**, your earlier work was preserved. Continue from where you left off based on the summary.")
            appendLine("7. **If a child completion arrives AFTER your final answer**, reply ONLY with $SILENT_REPLY_TOKEN.")
            appendLine()

            // == Output Format ==
            appendLine("## Output Format")
            appendLine()
            appendLine("When your task is complete, provide a clear summary of your findings/results. Include:")
            appendLine("- Key findings or results")
            appendLine("- Any relevant data, code, or references")
            appendLine("- Errors encountered and how they were handled (if any)")
            appendLine()

            // == What You DON'T Do ==
            appendLine("## What You DON'T Do")
            appendLine()
            appendLine("- No direct user conversations or messages")
            appendLine("- No sending external messages (Feishu, Discord, Slack, etc.)")
            appendLine("- No scheduling cron jobs")
            appendLine("- No pretending to be the $parentLabel")
            appendLine()

            // == Sub-Agent Spawning (conditional) ==
            if (capabilities.canSpawn) {
                appendLine("## Sub-Agent Spawning")
                appendLine()
                appendLine("You CAN spawn your own sub-agents using the `sessions_spawn` tool to parallelize work.")
                appendLine("- Keep tasks focused and well-scoped")
                appendLine("- Wait for all child completions before sending your final answer")
                appendLine("- Do NOT poll for child status — completions arrive as messages automatically")
                appendLine()
            } else {
                appendLine("## Sub-Agent Spawning")
                appendLine()
                if (capabilities.depth >= 2) {
                    appendLine("You are a **leaf worker**. You CANNOT spawn further sub-agents. Complete your task directly.")
                } else {
                    appendLine("You CANNOT spawn further sub-agents at this depth. Complete your task directly.")
                }
                appendLine()
            }

            // == Session Context ==
            appendLine("## Session Context")
            appendLine()
            if (displayLabel.isNotBlank()) {
                appendLine("- **Label:** $displayLabel")
            }
            appendLine("- **Requester session:** $parentSessionKey")
            appendLine("- **Your session:** $childSessionKey")
            appendLine("- **Depth:** ${capabilities.depth} / role: ${capabilities.role.wireValue}")
            appendLine()

            // == Device Context (Android-specific) ==
            appendLine("## Device Context")
            appendLine()
            appendLine("- **Platform:** Android")
            appendLine("- **Execution:** In-process coroutine (no network latency between agents)")
        }.trimEnd()
    }

    /**
     * Build the reply instruction for the parent agent when processing an announce.
     * Aligned with OpenClaw buildAnnounceReplyInstruction.
     *
     * @param requesterIsSubagent Whether the requester is itself a subagent
     * @param expectsCompletionMessage Whether the requester expects a completion message
     */
    fun buildAnnounceReplyInstruction(
        requesterIsSubagent: Boolean,
        expectsCompletionMessage: Boolean = true,
    ): String {
        if (requesterIsSubagent) {
            return "Convert this completion into a concise internal orchestration update for your parent agent in your own words. Keep this internal context private (don't mention system/log/stats/session details or announce type). If this result is duplicate or no update is needed, reply ONLY: $SILENT_REPLY_TOKEN."
        }
        if (expectsCompletionMessage) {
            return "A completed subagent is ready for user delivery. Convert the result above into your normal assistant voice and send that user-facing update now. Keep this internal context private (don't mention system/log/stats/session details or announce type)."
        }
        return "A completed subagent is ready for user delivery. Convert the result above into your normal assistant voice and send that user-facing update now. Keep this internal context private (don't mention system/log/stats/session details or announce type), and do not copy the internal event text verbatim. Reply ONLY: $SILENT_REPLY_TOKEN if this exact result was already delivered to the user in this same turn."
    }

    /**
     * Describe a subagent outcome for display.
     * Aligned with OpenClaw describeSubagentOutcome.
     */
    private fun describeOutcome(outcome: SubagentRunOutcome?): String {
        return when (outcome?.status) {
            SubagentRunStatus.OK -> "done"
            SubagentRunStatus.ERROR -> "failed: ${outcome.error ?: "unknown"}"
            SubagentRunStatus.TIMEOUT -> "timed out"
            else -> "unknown"
        }
    }

    /**
     * Build the announcement message injected into the parent's steer channel
     * when a subagent completes.
     * Aligned with OpenClaw runSubagentAnnounceFlow structure + buildAnnounceReplyInstruction.
     *
     * Uses structured format with:
     * 1. Completion event header
     * 2. Output text (untrusted, wrapped)
     * 3. Child completion findings
     * 4. Reply instruction for the parent LLM
     */
    fun buildAnnouncement(
        record: SubagentRunRecord,
        outcome: SubagentRunOutcome,
        findings: String? = null,
        requesterIsSubagent: Boolean = false,
    ): String {
        val statusLabel = when (outcome.status) {
            SubagentRunStatus.OK -> "completed successfully"
            SubagentRunStatus.ERROR -> "failed: ${outcome.error ?: "unknown error"}"
            SubagentRunStatus.TIMEOUT -> "timed out"
            SubagentRunStatus.UNKNOWN -> "ended with unknown status"
        }
        val displayLabel = resolveSubagentLabel(record)

        return buildString {
            appendLine("[Subagent Complete] $displayLabel")
            appendLine("Run ID: ${record.runId}")
            appendLine("Session: ${record.childSessionKey}")
            appendLine("Status: $statusLabel")
            appendLine("Duration: ${record.runtimeMs}ms")
            appendLine()
            appendLine("Task: ${record.task}")
            appendLine()

            val result = selectSubagentOutputText(record, null, outcome)
            if (!result.isNullOrBlank()) {
                appendLine("<<<BEGIN_UNTRUSTED_SUBAGENT_RESULT>>>")
                appendLine(result)
                appendLine("<<<END_UNTRUSTED_SUBAGENT_RESULT>>>")
            } else {
                appendLine("(no output)")
            }

            // Append child completion findings if present
            if (!findings.isNullOrBlank()) {
                appendLine()
                appendLine(findings)
            }

            // Reply instruction (aligned with OpenClaw buildAnnounceReplyInstruction)
            appendLine()
            appendLine("---")
            appendLine(buildAnnounceReplyInstruction(requesterIsSubagent, record.expectsCompletionMessage))
        }.trimEnd()
    }

    /**
     * Build child completion findings from descendant runs.
     * Sorted ascending by createdAt, then ascending by endedAt.
     * Aligned with OpenClaw buildChildCompletionFindings sort order.
     */
    fun buildChildCompletionFindings(children: List<SubagentRunRecord>): String? {
        if (children.isEmpty()) return null

        // Sort ascending by createdAt, then by endedAt (unfinished → MAX_VALUE)
        // Aligned with OpenClaw: a.createdAt - b.createdAt, then a.endedAt - b.endedAt
        val sorted = children.sortedWith(compareBy<SubagentRunRecord> { it.createdAt }
            .thenBy { it.endedAt ?: Long.MAX_VALUE })

        val sections = mutableListOf<String>()
        for ((i, child) in sorted.withIndex()) {
            val title = child.label.trim().ifEmpty {
                child.task.trim().ifEmpty {
                    child.childSessionKey.trim().ifEmpty {
                        "child ${i + 1}"
                    }
                }
            }
            val outcome = describeOutcome(child.outcome)
            val resultText = child.frozenResultText?.trim()

            val section = buildString {
                appendLine("${i + 1}. $title")
                appendLine("status: $outcome")
                if (!resultText.isNullOrEmpty() && !isSilentReplyText(resultText)) {
                    appendLine("<<<BEGIN_UNTRUSTED_CHILD_RESULT>>>")
                    appendLine(resultText.take(2000))
                    appendLine("<<<END_UNTRUSTED_CHILD_RESULT>>>")
                }
            }.trimEnd()
            sections.add(section)
        }

        if (sections.isEmpty()) return null

        return buildString {
            appendLine("Child completion results:")
            appendLine()
            append(sections.joinToString("\n\n"))
        }.trimEnd()
    }
}
