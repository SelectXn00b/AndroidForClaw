package com.xiaomo.androidforclaw.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/agent-paths.ts
 *
 * Agent directory path resolution.
 */

import com.xiaomo.androidforclaw.workspace.StoragePaths
import java.io.File

object AgentPaths {
    /** Root directory for all agents: <root>/agents/ */
    val agentsRoot: File
        get() = StoragePaths.agents

    /** Directory for a specific agent: <appDir>/agents/<agentId>/ */
    fun agentDir(agentId: String): File = File(agentsRoot, agentId)

    /** Sessions directory for an agent: <appDir>/agents/<agentId>/sessions/ */
    fun sessionsDir(agentId: String): File = File(agentDir(agentId), "sessions")

    /** Workspace directory for an agent: <appDir>/agents/<agentId>/workspace/ */
    fun workspaceDir(agentId: String): File = File(agentDir(agentId), "workspace")

    /** Memory directory for an agent: <appDir>/agents/<agentId>/memory/ */
    fun memoryDir(agentId: String): File = File(agentDir(agentId), "memory")

    /** Logs directory for an agent: <appDir>/agents/<agentId>/logs/ */
    fun logsDir(agentId: String): File = File(agentDir(agentId), "logs")

    /** Default agent ID */
    const val DEFAULT_AGENT_ID = "main"

    /** Convenience: default agent directories */
    val defaultAgentDir: File get() = agentDir(DEFAULT_AGENT_ID)
    val defaultSessionsDir: File get() = sessionsDir(DEFAULT_AGENT_ID)
    val defaultWorkspaceDir: File get() = workspaceDir(DEFAULT_AGENT_ID)
}
