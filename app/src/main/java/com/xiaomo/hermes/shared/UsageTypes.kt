package com.xiaomo.hermes.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/usage-types.ts + usage-aggregates.ts +
 *         session-usage-timeseries-types.ts
 *
 * Token usage tracking and aggregation types.
 */

// --- usage-aggregates.ts ---

data class ModelUsageEntry(
    val modelId: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val requestCount: Int = 0
)

data class ToolUsageEntry(
    val toolName: String,
    val callCount: Int = 0,
    val totalDurationMs: Long = 0
)

data class CostUsageSummary(
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCacheReadTokens: Long = 0,
    val totalCacheWriteTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCostUsd: Double = 0.0
)

/** Aggregate usage across multiple model entries. */
fun aggregateModelUsage(entries: List<ModelUsageEntry>): CostUsageSummary {
    var totalInput = 0L
    var totalOutput = 0L
    var totalCacheRead = 0L
    var totalCacheWrite = 0L
    for (entry in entries) {
        totalInput += entry.inputTokens
        totalOutput += entry.outputTokens
        totalCacheRead += entry.cacheReadTokens
        totalCacheWrite += entry.cacheWriteTokens
    }
    return CostUsageSummary(
        totalInputTokens = totalInput,
        totalOutputTokens = totalOutput,
        totalCacheReadTokens = totalCacheRead,
        totalCacheWriteTokens = totalCacheWrite,
        totalTokens = totalInput + totalOutput + totalCacheRead + totalCacheWrite
    )
}

// --- session-usage-timeseries-types.ts ---

data class SessionUsageTimePoint(
    val timestamp: Long,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val totalTokens: Long,
    val cost: Double,
    val cumulativeTokens: Long,
    val cumulativeCost: Double
)

data class SessionUsageTimeSeries(
    val sessionId: String? = null,
    val points: List<SessionUsageTimePoint> = emptyList()
)

// --- Latency tracking ---

data class SessionLatencyStats(
    val count: Int = 0,
    val totalMs: Long = 0,
    val minMs: Long = Long.MAX_VALUE,
    val maxMs: Long = 0,
    val avgMs: Double = 0.0
)

data class SessionMessageCounts(
    val userMessages: Int = 0,
    val assistantMessages: Int = 0,
    val toolUseMessages: Int = 0,
    val totalMessages: Int = 0
)
