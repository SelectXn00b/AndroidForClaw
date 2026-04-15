package com.xiaomo.androidforclaw.hermes.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Insights - 使用洞察/统计
 * 1:1 对齐 hermes/agent/insights.py
 *
 * 跟踪 API 调用、token 使用、费用等统计数据。
 */

data class UsageEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "",
    val model: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cost: Double = 0.0,
    val durationMs: Long = 0L,
    val success: Boolean = true,
    val errorType: String? = null,
    val platform: String = "",
    val sessionId: String = "",
    val toolName: String = "",
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val costUsd: Double = cost,
    val durationSec: Double = durationMs / 1000.0,
    val date: String = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
)

data class ProviderStats(
    var totalCalls: Int = 0,
    var successfulCalls: Int = 0,
    var failedCalls: Int = 0,
    var totalInputTokens: Int = 0,
    var totalOutputTokens: Int = 0,
    var totalCost: Double = 0.0,
    var totalDurationMs: Long = 0L,
    val errors: MutableMap<String, Int> = mutableMapOf()
) {
    val successRate: Double get() = if (totalCalls > 0) successfulCalls.toDouble() / totalCalls else 0.0
    val averageDurationMs: Double get() = if (totalCalls > 0) totalDurationMs.toDouble() / totalCalls else 0.0
}

class Insights(
    private val dataDir: String = "."
) {

    private val gson = Gson()
    private val entries: MutableList<UsageEntry> = mutableListOf()
    private val providerStats: ConcurrentHashMap<String, ProviderStats> = ConcurrentHashMap()

    /**
     * 记录一次 API 调用
     */
    fun record(entry: UsageEntry) {
        synchronized(entries) {
            entries.add(entry)
        }

        val stats = providerStats.getOrPut(entry.provider) { ProviderStats() }
        synchronized(stats) {
            stats.totalCalls++
            if (entry.success) stats.successfulCalls++ else stats.failedCalls++
            stats.totalInputTokens += entry.inputTokens
            stats.totalOutputTokens += entry.outputTokens
            stats.totalCost += entry.cost
            stats.totalDurationMs += entry.durationMs
            if (entry.errorType != null) {
                stats.errors[entry.errorType] = (stats.errors[entry.errorType] ?: 0) + 1
            }
        }
    }

    /**
     * 获取总统计
     */
    fun getTotalStats(): ProviderStats {
        val total = ProviderStats()
        for (stats in providerStats.values) {
            total.totalCalls += stats.totalCalls
            total.successfulCalls += stats.successfulCalls
            total.failedCalls += stats.failedCalls
            total.totalInputTokens += stats.totalInputTokens
            total.totalOutputTokens += stats.totalOutputTokens
            total.totalCost += stats.totalCost
            total.totalDurationMs += stats.totalDurationMs
        }
        return total
    }

    /**
     * 获取指定 provider 的统计
     */
    fun getProviderStats(provider: String): ProviderStats? {
        return providerStats[provider]
    }

    /**
     * 获取所有 provider 的统计
     */
    fun getAllProviderStats(): Map<String, ProviderStats> {
        return providerStats.toMap()
    }

    /**
     * 获取指定时间范围内的使用记录
     */
    fun getEntriesSince(sinceMs: Long): List<UsageEntry> {
        synchronized(entries) {
            return entries.filter { it.timestamp >= sinceMs }
        }
    }

    /**
     * 获取今天的使用统计
     */
    fun getTodayStats(): ProviderStats {
        val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000)
        val todayEntries = getEntriesSince(todayStart)
        val stats = ProviderStats()
        for (entry in todayEntries) {
            stats.totalCalls++
            if (entry.success) stats.successfulCalls++ else stats.failedCalls++
            stats.totalInputTokens += entry.inputTokens
            stats.totalOutputTokens += entry.outputTokens
            stats.totalCost += entry.cost
            stats.totalDurationMs += entry.durationMs
        }
        return stats
    }

    /**
     * 生成统计报告
     */
    fun generateReport(): String {
        val sb = StringBuilder()
        val total = getTotalStats()

        sb.appendLine("=== Usage Insights ===")
        sb.appendLine("Total calls: ${total.totalCalls}")
        sb.appendLine("Success rate: ${String.format("%.1f", total.successRate * 100)}%")
        sb.appendLine("Total tokens: ${total.totalInputTokens} in / ${total.totalOutputTokens} out")
        sb.appendLine("Total cost: $${String.format("%.4f", total.totalCost)}")
        sb.appendLine("Avg duration: ${String.format("%.0f", total.averageDurationMs)}ms")

        sb.appendLine("\n--- By Provider ---")
        for ((provider, stats) in providerStats) {
            sb.appendLine("$provider: ${stats.totalCalls} calls, $${String.format("%.4f", stats.totalCost)}, ${String.format("%.1f", stats.successRate * 100)}% success")
        }

        return sb.toString().trim()
    }

    /**
     * 保存到 JSON 文件
     */
    fun save(filename: String = "insights.json") {
        val file = File(dataDir, filename)
        file.parentFile?.mkdirs()
        val data = mapOf(
            "entries" to entries,
            "providerStats" to providerStats.mapValues { (_, stats) ->
                mapOf(
                    "totalCalls" to stats.totalCalls,
                    "successfulCalls" to stats.successfulCalls,
                    "failedCalls" to stats.failedCalls,
                    "totalInputTokens" to stats.totalInputTokens,
                    "totalOutputTokens" to stats.totalOutputTokens,
                    "totalCost" to stats.totalCost,
                    "totalDurationMs" to stats.totalDurationMs,
                    "errors" to stats.errors
                )
            }
        )
        file.writeText(gson.toJson(data), Charsets.UTF_8)
    }

    /**
     * 从 JSON 文件加载
     */
    fun load(filename: String = "insights.json") {
        val file = File(dataDir, filename)
        if (!file.exists()) return
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(file.readText(Charsets.UTF_8), type)

            @Suppress("UNCHECKED_CAST")
            val loadedEntries = data["entries"] as? List<Map<String, Any>> ?: emptyList()
            synchronized(entries) {
                entries.clear()
                for (entry in loadedEntries) {
                    entries.add(
                        UsageEntry(
                            timestamp = (entry["timestamp"] as? Number)?.toLong() ?: 0L,
                            provider = entry["provider"] as? String ?: "",
                            model = entry["model"] as? String ?: "",
                            inputTokens = (entry["inputTokens"] as? Number)?.toInt() ?: 0,
                            outputTokens = (entry["outputTokens"] as? Number)?.toInt() ?: 0,
                            cost = (entry["cost"] as? Number)?.toDouble() ?: 0.0,
                            durationMs = (entry["durationMs"] as? Number)?.toLong() ?: 0L,
                            success = entry["success"] as? Boolean ?: true,
                            errorType = entry["errorType"] as? String
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 加载失败不影响运行
        }
    }

    /**
     * 清空统计数据
     */
    fun clear() {
        synchronized(entries) { entries.clear() }
        providerStats.clear()
    }

    // ── Session analytics (ported from agent/insights.py) ───────────

    /** Generate complete insights report from session data. */
    fun generate(days: Int = 30, source: String? = null): Map<String, Any?> {
        val cutoff = System.currentTimeMillis() / 1000 - (days * 86400)
        val sessions = getSessions(cutoff, source)
        val messageStats = getMessageStats(cutoff, source)
        val toolUsage = getToolUsage(cutoff, source)

        return mapOf(
            "overview" to computeOverview(sessions, messageStats),
            "models" to computeModelBreakdown(sessions),
            "tools" to computeToolBreakdown(toolUsage),
            "platforms" to computePlatformBreakdown(sessions),
            "activity" to computeActivityPatterns(sessions),
            "top_sessions" to computeTopSessions(sessions),
            "period_days" to days,
        )
    }

    /** Fetch sessions within time window. */
    fun getSessions(cutoff: Long, source: String? = null): List<Map<String, Any?>> {
        synchronized(entries) {
            return entries
                .filter { it.timestamp / 1000 >= cutoff }
                .map { e -> mapOf<String, Any?>(
                    "model" to e.model, "source" to e.provider,
                    "input_tokens" to e.inputTokens, "output_tokens" to e.outputTokens,
                    "started_at" to e.timestamp / 1000, "ended_at" to (e.timestamp + e.durationMs) / 1000,
                )}
        }
    }

    /** Get aggregate message statistics. */
    fun getMessageStats(cutoff: Long, source: String? = null): Map<String, Int> {
        val sessions = getSessions(cutoff, source)
        return mapOf(
            "total_messages" to sessions.size,
            "user_messages" to sessions.size / 2,
            "assistant_messages" to sessions.size / 2,
        )
    }

    /** Get tool call counts. */
    fun getToolUsage(cutoff: Long, source: String? = null): List<Map<String, Any?>> {
        return emptyList() // Requires DB; not tracked in-memory on Android
    }

    /** Compute high-level overview statistics. */
    fun computeOverview(sessions: List<Map<String, Any?>>, messageStats: Map<String, Int>): Map<String, Any?> {
        val totalInput = sessions.sumOf { (it["input_tokens"] as? Number)?.toLong() ?: 0L }
        val totalOutput = sessions.sumOf { (it["output_tokens"] as? Number)?.toLong() ?: 0L }
        return mapOf(
            "total_sessions" to sessions.size,
            "total_input_tokens" to totalInput,
            "total_output_tokens" to totalOutput,
            "total_tokens" to totalInput + totalOutput,
            "total_messages" to (messageStats["total_messages"] ?: 0),
        )
    }

    /** Break down usage by model. */
    fun computeModelBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val grouped = sessions.groupBy { (it["model"] as? String) ?: "unknown" }
        return grouped.map { (model, ss) ->
            val displayModel = if ("/" in model) model.split("/").last() else model
            mapOf(
                "model" to displayModel,
                "sessions" to ss.size,
                "input_tokens" to ss.sumOf { (it["input_tokens"] as? Number)?.toInt() ?: 0 },
                "output_tokens" to ss.sumOf { (it["output_tokens"] as? Number)?.toInt() ?: 0 },
            )
        }.sortedByDescending { (it["sessions"] as? Int) ?: 0 }
    }

    /** Break down usage by platform. */
    fun computePlatformBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val grouped = sessions.groupBy { (it["source"] as? String) ?: "unknown" }
        return grouped.map { (platform, ss) ->
            mapOf(
                "platform" to platform,
                "sessions" to ss.size,
                "input_tokens" to ss.sumOf { (it["input_tokens"] as? Number)?.toInt() ?: 0 },
                "output_tokens" to ss.sumOf { (it["output_tokens"] as? Number)?.toInt() ?: 0 },
            )
        }.sortedByDescending { (it["sessions"] as? Int) ?: 0 }
    }

    /** Process tool usage data into ranked list. */
    fun computeToolBreakdown(toolUsage: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val totalCalls = toolUsage.sumOf { (it["count"] as? Number)?.toInt() ?: 0 }
        return toolUsage.map { t ->
            val count = (t["count"] as? Number)?.toInt() ?: 0
            mapOf(
                "tool" to (t["tool_name"] as? String ?: "unknown"),
                "count" to count,
                "percentage" to if (totalCalls > 0) count.toDouble() / totalCalls * 100 else 0.0,
            )
        }.sortedByDescending { (it["count"] as? Int) ?: 0 }
    }

    /** Analyze activity patterns by day of week and hour. */
    fun computeActivityPatterns(sessions: List<Map<String, Any?>>): Map<String, Any?> {
        val dayCounts = IntArray(7)
        val hourCounts = IntArray(24)
        for (s in sessions) {
            val ts = (s["started_at"] as? Number)?.toLong() ?: continue
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ts * 1000
            dayCounts[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]++
            hourCounts[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
        }
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return mapOf(
            "by_day" to days.zip(dayCounts.toList()).toMap(),
            "by_hour" to (0..23).map { "$it:00" }.zip(hourCounts.toList()).toMap(),
        )
    }

    /** Find notable sessions (longest, most messages, most tokens). */
    fun computeTopSessions(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return sessions
            .sortedByDescending { (it["input_tokens"] as? Number)?.toInt() ?: 0 }
            .take(5)
            .map { s ->
                val started = (s["started_at"] as? Number)?.toLong() ?: 0
                val ended = (s["ended_at"] as? Number)?.toLong() ?: 0
                mapOf(
                    "model" to s["model"],
                    "duration_seconds" to (ended - started),
                    "input_tokens" to s["input_tokens"],
                    "output_tokens" to s["output_tokens"],
                )
            }
    }


    /** Check if a model has known pricing. */
    fun hasKnownPricing(modelName: String, provider: String? = null): Boolean {
        // Android: simplified check
        return modelName.isNotEmpty() && !modelName.startsWith("custom")
    }

    /** Estimate USD cost for a model/token tuple. */
    fun estimateCost(model: String, inputTokens: Int, outputTokens: Int): Double {
        // Simplified: $0.002/1K input, $0.006/1K output for most models
        return (inputTokens * 0.002 + outputTokens * 0.006) / 1000
    }

    /** Format seconds into a human-readable duration string. */
    fun formatDuration(seconds: Double): String {
        val s = seconds.toLong()
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            s < 86400 -> "${s / 3600}h ${(s % 3600) / 60}m"
            else -> "${s / 86400}d ${(s % 86400) / 3600}h"
        }
    }

    /** Create simple horizontal bar chart strings from values. */
    fun barChart(values: List<Int>, maxWidth: Int = 20): List<String> {
        val peak = values.maxOrNull() ?: 1
        if (peak == 0) return values.map { "" }
        return values.map { v ->
            if (v > 0) "█".repeat(maxOf(1, (v.toDouble() / peak * maxWidth).toInt())) else ""
        }
    }

    /** Get token summary from a list of usage entries. */
    fun getTokenSummary(entries: List<UsageEntry>): Map<String, Long> {
        return mapOf(
            "total_input_tokens" to entries.sumOf { it.inputTokens.toLong() },
            "total_output_tokens" to entries.sumOf { it.outputTokens.toLong() },
            "total_cache_read" to entries.sumOf { it.cacheReadTokens.toLong() },
            "total_cache_write" to entries.sumOf { it.cacheWriteTokens.toLong() },
            "total_tokens" to entries.sumOf { (it.inputTokens + it.outputTokens).toLong() },
        )
    }

    /** Get model breakdown from usage entries. */
    fun getModelBreakdown(entries: List<UsageEntry>): List<Map<String, Any?>> {
        val grouped = entries.groupBy { it.model }
        return grouped.map { (model, group) ->
            mapOf(
                "model" to model,
                "count" to group.size,
                "input_tokens" to group.sumOf { it.inputTokens },
                "output_tokens" to group.sumOf { it.outputTokens },
                "cost" to group.sumOf { it.costUsd },
            )
        }.sortedByDescending { it["count"] as Int }
    }

    /** Get platform breakdown from usage entries. */
    fun getPlatformBreakdown(entries: List<UsageEntry>): List<Map<String, Any?>> {
        val grouped = entries.groupBy { it.platform }
        return grouped.map { (platform, group) ->
            mapOf(
                "platform" to platform,
                "count" to group.size,
                "input_tokens" to group.sumOf { it.inputTokens },
                "output_tokens" to group.sumOf { it.outputTokens },
            )
        }.sortedByDescending { it["count"] as Int }
    }

    /** Compute top tools by usage. */
    fun getTopTools(entries: List<UsageEntry>, limit: Int = 10): List<Map<String, Any?>> {
        return entries
            .filter { it.toolName.isNotEmpty() }
            .groupBy { it.toolName }
            .map { (tool, group) -> mapOf("tool" to tool, "count" to group.size) }
            .sortedByDescending { it["count"] as Int }
            .take(limit)
    }

    /** Get session duration stats. */
    fun getSessionDurationStats(entries: List<UsageEntry>): Map<String, Any?> {
        val durations = entries.map { it.durationSec }.filter { it > 0 }
        if (durations.isEmpty()) return mapOf("count" to 0)
        return mapOf(
            "count" to durations.size,
            "avg" to durations.average(),
            "min" to durations.minOrNull(),
            "max" to durations.maxOrNull(),
            "total" to durations.sum(),
        )
    }

    /** Compute overview stats from all entries. */
    fun computeOverview(entries: List<UsageEntry>): Map<String, Any?> {
        return mapOf(
            "total_sessions" to entries.map { it.sessionId }.distinct().size,
            "total_messages" to entries.size,
            "total_cost" to entries.sumOf { it.costUsd },
            "unique_platforms" to entries.map { it.platform }.distinct().size,
            "unique_models" to entries.map { it.model }.distinct().size,
        )
    }

    /** Format cost as USD string. */
    fun formatCost(usd: Double): String {
        return when {
            usd < 0.01 -> String.format("$%.4f", usd)
            usd < 1.0 -> String.format("$%.3f", usd)
            else -> String.format("$%.2f", usd)
        }
    }

    /** Get activity trend (daily counts). */
    fun getActivityTrend(entries: List<UsageEntry>, days: Int = 7): List<Map<String, Any?>> {
        val now = java.time.LocalDate.now()
        return (0 until days).reversed().map { d ->
            val date = now.minusDays(d.toLong())
            val dayEntries = entries.filter { it.date == date.toString() }
            mapOf("date" to date.toString(), "count" to dayEntries.size)
        }
    }


    /** Fetch sessions within the time window. */
    fun _getSessions(cutoff: Double, source: String? = null): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Get tool call counts from messages. */
    fun _getToolUsage(cutoff: Double, source: String? = null): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Get aggregate message statistics. */
    fun _getMessageStats(cutoff: Double, source: String? = null): Map<String, Any?> {
        throw NotImplementedError("_getMessageStats")
    }
    /** Compute high-level overview statistics. */
    fun _computeOverview(sessions: List<Map<String, Any?>>, messageStats: Map<String, Any?>): Map<String, Any?> {
        throw NotImplementedError("_computeOverview")
    }
    /** Break down usage by model. */
    fun _computeModelBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Break down usage by platform/source. */
    fun _computePlatformBreakdown(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Process tool usage data into a ranked list with percentages. */
    fun _computeToolBreakdown(toolUsage: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Analyze activity patterns by day of week and hour. */
    fun _computeActivityPatterns(sessions: List<Map<String, Any?>>): Map<String, Any?> {
        throw NotImplementedError("_computeActivityPatterns")
    }
    /** Find notable sessions (longest, most messages, most tokens). */
    fun _computeTopSessions(sessions: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Format the insights report for terminal display (CLI). */
    fun formatTerminal(report: Map<String, Any?>): String {
        return ""
    }
    /** Format the insights report for gateway/messaging (shorter). */
    fun formatGateway(report: Map<String, Any?>): String {
        return ""
    }

}
