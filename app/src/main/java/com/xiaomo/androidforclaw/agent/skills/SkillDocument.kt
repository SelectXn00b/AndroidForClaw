package com.xiaomo.androidforclaw.agent.skills

/**
 * Skill Document Data Model
 * Corresponds to AgentSkills.io format
 *
 * File format:
 * ---
 * name: skill-name
 * description: Skill description
 * metadata:
 *   {
 *     "openclaw": {
 *       "always": true,
 *       "emoji": "📱",
 *       "requires": {
 *         "bins": ["binary"],
 *         "env": ["ENV_VAR"],
 *         "config": ["config.key"]
 *       }
 *     }
 *   }
 * ---
 * # Skill Content
 * ...
 */
data class SkillDocument(
    /**
     * Skill name (unique identifier)
     * e.g.: "mobile-operations", "app-testing"
     */
    val name: String,

    /**
     * Skill description (1-2 sentences)
     * e.g.: "Core mobile device operation skills"
     */
    val description: String,

    /**
     * Skill metadata
     */
    val metadata: SkillMetadata,

    /**
     * Skill body content (Markdown format)
     * This part will be injected into system prompt
     */
    val content: String,

    /**
     * Skill source
     * "bundled" - Built-in at assets/skills/
     * "managed" - From /sdcard/.androidforclaw/skills/ (aligns with ~/.openclaw/skills/)
     * "workspace" - From /sdcard/.androidforclaw/workspace/skills/ (aligns with ~/.openclaw/workspace/)
     */
    val source: SkillSource = SkillSource.BUNDLED
) {
    /**
     * Get formatted content (with title)
     */
    fun getFormattedContent(): String {
        val emoji = metadata.emoji ?: ""
        val title = if (emoji.isNotEmpty()) "$emoji $name" else name
        return """
# $title

$content
        """.trim()
    }

    /**
     * Estimate token count (rough estimate: 1 token ≈ 4 characters)
     */
    fun estimateTokens(): Int {
        return (content.length / 4.0).toInt()
    }
}

/**
 * Skill Metadata
 */
data class SkillMetadata(
    /**
     * Whether to always load (load at startup)
     * true: Load into all system prompts
     * false: Load on demand
     */
    val always: Boolean = false,

    /**
     * Skill's emoji icon
     * e.g.: "📱", "🧪", "🐛"
     */
    val emoji: String? = null,

    /**
     * Skill dependency requirements
     */
    val requires: SkillRequires? = null
)

/**
 * Skill Source Enum
 * Aligns with OpenClaw three-tier architecture
 */
enum class SkillSource(val displayName: String) {
    BUNDLED("bundled"),      // assets/skills/
    MANAGED("managed"),      // /sdcard/.androidforclaw/skills/ (aligns with ~/.openclaw/skills/)
    WORKSPACE("workspace")   // /sdcard/.androidforclaw/workspace/skills/ (aligns with ~/.openclaw/workspace/)
}

/**
 * Skill Dependency Requirements
 * Used to check if Skill is available
 */
data class SkillRequires(
    /**
     * Required binary tools
     * e.g.: ["adb", "ffmpeg"]
     */
    val bins: List<String> = emptyList(),

    /**
     * Required environment variables
     * e.g.: ["ANDROID_HOME", "PATH"]
     */
    val env: List<String> = emptyList(),

    /**
     * Required config items
     * e.g.: ["api.key", "device.id"]
     */
    val config: List<String> = emptyList()
) {
    /**
     * Check if there are any dependencies
     */
    fun hasRequirements(): Boolean {
        return bins.isNotEmpty() || env.isNotEmpty() || config.isNotEmpty()
    }
}
