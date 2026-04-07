/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tools/session-status-tool.ts
 *
 * AndroidForClaw adaptation: LLM-facing tool to display session status.
 * Shows model info, runtime, subagent count, and optional model override.
 * Simplified for Android (no usage provider, no queue settings, no session store).
 */
package com.xiaomo.androidforclaw.agent.tools

import com.xiaomo.androidforclaw.agent.subagent.SessionAccessResult
import com.xiaomo.androidforclaw.agent.subagent.SessionVisibilityGuard
import com.xiaomo.androidforclaw.agent.subagent.SubagentRegistry
import com.xiaomo.androidforclaw.agent.subagent.countActiveRunsForSession
import com.xiaomo.androidforclaw.agent.subagent.getRunByChildSessionKey
import com.xiaomo.androidforclaw.agent.subagent.listRunsForController
import com.xiaomo.androidforclaw.agent.subagent.resolveSubagentLabel
import com.xiaomo.androidforclaw.agent.subagent.resolveSubagentSessionStatus
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * session_status — Show session status card (model, runtime, subagents).
 * Aligned with OpenClaw createSessionStatusTool.
 */
class SessionStatusTool(
    private val registry: SubagentRegistry,
    private val callerSessionKey: String,
    private val configLoader: ConfigLoader,
) : Tool {

    override val name = "session_status"
    override val description = "Show session status card with model info, runtime, and subagent summary. " +
        "Optional: set per-session model override (model=default resets overrides)."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "sessionKey" to PropertySchema(
                            type = "string",
                            description = "Session key to query. Defaults to current session."
                        ),
                        "model" to PropertySchema(
                            type = "string",
                            description = "Set model override for this session. Use 'default' to reset."
                        ),
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val requestedKey = (args["sessionKey"] as? String)?.trim() ?: callerSessionKey

        // Visibility guard
        if (requestedKey != callerSessionKey) {
            val visibility = SessionVisibilityGuard.resolveVisibility(callerSessionKey, registry)
            val access = SessionVisibilityGuard.checkAccess(
                "view status of", callerSessionKey, requestedKey, visibility, registry
            )
            if (access is SessionAccessResult.Denied) {
                return ToolResult(success = false, content = access.reason)
            }
        }

        // Model override handling
        val modelParam = (args["model"] as? String)?.trim()
        var changedModel = false
        if (modelParam != null) {
            // On Android, model override is informational only — no persistent session store
            changedModel = true
        }

        // Build status card
        val config = try {
            configLoader.loadOpenClawConfig()
        } catch (_: Exception) { null }

        val defaultModel = config?.resolveDefaultModel() ?: "unknown"
        val activeRuns = registry.countActiveRunsForSession(requestedKey)
        val allRuns = registry.listRunsForController(requestedKey)
        val recentCompleted = allRuns.count { !it.isActive }

        // Check if requestedKey is a subagent
        val subagentRun = registry.getRunByChildSessionKey(requestedKey)
        val isSubagent = subagentRun != null

        // Time display
        val tz = TimeZone.getDefault()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        sdf.timeZone = tz
        val timeStr = sdf.format(Date())

        val statusText = buildString {
            appendLine("## Session Status")
            appendLine()
            appendLine("Session: $requestedKey")
            if (isSubagent) {
                appendLine("Role: subagent (depth=${subagentRun!!.depth})")
                appendLine("Status: ${resolveSubagentSessionStatus(subagentRun)}")
                appendLine("Task: ${subagentRun.task.take(100)}")
            } else {
                appendLine("Role: main")
            }
            appendLine()
            appendLine("Model: $defaultModel")
            if (changedModel && modelParam != null) {
                appendLine("Model override requested: $modelParam (note: per-session model override is not yet supported on Android)")
            }
            appendLine()
            appendLine("Subagents: $activeRuns active, $recentCompleted completed (${allRuns.size} total)")
            if (activeRuns > 0) {
                val activeList = allRuns.filter { it.isActive }
                for (run in activeList.take(10)) {
                    val runtime = SessionsListTool.formatDurationCompact(run.runtimeMs)
                    appendLine("  - ${resolveSubagentLabel(run)} ($runtime, model=${run.model ?: "default"})")
                }
            }
            appendLine()
            appendLine("Time: $timeStr")
        }.trimEnd()

        return ToolResult(
            success = true,
            content = statusText,
            metadata = mapOf(
                "sessionKey" to requestedKey,
                "changedModel" to changedModel,
                "activeSubagents" to activeRuns,
            )
        )
    }
}
