package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-policy-pipeline.ts (buildDefaultToolPolicyPipelineSteps, applyToolPolicyPipeline)
 *
 * AndroidForClaw adaptation: multi-step tool policy pipeline.
 * Filters tools through ordered policy steps: profile, byProvider.profile, allow, byProvider.allow,
 * agent, agent.byProvider, group, owner-only, subagent.
 *
 * Shared types → ToolPolicyShared.kt
 * Individual rules → ToolPolicyRules.kt
 */

import com.xiaomo.androidforclaw.logging.Log

/**
 * ToolPolicyPipeline — Multi-step tool policy pipeline.
 * Aligned with OpenClaw tool-policy-pipeline.ts.
 */
object ToolPolicyPipeline {

    private const val TAG = "ToolPolicyPipeline"

    /**
     * Build default pipeline steps (7 steps).
     * Aligned with OpenClaw buildDefaultToolPolicyPipelineSteps.
     */
    fun buildDefaultSteps(
        profilePolicy: ToolPolicyLike? = null,
        providerProfilePolicy: ToolPolicyLike? = null,
        globalPolicy: ToolPolicyLike? = null,
        globalProviderPolicy: ToolPolicyLike? = null,
        agentPolicy: ToolPolicyLike? = null,
        agentProviderPolicy: ToolPolicyLike? = null,
        groupPolicy: ToolPolicyLike? = null
    ): List<ToolPolicyPipelineStep> {
        return listOf(
            ToolPolicyPipelineStep(profilePolicy, "tools.profile", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(providerProfilePolicy, "tools.byProvider.profile", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(globalPolicy, "tools.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(globalProviderPolicy, "tools.byProvider.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(agentPolicy, "agents.{id}.tools.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(agentProviderPolicy, "agents.{id}.tools.byProvider.allow", stripPluginOnlyAllowlist = true),
            ToolPolicyPipelineStep(groupPolicy, "group tools.allow", stripPluginOnlyAllowlist = true)
        )
    }

    /**
     * Apply pipeline: filter tools through ordered steps.
     * Aligned with OpenClaw applyToolPolicyPipeline.
     */
    fun apply(
        toolNames: List<String>,
        steps: List<ToolPolicyPipelineStep>
    ): List<String> {
        var remaining = toolNames

        for (step in steps) {
            val policy = step.policy ?: continue
            remaining = filterByPolicy(remaining, policy)
            if (remaining.isEmpty()) {
                Log.w(TAG, "All tools filtered out at step '${step.label}'")
                break
            }
        }

        return remaining
    }

    /**
     * Filter tool names by a single policy.
     * Aligned with OpenClaw filterToolsByPolicy.
     */
    fun filterByPolicy(toolNames: List<String>, policy: ToolPolicyLike): List<String> {
        var result = toolNames

        // Apply allowlist: keep only allowed tools
        val expandedAllow = ToolGroups.expandToolGroups(policy.allow)
        if (expandedAllow != null && expandedAllow.isNotEmpty()) {
            val allowSet = expandedAllow.map { it.lowercase() }.toSet()
            result = result.filter { it.lowercase() in allowSet ||
                // Special: apply_patch is allowed if exec is allowed
                (it.lowercase() == "apply_patch" && "exec" in allowSet)
            }
        }

        // Apply denylist: remove denied tools
        val expandedDeny = ToolGroups.expandToolGroups(policy.deny)
        if (expandedDeny != null) {
            val denySet = expandedDeny.map { it.lowercase() }.toSet()
            result = result.filter { it.lowercase() !in denySet }
        }

        return result
    }
}
