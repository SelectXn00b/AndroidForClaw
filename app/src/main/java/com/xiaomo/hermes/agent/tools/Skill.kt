package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import com.xiaomo.hermes.providers.ToolDefinition
import com.xiaomo.hermes.providers.llm.ImageBlock

/**
 * Skill interface
 * Inspired by nanobot's Skill design
 */
interface Skill {
    /**
     * Skill name (corresponds to function name)
     */
    val name: String

    /**
     * Skill description
     */
    val description: String

    /**
     * Get Tool Definition (for LLM function calling)
     */
    fun getToolDefinition(): ToolDefinition

    /**
     * Execute skill
     * @param args Parameter map
     * @return SkillResult Execution result
     */
    suspend fun execute(args: Map<String, Any?>): SkillResult
}

/**
 * Skill execution result
 */
data class SkillResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any?> = emptyMap(),
    /** Inline images to include in the tool result (multimodal). */
    val images: List<ImageBlock>? = null
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any?> = emptyMap(), images: List<ImageBlock>? = null) =
            SkillResult(true, content, metadata, images)

        fun error(message: String) =
            SkillResult(false, "Error: $message")
    }

    override fun toString(): String = content
}
