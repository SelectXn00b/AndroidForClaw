/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-control.ts (killControlledSubagentRun, cascadeKillChildren)
 *
 * AndroidForClaw adaptation: LLM-facing tool to kill running subagents.
 * Supports multi-strategy target resolution and cascade kill of descendants.
 */
package com.xiaomo.androidforclaw.agent.tools

import com.xiaomo.androidforclaw.agent.subagent.resolveTarget
import com.xiaomo.androidforclaw.agent.subagent.SubagentSpawner
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

/**
 * sessions_kill — Kill a running subagent and optionally cascade to its descendants.
 * Aligned with OpenClaw killControlledSubagentRun + cascadeKillChildren.
 *
 * Target resolution supports: "last", numeric index, session key, label, label prefix, runId prefix.
 */
class SessionsKillTool(
    private val spawner: SubagentSpawner,
    private val parentSessionKey: String,
) : Tool {

    override val name = "sessions_kill"
    override val description = "Kill a running subagent by target (label, index, run ID, or 'last'). " +
        "By default, also kills all descendant subagents (cascade)."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "target" to PropertySchema(
                            type = "string",
                            description = "Target subagent: 'last', numeric index (1-based), label, label prefix, run ID, or session key."
                        ),
                        "cascade" to PropertySchema(
                            type = "boolean",
                            description = "Also kill all descendant subagents. Default: true."
                        ),
                    ),
                    required = listOf("target")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val target = args["target"] as? String
        if (target.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: target")
        }
        val cascade = (args["cascade"] as? Boolean) ?: true

        // Resolve target using multi-strategy resolution
        val record = spawner.registry.resolveTarget(target, parentSessionKey)
            ?: return ToolResult(success = false, content = "No matching subagent found for target: $target")

        val (success, killedIds) = spawner.kill(record.runId, cascade)
        return if (success) {
            ToolResult(
                success = true,
                content = if (killedIds.size == 1) {
                    "Killed subagent '${record.label}' (${killedIds[0]})."
                } else {
                    "Killed ${killedIds.size} run(s) (cascade): ${killedIds.joinToString(", ")}"
                }
            )
        } else {
            ToolResult(success = false, content = "Failed to kill '${record.label}': not found or already completed.")
        }
    }
}
