package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/tool-policy-shared.ts (TOOL_NAME_ALIASES, normalizeToolName, expandToolGroups)
 * - ../openclaw/src/agents/tool-policy.ts (ToolPolicyLike, ToolProfileId)
 *
 * AndroidForClaw adaptation: shared types and constants for tool policy pipeline.
 */

/**
 * Tool policy definition (allow/deny lists).
 * Aligned with OpenClaw ToolPolicyLike.
 */
data class ToolPolicyLike(
    val allow: List<String>? = null,
    val deny: List<String>? = null
)

/**
 * Tool profile IDs for preset tool sets.
 * Aligned with OpenClaw ToolProfileId.
 */
enum class ToolProfileId(val id: String) {
    MINIMAL("minimal"),
    CODING("coding"),
    MESSAGING("messaging"),
    FULL("full");

    companion object {
        fun fromString(s: String?): ToolProfileId? =
            entries.find { it.id == s?.lowercase() }
    }
}

/**
 * A single step in the tool policy pipeline.
 * Aligned with OpenClaw ToolPolicyPipelineStep.
 */
data class ToolPolicyPipelineStep(
    val policy: ToolPolicyLike?,
    val label: String,
    val stripPluginOnlyAllowlist: Boolean = false
)

/**
 * Tool name aliases for normalization.
 * Aligned with OpenClaw TOOL_NAME_ALIASES.
 */
object ToolNameAliases {
    private val ALIASES = mapOf(
        "bash" to "exec",
        "apply-patch" to "apply_patch"
    )

    /** Normalize a tool name: trim, lowercase, resolve aliases. */
    fun normalizeToolName(name: String): String {
        val normalized = name.trim().lowercase()
        return ALIASES[normalized] ?: normalized
    }

    /** Normalize a list of tool names. */
    fun normalizeToolList(list: List<String>?): List<String>? {
        return list?.map { normalizeToolName(it) }
    }
}

/**
 * Built-in tool groups for policy expansion.
 * Aligned with OpenClaw TOOL_GROUPS / CORE_TOOL_GROUPS.
 */
object ToolGroups {
    val GROUPS: Map<String, List<String>> = mapOf(
        "files" to listOf("read_file", "write_file", "edit_file", "list_dir"),
        "runtime" to listOf("exec"),
        "web" to listOf("web_search", "web_fetch"),
        "memory" to listOf("memory_search", "memory_get"),
        "sessions" to listOf("sessions_list", "sessions_history", "sessions_send", "sessions_spawn", "sessions_yield", "sessions_kill", "session_status", "subagents"),
        "ui" to listOf("canvas", "browser"),
        "media" to listOf("tts", "eye", "feishu_send_image"),
        "config" to listOf("config_get", "config_set"),
        "automation" to listOf("cron")
    )

    /** Expand group references in tool names list */
    fun expandToolGroups(names: List<String>?): List<String>? {
        if (names == null) return null
        val expanded = mutableListOf<String>()
        for (name in names) {
            val groupName = name.removePrefix("group:")
            val group = GROUPS[groupName]
            if (group != null) {
                expanded.addAll(group)
            } else {
                expanded.add(ToolNameAliases.normalizeToolName(name))
            }
        }
        return expanded.distinct()
    }
}
