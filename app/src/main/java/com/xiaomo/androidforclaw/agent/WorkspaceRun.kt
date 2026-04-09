package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/workspace-run.ts
 *
 * Workspace run context — tracks state for a single agent execution run.
 */

import java.util.UUID

/**
 * Represents a single agent execution run within a workspace.
 * Each run gets a unique ID and tracks its own state.
 */
data class WorkspaceRun(
    val runId: String = UUID.randomUUID().toString(),
    val agentId: String = AgentPaths.DEFAULT_AGENT_ID,
    val sessionKey: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    var completedAt: Long? = null,
    var iterationCount: Int = 0,
    var toolCallCount: Int = 0
) {
    /** Mark run as completed */
    fun complete() {
        completedAt = System.currentTimeMillis()
    }

    /** Duration in milliseconds (or null if still running) */
    val durationMs: Long?
        get() = completedAt?.let { it - startedAt }

    /** Whether the run is still active */
    val isActive: Boolean
        get() = completedAt == null
}
