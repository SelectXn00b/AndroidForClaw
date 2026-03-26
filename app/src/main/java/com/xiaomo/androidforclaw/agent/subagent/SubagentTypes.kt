/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-registry.types.ts (SubagentRunRecord)
 * - ../openclaw/src/agents/subagent-capabilities.ts (role, control scope, depth)
 * - ../openclaw/src/agents/subagent-lifecycle-events.ts (ended reason, ended outcome)
 * - ../openclaw/src/agents/subagent-spawn.ts (SpawnSubagentParams, SpawnSubagentResult, SpawnSubagentMode)
 * - ../openclaw/src/agents/subagent-announce.ts (SubagentRunOutcome)
 * - ../openclaw/src/agents/subagent-registry.ts (announce constants)
 * - ../openclaw/src/agents/subagent-control.ts (steer/control constants)
 * - ../openclaw/src/agents/subagent-attachments.ts (attachment types)
 */
package com.xiaomo.androidforclaw.agent.subagent

// ==================== Constants (aligned with OpenClaw) ====================

/** Aligned with OpenClaw DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH = 1 */
const val DEFAULT_MAX_SPAWN_DEPTH = 1

/** Aligned with OpenClaw maxChildrenPerAgent default */
const val DEFAULT_MAX_CHILDREN_PER_AGENT = 5

/** Aligned with OpenClaw STEER_RATE_LIMIT_MS */
const val STEER_RATE_LIMIT_MS = 2000L

/** Aligned with OpenClaw STEER_ABORT_SETTLE_TIMEOUT_MS */
const val STEER_ABORT_SETTLE_TIMEOUT_MS = 5_000L

/** Aligned with OpenClaw MAX_STEER_MESSAGE_CHARS */
const val MAX_STEER_MESSAGE_CHARS = 4_000

/** Aligned with OpenClaw SUBAGENT_ANNOUNCE_TIMEOUT_MS */
const val SUBAGENT_ANNOUNCE_TIMEOUT_MS = 120_000L

/** Aligned with OpenClaw DEFAULT_SUBAGENT_ANNOUNCE_TIMEOUT_MS */
const val DEFAULT_SUBAGENT_ANNOUNCE_TIMEOUT_MS = 90_000L

/** Aligned with OpenClaw MIN_ANNOUNCE_RETRY_DELAY_MS */
const val MIN_ANNOUNCE_RETRY_DELAY_MS = 1_000L

/** Aligned with OpenClaw MAX_ANNOUNCE_RETRY_DELAY_MS */
const val MAX_ANNOUNCE_RETRY_DELAY_MS = 8_000L

/** Aligned with OpenClaw MAX_ANNOUNCE_RETRY_COUNT */
const val MAX_ANNOUNCE_RETRY_COUNT = 3

/** Aligned with OpenClaw ANNOUNCE_EXPIRY_MS (5 min for non-completion flows) */
const val ANNOUNCE_EXPIRY_MS = 5 * 60_000L

/** Aligned with OpenClaw ANNOUNCE_COMPLETION_HARD_EXPIRY_MS (30 min for completion flows) */
const val ANNOUNCE_COMPLETION_HARD_EXPIRY_MS = 30 * 60_000L

/** Aligned with OpenClaw LIFECYCLE_ERROR_RETRY_GRACE_MS */
const val LIFECYCLE_ERROR_RETRY_GRACE_MS = 15_000L

/** Aligned with OpenClaw FROZEN_RESULT_TEXT_MAX_BYTES (100 KB) */
const val FROZEN_RESULT_TEXT_MAX_BYTES = 100 * 1024

/** Aligned with OpenClaw DEFAULT_RECENT_MINUTES */
const val DEFAULT_RECENT_MINUTES = 30

/** Archive completed runs after 1 hour */
const val ARCHIVE_AFTER_MS = 60 * 60 * 1000L

// ==================== Spawn Mode ====================

/** Aligned with OpenClaw SpawnSubagentMode ("run" | "session") */
enum class SpawnMode {
    /** One-shot: run task, announce result, clean up */
    RUN,
    /** Persistent: thread-bound session stays active after task */
    SESSION;

    val wireValue: String get() = name.lowercase()
}

// ==================== Spawn Status ====================

/** Aligned with OpenClaw SpawnSubagentResult.status */
enum class SpawnStatus {
    ACCEPTED,
    FORBIDDEN,
    ERROR;

    val wireValue: String get() = name.lowercase()
}

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

// ==================== Lifecycle ====================

/** Aligned with OpenClaw SubagentLifecycleEndedReason */
enum class SubagentLifecycleEndedReason {
    SUBAGENT_COMPLETE,
    SUBAGENT_ERROR,
    SUBAGENT_KILLED,
    SESSION_RESET,
    SESSION_DELETE;

    val wireValue: String get() = name.lowercase().replace('_', '-')
}

/** Aligned with OpenClaw SubagentRunOutcome.status */
enum class SubagentRunStatus {
    OK,
    ERROR,
    TIMEOUT,
    UNKNOWN;

    val wireValue: String get() = name.lowercase()
}

/**
 * Aligned with OpenClaw lifecycle ended outcome values.
 * Separate from SubagentRunStatus — these are lifecycle event outcomes.
 */
enum class SubagentLifecycleEndedOutcome {
    OK,
    ERROR,
    TIMEOUT,
    KILLED,
    RESET,
    DELETED;

    val wireValue: String get() = name.lowercase()
}

/** Aligned with OpenClaw SUBAGENT_TARGET_KIND */
enum class SubagentLifecycleTargetKind {
    SUBAGENT,
    ACP;

    val wireValue: String get() = name.lowercase()
}

/** Aligned with OpenClaw SpawnSubagentSandboxMode ("inherit" | "require") */
enum class SpawnSubagentSandboxMode {
    INHERIT,
    REQUIRE;

    val wireValue: String get() = name.lowercase()
}

// ==================== Data Classes ====================

/** Aligned with OpenClaw SubagentRunOutcome */
data class SubagentRunOutcome(
    val status: SubagentRunStatus,
    val error: String? = null,
)

/**
 * Aligned with OpenClaw SubagentRunRecord (subagent-registry.types.ts).
 * Tracks a single subagent run from spawn to completion/cleanup.
 */
data class SubagentRunRecord(
    val runId: String,
    val childSessionKey: String,
    /** Who controls this subagent (can differ from requester in cross-agent scenarios) */
    val controllerSessionKey: String? = null,
    val requesterSessionKey: String,
    /** Aligned with OpenClaw requesterOrigin — simplified for Android */
    val requesterDisplayKey: String = "",
    val task: String,
    val label: String,
    val model: String?,
    /** Aligned with OpenClaw cleanup: "delete" | "keep" */
    val cleanup: String = "delete",
    val spawnMode: SpawnMode,
    val workspaceDir: String? = null,
    val runTimeoutSeconds: Int? = null,
    val createdAt: Long,
    var startedAt: Long? = null,
    /** Stable across follow-up runs (preserved on steer restart) */
    var sessionStartedAt: Long? = null,
    /** Runtime carried from previous steer-restarted runs */
    var accumulatedRuntimeMs: Long = 0L,
    var endedAt: Long? = null,
    var outcome: SubagentRunOutcome? = null,
    /** When to auto-archive this run record */
    var archiveAtMs: Long? = null,
    var cleanupCompletedAt: Long? = null,
    var cleanupHandled: Boolean = false,
    var suppressAnnounceReason: String? = null,
    /** Whether to send a completion message to the requester */
    var expectsCompletionMessage: Boolean = true,
    var announceRetryCount: Int = 0,
    var lastAnnounceRetryAt: Long? = null,
    var endedReason: SubagentLifecycleEndedReason? = null,
    var wakeOnDescendantSettle: Boolean = false,
    var frozenResultText: String? = null,
    var frozenResultCapturedAt: Long? = null,
    var fallbackFrozenResultText: String? = null,
    var fallbackFrozenResultCapturedAt: Long? = null,
    var endedHookEmittedAt: Long? = null,
    /** Filesystem path where materialized attachments reside */
    var attachmentsDir: String? = null,
    var attachmentsRootDir: String? = null,
    var retainAttachmentsOnKeep: Boolean = false,
    /** Spawn depth — stored for steer restart prompt rebuilding */
    val depth: Int = 0,
) {
    val isActive: Boolean get() = endedAt == null
    val runtimeMs: Long get() {
        val start = startedAt ?: return accumulatedRuntimeMs
        val end = endedAt ?: System.currentTimeMillis()
        return (end - start) + accumulatedRuntimeMs
    }
}

/** Aligned with OpenClaw SubagentInlineAttachment */
data class InlineAttachment(
    val name: String,
    val content: String,
    val encoding: String = "utf8",
    val mimeType: String? = null,
)

/** Aligned with OpenClaw SubagentAttachmentReceiptFile */
data class AttachmentReceiptFile(
    val name: String,
    val bytes: Int,
    val sha256: String,
)

/** Aligned with OpenClaw SubagentAttachmentReceipt */
data class AttachmentReceipt(
    val count: Int,
    val totalBytes: Int,
    val files: List<AttachmentReceiptFile>,
    val relDir: String,
)

/** Aligned with OpenClaw SpawnSubagentParams */
data class SpawnSubagentParams(
    val task: String,
    val label: String? = null,
    val agentId: String? = null,
    val model: String? = null,
    val thinking: String? = null,
    val runTimeoutSeconds: Int? = null,
    val mode: SpawnMode = SpawnMode.RUN,
    /** "delete" | "keep" — aligned with OpenClaw default "keep" */
    val cleanup: String = "keep",
    val expectsCompletionMessage: Boolean? = null,
    val thread: Boolean? = null,
    val sandbox: String? = null,
    val attachments: List<InlineAttachment>? = null,
    val attachMountPath: String? = null,
    val cwd: String? = null,
    /** "subagent" | "acp" — ACP not supported on Android, defaults to "subagent" */
    val runtime: String? = null,
)

/** Aligned with OpenClaw SpawnSubagentResult */
data class SpawnSubagentResult(
    val status: SpawnStatus,
    val childSessionKey: String? = null,
    val runId: String? = null,
    val mode: SpawnMode? = null,
    val note: String? = null,
    val error: String? = null,
    val modelApplied: Boolean? = null,
    val attachments: AttachmentReceipt? = null,
)

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
 * depth <= 0 → MAIN, depth < maxSpawnDepth → ORCHESTRATOR, else → LEAF.
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

/**
 * Aligned with OpenClaw resolveLifecycleOutcomeFromRunOutcome.
 * Maps SubagentRunOutcome to SubagentLifecycleEndedOutcome.
 */
fun resolveLifecycleOutcome(outcome: SubagentRunOutcome?): SubagentLifecycleEndedOutcome {
    return when (outcome?.status) {
        SubagentRunStatus.OK -> SubagentLifecycleEndedOutcome.OK
        SubagentRunStatus.ERROR -> SubagentLifecycleEndedOutcome.ERROR
        SubagentRunStatus.TIMEOUT -> SubagentLifecycleEndedOutcome.TIMEOUT
        SubagentRunStatus.UNKNOWN, null -> SubagentLifecycleEndedOutcome.ERROR
    }
}

/**
 * Cap frozen result text to FROZEN_RESULT_TEXT_MAX_BYTES (100KB).
 * Aligned with OpenClaw freezeRunResultAtCompletion truncation.
 */
fun capFrozenResultText(text: String?): String? {
    if (text == null) return null
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= FROZEN_RESULT_TEXT_MAX_BYTES) return text
    val notice = "\n\n[truncated: frozen output exceeded ${FROZEN_RESULT_TEXT_MAX_BYTES / 1024}KB]"
    val noticeBytes = notice.toByteArray(Charsets.UTF_8).size
    val maxContent = FROZEN_RESULT_TEXT_MAX_BYTES - noticeBytes
    if (maxContent <= 0) return notice
    val truncated = String(bytes, 0, maxContent, Charsets.UTF_8)
    return truncated + notice
}

/**
 * Compute announce retry delay with exponential backoff.
 * Aligned with OpenClaw transient retry pattern.
 */
fun computeAnnounceRetryDelayMs(retryCount: Int): Long {
    val baseDelay = MIN_ANNOUNCE_RETRY_DELAY_MS * (1L shl retryCount)
    return minOf(baseDelay, MAX_ANNOUNCE_RETRY_DELAY_MS)
}

/** Note returned to LLM after successful spawn (aligned with OpenClaw SUBAGENT_SPAWN_ACCEPTED_NOTE) */
const val SPAWN_ACCEPTED_NOTE = "Auto-announce is push-based. After spawning children, do NOT call sessions_list, sessions_history, exec sleep, or any polling tool. Wait for completion events to arrive as user messages, track expected child session keys, and only send your final answer after ALL expected completions arrive. If a child completion event arrives AFTER your final answer, reply ONLY with NO_REPLY."

/** Note for session-mode spawn (aligned with OpenClaw SUBAGENT_SPAWN_SESSION_ACCEPTED_NOTE) */
const val SPAWN_SESSION_ACCEPTED_NOTE = "thread-bound session stays active after this task; continue in-thread for follow-ups."

/** Maximum recentMinutes for subagent list queries (24 hours). Aligned with OpenClaw MAX_RECENT_MINUTES. */
const val MAX_RECENT_MINUTES = 24 * 60

// ==================== Missing Utility Functions ====================

/**
 * Resolve human-readable session status from a run record.
 * Aligned with OpenClaw resolveSubagentSessionStatus.
 */
fun resolveSubagentSessionStatus(record: SubagentRunRecord?): String {
    if (record == null) return "unknown"
    if (record.endedAt == null) return "running"
    return when (record.endedReason) {
        SubagentLifecycleEndedReason.SUBAGENT_KILLED -> "killed"
        else -> when (record.outcome?.status) {
            SubagentRunStatus.ERROR -> "failed"
            SubagentRunStatus.TIMEOUT -> "timeout"
            else -> "done"
        }
    }
}

/**
 * Compare two SubagentRunOutcome objects for equality.
 * Aligned with OpenClaw runOutcomesEqual.
 */
fun runOutcomesEqual(a: SubagentRunOutcome?, b: SubagentRunOutcome?): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    if (a.status != b.status) return false
    if (a.status == SubagentRunStatus.ERROR && a.error != b.error) return false
    return true
}

/**
 * Resolve the cleanup completion reason from a run record.
 * Aligned with OpenClaw resolveCleanupCompletionReason.
 */
fun resolveCleanupCompletionReason(record: SubagentRunRecord): SubagentLifecycleEndedReason {
    return record.endedReason ?: SubagentLifecycleEndedReason.SUBAGENT_COMPLETE
}

/**
 * Resolve session-ended reason to outcome.
 * Aligned with OpenClaw resolveSubagentSessionEndedOutcome.
 */
fun resolveSubagentSessionEndedOutcome(reason: SubagentLifecycleEndedReason): SubagentLifecycleEndedOutcome {
    return when (reason) {
        SubagentLifecycleEndedReason.SESSION_RESET -> SubagentLifecycleEndedOutcome.RESET
        SubagentLifecycleEndedReason.SESSION_DELETE -> SubagentLifecycleEndedOutcome.DELETED
        else -> SubagentLifecycleEndedOutcome.DELETED
    }
}

/**
 * Check if a subagent run is considered "active" (not ended OR has pending descendants).
 * Aligned with OpenClaw isActiveSubagentRun.
 */
fun isActiveSubagentRun(
    entry: SubagentRunRecord,
    pendingDescendantCount: (sessionKey: String) -> Int,
): Boolean {
    return entry.endedAt == null || pendingDescendantCount(entry.childSessionKey) > 0
}

/**
 * Get session started-at time with fallback chain.
 * Aligned with OpenClaw getSubagentSessionStartedAt.
 */
fun getSubagentSessionStartedAt(entry: SubagentRunRecord): Long? {
    return entry.sessionStartedAt ?: entry.startedAt ?: entry.createdAt
}

// ==================== Deferred Cleanup Decision ====================

/**
 * Deferred cleanup decision.
 * Aligned with OpenClaw DeferredCleanupDecision.
 */
sealed class DeferredCleanupDecision {
    /** Defer: active descendants still running */
    data class DeferDescendants(val delayMs: Long) : DeferredCleanupDecision()
    /** Give up: retry limit or expiry reached */
    data class GiveUp(val reason: String, val retryCount: Int? = null) : DeferredCleanupDecision()
    /** Retry: exponential backoff */
    data class Retry(val retryCount: Int, val resumeDelayMs: Long? = null) : DeferredCleanupDecision()
}

/** Aligned with OpenClaw DEFER_DESCENDANT_DELAY_MS default */
const val DEFER_DESCENDANT_DELAY_MS = 10_000L

/**
 * Resolve deferred cleanup decision.
 * Aligned with OpenClaw resolveDeferredCleanupDecision.
 */
fun resolveDeferredCleanupDecision(
    entry: SubagentRunRecord,
    now: Long,
    activeDescendantRuns: Int,
    announceExpiryMs: Long = ANNOUNCE_EXPIRY_MS,
    announceCompletionHardExpiryMs: Long = ANNOUNCE_COMPLETION_HARD_EXPIRY_MS,
    maxAnnounceRetryCount: Int = MAX_ANNOUNCE_RETRY_COUNT,
    deferDescendantDelayMs: Long = DEFER_DESCENDANT_DELAY_MS,
): DeferredCleanupDecision {
    val endedAgo = if (entry.endedAt != null) now - entry.endedAt!! else 0L
    val isCompletionMessageFlow = entry.expectsCompletionMessage
    val completionHardExpiryExceeded = isCompletionMessageFlow && endedAgo > announceCompletionHardExpiryMs

    // If completion flow with active descendants: defer or give-up
    if (isCompletionMessageFlow && activeDescendantRuns > 0) {
        return if (completionHardExpiryExceeded) {
            DeferredCleanupDecision.GiveUp(reason = "expiry")
        } else {
            DeferredCleanupDecision.DeferDescendants(delayMs = deferDescendantDelayMs)
        }
    }

    val retryCount = entry.announceRetryCount + 1
    val expiryExceeded = if (isCompletionMessageFlow) {
        completionHardExpiryExceeded
    } else {
        endedAgo > announceExpiryMs
    }

    if (retryCount >= maxAnnounceRetryCount || expiryExceeded) {
        return DeferredCleanupDecision.GiveUp(
            reason = if (retryCount >= maxAnnounceRetryCount) "retry-limit" else "expiry",
            retryCount = retryCount,
        )
    }

    return DeferredCleanupDecision.Retry(
        retryCount = retryCount,
        resumeDelayMs = if (isCompletionMessageFlow) computeAnnounceRetryDelayMs(retryCount) else null,
    )
}
