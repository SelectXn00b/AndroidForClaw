/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-registry-queries.ts (descendant counting, BFS traversal, controller queries)
 * - ../openclaw/src/agents/subagent-control.ts (resolveControlledSubagentTarget)
 *
 * Hermes adaptation: query/lookup extension functions on SubagentRegistry.
 * Extracted from SubagentRegistry.kt for 1:1 alignment with OpenClaw file structure.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.logging.Log

private const val TAG = "SubagentRegistryQueries"

// ==================== Basic Queries ====================

/**
 * Find run by child session key.
 * Returns active run first, fallback to any matching run (latest).
 * Aligned with OpenClaw getSubagentRunByChildSessionKey.
 */
fun SubagentRegistry.getRunByChildSessionKey(childSessionKey: String): SubagentRunRecord? {
    return runs.values.find { it.childSessionKey == childSessionKey && it.isActive }
        ?: runs.values
            .filter { it.childSessionKey == childSessionKey }
            .maxByOrNull { it.createdAt }
}

fun SubagentRegistry.getActiveRunsForParent(parentSessionKey: String): List<SubagentRunRecord> {
    return runs.values.filter { it.requesterSessionKey == parentSessionKey && it.isActive }
}

fun SubagentRegistry.getAllRuns(parentSessionKey: String): List<SubagentRunRecord> {
    return runs.values
        .filter { it.requesterSessionKey == parentSessionKey }
        .sortedByDescending { it.createdAt }
}

/**
 * Get a snapshot of all runs (keyed by runId).
 * Used for orphan recovery scanning.
 */
fun SubagentRegistry.getRunsSnapshot(): Map<String, SubagentRunRecord> {
    return runs.toMap()
}

/**
 * Build indexed list: active runs first (sorted by createdAt desc),
 * then completed runs (sorted by endedAt desc).
 * Used for numeric index resolution and list display.
 * Aligned with OpenClaw buildSubagentList ordering.
 */
fun SubagentRegistry.buildIndexedList(parentSessionKey: String): List<SubagentRunRecord> {
    val allRuns = runs.values.filter { it.requesterSessionKey == parentSessionKey }
    val active = allRuns.filter { it.isActive }.sortedByDescending { it.createdAt }
    val completed = allRuns.filter { !it.isActive }.sortedByDescending { it.endedAt }
    return active + completed
}

/**
 * List all runs spawned by a given requester session key (direct children).
 * Optional requesterRunId provides time-window scoping.
 * Aligned with OpenClaw listRunsForRequesterFromRuns.
 */
fun SubagentRegistry.listRunsForRequester(
    requesterSessionKey: String,
    requesterRunId: String? = null,
): List<SubagentRunRecord> {
    val key = requesterSessionKey.trim()
    if (key.isEmpty()) return emptyList()

    // Time-window scoping from requester run (aligned with OpenClaw)
    val requesterRun = requesterRunId?.trim()?.let { rid -> getRunById(rid) }
    val scopedRun = if (requesterRun != null && requesterRun.childSessionKey == key) requesterRun else null
    val lowerBound = scopedRun?.startedAt ?: scopedRun?.createdAt
    val upperBound = scopedRun?.endedAt

    return runs.values
        .filter { entry ->
            if (entry.requesterSessionKey != key) return@filter false
            if (lowerBound != null && entry.createdAt < lowerBound) return@filter false
            if (upperBound != null && entry.createdAt > upperBound) return@filter false
            true
        }
        .sortedByDescending { it.createdAt }
}

/**
 * List runs where controllerSessionKey matches.
 * Falls back to requesterSessionKey if controllerSessionKey is null.
 * Aligned with OpenClaw listRunsForControllerFromRuns.
 */
fun SubagentRegistry.listRunsForController(controllerKey: String): List<SubagentRunRecord> {
    return runs.values
        .filter {
            val key = it.controllerSessionKey ?: it.requesterSessionKey
            key == controllerKey
        }
        .sortedByDescending { it.createdAt }
}

/**
 * Count active runs for a session (using controllerSessionKey).
 * Active = not ended OR has pending descendants.
 * Aligned with OpenClaw countActiveRunsForSessionFromRuns.
 */
fun SubagentRegistry.countActiveRunsForSession(controllerSessionKey: String): Int {
    return runs.values.count { record ->
        val key = record.controllerSessionKey ?: record.requesterSessionKey
        key == controllerSessionKey && isActiveSubagentRun(record) { sessionKey ->
            countPendingDescendantRuns(sessionKey)
        }
    }
}

fun SubagentRegistry.activeChildCount(parentSessionKey: String): Int {
    return countActiveRunsForSession(parentSessionKey)
}

/**
 * Check if parent can spawn more children.
 * Aligned with OpenClaw active children check in spawnSubagentDirect.
 */
fun SubagentRegistry.canSpawn(parentSessionKey: String, maxChildren: Int): Boolean {
    return activeChildCount(parentSessionKey) < maxChildren
}

// ==================== Advanced Queries ====================

/**
 * Find all runIds associated with a child session key.
 * Aligned with OpenClaw findRunIdsByChildSessionKeyFromRuns.
 */
fun SubagentRegistry.findRunIdsByChildSessionKey(childSessionKey: String): List<String> {
    return runs.values
        .filter { it.childSessionKey == childSessionKey }
        .map { it.runId }
}

/**
 * Resolve requester for a child session.
 * Returns requesterSessionKey of the latest run for the child.
 * Aligned with OpenClaw resolveRequesterForChildSessionFromRuns.
 */
fun SubagentRegistry.resolveRequesterForChildSession(childSessionKey: String): String? {
    return runs.values
        .filter { it.childSessionKey == childSessionKey }
        .maxByOrNull { it.createdAt }
        ?.requesterSessionKey
}

/**
 * Check if any run for the given child session key is active.
 * Aligned with OpenClaw isSubagentSessionRunActive.
 */
fun SubagentRegistry.isSubagentSessionRunActive(childSessionKey: String): Boolean {
    return runs.values.any { it.childSessionKey == childSessionKey && it.isActive }
}

/**
 * Check if post-completion announce should be ignored for a session.
 * True if the session's run mode is RUN and cleanup has already been completed.
 * Aligned with OpenClaw shouldIgnorePostCompletionAnnounceForSessionFromRuns.
 */
fun SubagentRegistry.shouldIgnorePostCompletionAnnounceForSession(childSessionKey: String): Boolean {
    val latestRun = runs.values
        .filter { it.childSessionKey == childSessionKey }
        .maxByOrNull { it.createdAt } ?: return false
    return latestRun.spawnMode != SpawnMode.SESSION &&
        latestRun.endedAt != null &&
        latestRun.cleanupCompletedAt != null &&
        latestRun.cleanupCompletedAt!! >= latestRun.endedAt!!
}

// ==================== Target Resolution ====================

/**
 * Resolve a target token to a SubagentRunRecord.
 * Resolution order aligned with OpenClaw resolveControlledSubagentTarget:
 * 1. "last" keyword -> most recently started active run (or most recent)
 * 2. Numeric index -> 1-based index into buildIndexedList
 * 3. Contains ":" -> session key exact match
 * 4. Exact label match (case-insensitive)
 * 5. Label prefix match (case-insensitive)
 * 6. RunId prefix match
 */
fun SubagentRegistry.resolveTarget(token: String, parentSessionKey: String): SubagentRunRecord? {
    if (token.isBlank()) return null

    val parentRuns = getAllRuns(parentSessionKey)
    if (parentRuns.isEmpty()) return null

    // 1. "last" keyword
    if (token.equals("last", ignoreCase = true)) {
        return parentRuns.firstOrNull { it.isActive }
            ?: parentRuns.firstOrNull()
    }

    // 2. Numeric index (1-based)
    token.toIntOrNull()?.let { index ->
        val indexed = buildIndexedList(parentSessionKey)
        return indexed.getOrNull(index - 1)
    }

    // 3. Session key (contains ":")
    if (":" in token) {
        return parentRuns.find { it.childSessionKey == token }
    }

    // 4. Exact label match (case-insensitive)
    val exactLabel = parentRuns.filter { it.label.equals(token, ignoreCase = true) }
    if (exactLabel.size == 1) return exactLabel[0]

    // 5. Label prefix match (case-insensitive)
    val prefixLabel = parentRuns.filter { it.label.startsWith(token, ignoreCase = true) }
    if (prefixLabel.size == 1) return prefixLabel[0]

    // 6. RunId prefix match
    val prefixRunId = parentRuns.filter { it.runId.startsWith(token) }
    if (prefixRunId.size == 1) return prefixRunId[0]

    return null
}

// ==================== Descendant Tracking ====================

/**
 * Count pending (active or not-cleanup-completed) descendant runs.
 * BFS traversal through the spawn tree.
 * Aligned with OpenClaw countPendingDescendantRunsFromRuns.
 */
fun SubagentRegistry.countPendingDescendantRuns(sessionKey: String): Int {
    var count = 0
    val queue = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    queue.add(sessionKey)
    visited.add(sessionKey)

    while (queue.isNotEmpty()) {
        val currentKey = queue.removeAt(0)
        val children = runs.values.filter { it.requesterSessionKey == currentKey }
        for (child in children) {
            if (child.isActive || child.cleanupCompletedAt == null) count++
            if (child.childSessionKey !in visited) {
                visited.add(child.childSessionKey)
                queue.add(child.childSessionKey)
            }
        }
    }
    return count
}

/**
 * Same as countPendingDescendantRuns but excluding a specific runId.
 * Used during announce to exclude the run being announced.
 * Aligned with OpenClaw countPendingDescendantRunsExcludingRunFromRuns.
 */
fun SubagentRegistry.countPendingDescendantRunsExcludingRun(sessionKey: String, excludeRunId: String): Int {
    var count = 0
    val queue = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    queue.add(sessionKey)
    visited.add(sessionKey)

    while (queue.isNotEmpty()) {
        val currentKey = queue.removeAt(0)
        val children = runs.values.filter { it.requesterSessionKey == currentKey }
        for (child in children) {
            if (child.runId != excludeRunId && (child.isActive || child.cleanupCompletedAt == null)) {
                count++
            }
            if (child.childSessionKey !in visited) {
                visited.add(child.childSessionKey)
                queue.add(child.childSessionKey)
            }
        }
    }
    return count
}

/**
 * Count active (not ended) descendant runs.
 * Aligned with OpenClaw countActiveDescendantRunsFromRuns.
 */
fun SubagentRegistry.countActiveDescendantRuns(sessionKey: String): Int {
    var count = 0
    val queue = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    queue.add(sessionKey)
    visited.add(sessionKey)

    while (queue.isNotEmpty()) {
        val currentKey = queue.removeAt(0)
        val children = runs.values.filter { it.requesterSessionKey == currentKey }
        for (child in children) {
            if (child.isActive) count++
            if (child.childSessionKey !in visited) {
                visited.add(child.childSessionKey)
                queue.add(child.childSessionKey)
            }
        }
    }
    return count
}

/**
 * List all descendant runs recursively.
 * Aligned with OpenClaw listDescendantRunsForRequesterFromRuns.
 */
fun SubagentRegistry.listDescendantRuns(sessionKey: String): List<SubagentRunRecord> {
    val result = mutableListOf<SubagentRunRecord>()
    val queue = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    queue.add(sessionKey)
    visited.add(sessionKey)

    while (queue.isNotEmpty()) {
        val currentKey = queue.removeAt(0)
        val children = runs.values.filter { it.requesterSessionKey == currentKey }
        for (child in children) {
            result.add(child)
            if (child.childSessionKey !in visited) {
                visited.add(child.childSessionKey)
                queue.add(child.childSessionKey)
            }
        }
    }
    return result
}
