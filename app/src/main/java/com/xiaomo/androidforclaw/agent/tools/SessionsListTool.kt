/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/sessions-list-tool.ts
 * - ../openclaw/src/agents/subagent-control.ts (buildSubagentList)
 *
 * AndroidForClaw adaptation: LLM-facing tool to list subagent runs.
 * Format aligned with OpenClaw buildSubagentList.
 */
package com.xiaomo.androidforclaw.agent.tools

import android.util.Log
import com.xiaomo.androidforclaw.agent.subagent.SessionVisibilityGuard
import com.xiaomo.androidforclaw.agent.subagent.SubagentRegistry
import com.xiaomo.androidforclaw.agent.subagent.SubagentRunStatus
import com.xiaomo.androidforclaw.agent.subagent.isActiveSubagentRun
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * sessions_list — List active and recent subagent runs.
 * Aligned with OpenClaw buildSubagentList ordering and formatting.
 */
class SessionsListTool(
    private val registry: SubagentRegistry,
    private val parentSessionKey: String,
) : Tool {

    companion object {
        private const val TAG = "SessionsListTool"

        private val VALID_KINDS = setOf("subagent", "cron", "main", "group", "hook", "other")

        /** Format duration compactly (aligned with OpenClaw formatDurationCompact) */
        fun formatDurationCompact(ms: Long): String {
            return when {
                ms < 1000 -> "${ms}ms"
                ms < 60_000 -> "${ms / 1000}s"
                ms < 3600_000 -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
                else -> "${ms / 3600_000}h${(ms % 3600_000) / 60_000}m"
            }
        }
    }

    override val name = "sessions_list"
    override val description = "List active and recent subagent runs spawned by this session."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "status" to PropertySchema(
                            type = "string",
                            description = "Filter: 'active' (running only) or 'all' (including completed). Default: 'all'.",
                            enum = listOf("active", "all")
                        ),
                        "kinds" to PropertySchema(
                            type = "array",
                            description = "Filter by session kinds: 'subagent', 'cron', 'main', 'group', 'hook', 'other'. Default: all.",
                            items = PropertySchema(
                                type = "string",
                                description = "Session kind",
                                enum = VALID_KINDS.toList()
                            )
                        ),
                        "limit" to PropertySchema(
                            type = "number",
                            description = "Maximum number of sessions to return. Default: 50."
                        ),
                        "active_minutes" to PropertySchema(
                            type = "number",
                            description = "Only show sessions active within the last N minutes. Default: 30."
                        ),
                        "message_limit" to PropertySchema(
                            type = "number",
                            description = "Number of recent messages to include per session (0-20). Default: 0."
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val statusFilter = (args["status"] as? String)?.lowercase() ?: "all"

        // Parse kinds filter
        val kindsRaw = args["kinds"]
        val kindsFilter: Set<String>? = when (kindsRaw) {
            is List<*> -> {
                val parsed = kindsRaw.mapNotNull { (it as? String)?.lowercase() }
                    .filter { it in VALID_KINDS }
                if (parsed.isNotEmpty()) parsed.toSet() else null
            }
            else -> null
        }

        // Parse limit (default 50)
        val limit = when (val v = args["limit"]) {
            is Number -> v.toInt().coerceIn(1, 500)
            else -> 50
        }

        // Parse active_minutes (default 30)
        val activeMinutes = when (val v = args["active_minutes"]) {
            is Number -> v.toInt().coerceIn(1, 10080) // max 7 days
            else -> 30
        }

        // Parse message_limit (default 0)
        val messageLimit = when (val v = args["message_limit"]) {
            is Number -> v.toInt().coerceIn(0, 20)
            else -> 0
        }
        if (messageLimit > 0) {
            Log.w(TAG, "message_limit=$messageLimit requested but not yet implemented; ignoring")
        }

        val cutoffMs = System.currentTimeMillis() - activeMinutes * 60_000L

        // Visibility guard (aligned with OpenClaw controlScope filtering)
        val visibility = SessionVisibilityGuard.resolveVisibility(parentSessionKey, registry)

        // Use indexed list (active first, then recent) — aligned with OpenClaw
        var runs = SessionVisibilityGuard.filterVisible(
            parentSessionKey,
            registry.buildIndexedList(parentSessionKey),
            visibility,
            registry,
        )

        // Filter by status
        if (statusFilter == "active") {
            runs = runs.filter { it.isActive }
        }

        // Filter by kinds (match session key pattern e.g. ":subagent:", ":cron:", etc.)
        if (kindsFilter != null) {
            runs = runs.filter { record ->
                val key = record.childSessionKey.lowercase()
                kindsFilter.any { kind -> ":$kind:" in key }
            }
        }

        // Filter by active_minutes: keep runs that are currently active OR were active within the window
        runs = runs.filter { record ->
            if (record.isActive) {
                true // active runs always pass
            } else {
                // Check if endedAt or createdAt falls within the window
                val relevantTime = record.endedAt ?: record.createdAt
                relevantTime >= cutoffMs
            }
        }

        // Apply limit
        runs = runs.take(limit)

        if (runs.isEmpty()) {
            return ToolResult(
                success = true,
                content = "No subagent runs found (filter: status=$statusFilter, kinds=${kindsFilter ?: "all"}, active_minutes=$activeMinutes).",
            )
        }

        val text = buildString {
            // Separate active and recent sections (aligned with OpenClaw buildSubagentList)
            val active = runs.filter { it.isActive }
            val recent = runs.filter { !it.isActive }

            if (active.isNotEmpty() || statusFilter == "all") {
                appendLine("Active subagents:")
                if (active.isEmpty()) {
                    appendLine("  (none)")
                } else {
                    for ((i, run) in active.withIndex()) {
                        val pendingChildren = registry.countPendingDescendantRuns(run.childSessionKey)
                        val status = if (pendingChildren > 0) {
                            "active (waiting on $pendingChildren children)"
                        } else {
                            "active"
                        }
                        val runtime = formatDurationCompact(run.runtimeMs)
                        val model = run.model?.let { " ($it)" } ?: ""
                        val taskSnippet = if (run.task != run.label) ", ${run.task.take(80)}" else ""
                        appendLine("  ${i + 1}. ${run.label}$model, $runtime [$status]$taskSnippet")
                    }
                }
                appendLine()
            }

            if (recent.isNotEmpty() && statusFilter != "active") {
                appendLine("Recent (completed):")
                val offset = active.size
                for ((i, run) in recent.withIndex()) {
                    val status = when (run.outcome?.status) {
                        SubagentRunStatus.OK -> "done"
                        SubagentRunStatus.TIMEOUT -> "timeout"
                        SubagentRunStatus.ERROR -> "failed"
                        else -> run.outcome?.status?.wireValue ?: "unknown"
                    }
                    val runtime = formatDurationCompact(run.runtimeMs)
                    val model = run.model?.let { " ($it)" } ?: ""
                    val error = run.outcome?.error?.let { " - $it" } ?: ""
                    appendLine("  ${offset + i + 1}. ${run.label}$model, $runtime [$status]$error")
                }
            }
        }.trimEnd()

        return ToolResult(success = true, content = text)
    }
}
