package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-policy.ts (isOwnerOnlyToolName, applyOwnerOnlyToolPolicy)
 * - ../openclaw/src/security/dangerous-tools.ts (DEFAULT_GATEWAY_HTTP_TOOL_DENY, DANGEROUS_ACP_TOOLS)
 *
 * AndroidForClaw adaptation: individual policy rules/evaluators.
 */

/**
 * Owner-only tools that require sender to be the device owner.
 * Aligned with OpenClaw OWNER_ONLY_TOOL_NAME_FALLBACKS.
 */
object OwnerOnlyTools {
    private val OWNER_ONLY_TOOL_NAMES = setOf(
        "whatsapp_login",
        "cron",
        "gateway",
        "nodes"
    )

    fun isOwnerOnlyToolName(name: String): Boolean =
        ToolNameAliases.normalizeToolName(name) in OWNER_ONLY_TOOL_NAMES

    /**
     * Filter tools based on owner status.
     * Aligned with OpenClaw applyOwnerOnlyToolPolicy.
     */
    fun filterByOwnerStatus(
        toolNames: List<String>,
        senderIsOwner: Boolean
    ): List<String> {
        if (senderIsOwner) return toolNames
        return toolNames.filter { !isOwnerOnlyToolName(it) }
    }
}

/**
 * Dangerous tools that should be restricted in certain contexts.
 * Aligned with OpenClaw dangerous-tools.ts.
 */
object DangerousTools {
    /**
     * Tools denied on Gateway HTTP by default.
     * Aligned with OpenClaw DEFAULT_GATEWAY_HTTP_TOOL_DENY.
     */
    val DEFAULT_GATEWAY_HTTP_TOOL_DENY = setOf(
        "sessions_spawn", "sessions_send", "cron", "gateway", "whatsapp_login"
    )

    /**
     * Tools dangerous for ACP (inter-agent) calls.
     * Aligned with OpenClaw DANGEROUS_ACP_TOOL_NAMES.
     */
    val DANGEROUS_ACP_TOOLS = setOf(
        "exec", "spawn", "shell",
        "sessions_spawn", "sessions_send", "gateway",
        "fs_write", "fs_delete", "fs_move", "apply_patch"
    )
}

/**
 * Subagent tool restrictions.
 * Aligned with OpenClaw subagent tool policy.
 */
object SubagentToolPolicy {
    /** Tools that subagents (non-root agents) should not have access to */
    private val SUBAGENT_RESTRICTED_TOOLS = setOf(
        "cron",
        "config_set",
        "config_get"
    )

    fun filterForSubagent(
        toolNames: List<String>,
        isSubagent: Boolean
    ): List<String> {
        if (!isSubagent) return toolNames
        return toolNames.filter { it !in SUBAGENT_RESTRICTED_TOOLS }
    }
}

/**
 * Resolve tool profile policy (preset tool sets).
 * Aligned with OpenClaw resolveToolProfilePolicy.
 */
fun resolveToolProfilePolicy(profileId: ToolProfileId?): ToolPolicyLike? {
    return when (profileId) {
        ToolProfileId.MINIMAL -> ToolPolicyLike(
            allow = listOf("read_file", "list_dir", "web_search", "web_fetch")
        )
        ToolProfileId.CODING -> ToolPolicyLike(
            allow = listOf("read_file", "write_file", "edit_file", "list_dir", "exec", "web_search", "web_fetch")
        )
        ToolProfileId.MESSAGING -> ToolPolicyLike(
            allow = listOf("read_file", "list_dir", "web_search", "web_fetch",
                "memory_search", "memory_get", "sessions_list", "sessions_history",
                "sessions_send", "tts", "canvas")
        )
        ToolProfileId.FULL, null -> null  // null = no restriction
    }
}
