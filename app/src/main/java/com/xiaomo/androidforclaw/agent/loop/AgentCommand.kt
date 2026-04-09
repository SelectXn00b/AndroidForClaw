package com.xiaomo.androidforclaw.agent.loop

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/agent-command.ts
 *
 * Agent command types: result and progress event definitions.
 */

import com.xiaomo.androidforclaw.providers.llm.Message

/**
 * Agent execution result
 */
data class AgentResult(
    val finalContent: String,
    val toolsUsed: List<String>,
    val messages: List<Message>,
    val iterations: Int
)

/**
 * Progress update
 */
sealed class ProgressUpdate {
    /** Start new iteration */
    data class Iteration(val number: Int) : ProgressUpdate()

    /** Thinking step X (intermediate feedback) */
    data class Thinking(val iteration: Int) : ProgressUpdate()

    /** Reasoning thinking process */
    data class Reasoning(val content: String, val llmDuration: Long) : ProgressUpdate()

    /** Tool call */
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : ProgressUpdate()

    /** Tool result */
    data class ToolResult(val name: String, val result: String, val execDuration: Long) : ProgressUpdate()

    /** Iteration complete */
    data class IterationComplete(val number: Int, val iterationDuration: Long, val llmDuration: Long, val execDuration: Long) : ProgressUpdate()

    /** Context overflow */
    data class ContextOverflow(val message: String) : ProgressUpdate()

    /** Context recovered successfully */
    data class ContextRecovered(val strategy: String, val attempt: Int) : ProgressUpdate()

    /** Error */
    data class Error(val message: String) : ProgressUpdate()

    /** Loop detected */
    data class LoopDetected(
        val detector: String,
        val count: Int,
        val message: String,
        val critical: Boolean
    ) : ProgressUpdate()

    /**
     * Intermediate text reply (block reply).
     *
     * Aligned with OpenClaw's blockReplyBreak="text_end" mechanism:
     * When LLM returns text + tool_calls in the same response,
     * the text is emitted immediately as an intermediate reply
     * (not held until the final answer).
     */
    data class BlockReply(val text: String, val iteration: Int) : ProgressUpdate()

    /** A steer message was injected into the conversation mid-run */
    data class SteerMessageInjected(val content: String) : ProgressUpdate()

    /** A subagent was spawned (for observability) */
    data class SubagentSpawned(val runId: String, val label: String, val childSessionKey: String) : ProgressUpdate()

    /** A subagent completed and its result was announced to the parent */
    data class SubagentAnnounced(val runId: String, val label: String, val status: String) : ProgressUpdate()

    /** The agent loop yielded (sessions_yield) to wait for subagent results */
    data object Yielded : ProgressUpdate()

    /** Streaming: incremental reasoning/thinking token */
    data class ReasoningDelta(val text: String) : ProgressUpdate()

    /** Streaming: incremental content token */
    data class ContentDelta(val text: String) : ProgressUpdate()
}
