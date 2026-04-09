/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-capabilities.ts (role, control scope, depth resolution)
 *
 * AndroidForClaw adaptation: subagent role and capability resolution based on spawn depth.
 */
package com.xiaomo.androidforclaw.agent.subagent

// ==================== Constants ====================

/** Aligned with OpenClaw DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH = 1 */
const val DEFAULT_MAX_SPAWN_DEPTH = 1

// ==================== Role System ====================

/**
 * Aligned with OpenClaw SubagentSessionRole.
 * Determined by spawn depth relative to maxSpawnDepth.
 */
enum class SubagentSessionRole {
    /** Depth 0: top-level agent. Can spawn children, can control children. */
    MAIN,
    /** 0 < depth < maxSpawnDepth: intermediate. Can spawn and control children. */
    ORCHESTRATOR,
    /** depth >= maxSpawnDepth: leaf worker. Cannot spawn further subagents. */
    LEAF;

    val wireValue: String get() = name.lowercase()
}

/**
 * Aligned with OpenClaw SubagentControlScope.
 * MAIN/ORCHESTRATOR get CHILDREN, LEAF gets NONE.
 */
enum class SubagentControlScope {
    CHILDREN,
    NONE;

    val wireValue: String get() = name.lowercase()
}

/** Resolved capabilities for a given depth. Aligned with OpenClaw resolveSubagentCapabilities return. */
data class SubagentCapabilities(
    val depth: Int,
    val role: SubagentSessionRole,
    val controlScope: SubagentControlScope,
    val canSpawn: Boolean,
    val canControlChildren: Boolean,
)

// ==================== Capability Resolution ====================

/**
 * Aligned with OpenClaw resolveSubagentRoleForDepth.
 * depth <= 0 -> MAIN, depth < maxSpawnDepth -> ORCHESTRATOR, else -> LEAF.
 */
fun resolveSubagentRole(depth: Int, maxSpawnDepth: Int = DEFAULT_MAX_SPAWN_DEPTH): SubagentSessionRole {
    return when {
        depth <= 0 -> SubagentSessionRole.MAIN
        depth < maxSpawnDepth -> SubagentSessionRole.ORCHESTRATOR
        else -> SubagentSessionRole.LEAF
    }
}

/** Aligned with OpenClaw resolveSubagentControlScopeForRole */
fun resolveSubagentControlScope(role: SubagentSessionRole): SubagentControlScope {
    return if (role == SubagentSessionRole.LEAF) SubagentControlScope.NONE else SubagentControlScope.CHILDREN
}

/** Aligned with OpenClaw resolveSubagentCapabilities */
fun resolveSubagentCapabilities(depth: Int, maxSpawnDepth: Int = DEFAULT_MAX_SPAWN_DEPTH): SubagentCapabilities {
    val role = resolveSubagentRole(depth, maxSpawnDepth)
    val controlScope = resolveSubagentControlScope(role)
    return SubagentCapabilities(
        depth = depth,
        role = role,
        controlScope = controlScope,
        canSpawn = role == SubagentSessionRole.MAIN || role == SubagentSessionRole.ORCHESTRATOR,
        canControlChildren = controlScope == SubagentControlScope.CHILDREN,
    )
}
