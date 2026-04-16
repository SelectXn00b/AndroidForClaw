/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-lifecycle-events.ts (ended reason, ended outcome, target kind)
 *
 * Hermes adaptation: lifecycle event enums and resolution functions.
 */
package com.xiaomo.hermes.agent.subagent

// ==================== Lifecycle Enums ====================

/** Aligned with OpenClaw SubagentLifecycleEndedReason */
enum class SubagentLifecycleEndedReason {
    SUBAGENT_COMPLETE,
    SUBAGENT_ERROR,
    SUBAGENT_KILLED,
    SESSION_RESET,
    SESSION_DELETE;

    val wireValue: String get() = name.lowercase().replace('_', '-')
}

/**
 * Aligned with OpenClaw lifecycle ended outcome values.
 * Separate from SubagentRunStatus -- these are lifecycle event outcomes.
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

// ==================== Resolution Functions ====================

/**
 * Aligned with OpenClaw resolveLifecycleOutcomeFromRunOutcome.
 * Maps SubagentRunOutcome to SubagentLifecycleEndedOutcome.
 */
fun resolveLifecycleOutcome(outcome: SubagentRunOutcome?): SubagentLifecycleEndedOutcome {
    // Aligned with OpenClaw resolveLifecycleOutcomeFromRunOutcome:
    // Only error/timeout are explicit; everything else (including unknown/null) -> OK.
    return when (outcome?.status) {
        SubagentRunStatus.ERROR -> SubagentLifecycleEndedOutcome.ERROR
        SubagentRunStatus.TIMEOUT -> SubagentLifecycleEndedOutcome.TIMEOUT
        else -> SubagentLifecycleEndedOutcome.OK
    }
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
