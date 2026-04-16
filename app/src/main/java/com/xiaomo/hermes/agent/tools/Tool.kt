package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import com.xiaomo.hermes.providers.ToolDefinition

/**
 * Tool interface - Low-level tools (inspired by nanobot's Tool base class)
 *
 * Tools are low-level, universal capabilities, such as:
 * - exec: Execute shell commands
 * - read_file: Read files
 * - write_file: Write files
 *
 * Difference from Skill:
 * - Tool: Code-level implementation, low-level operations (file, network, shell)
 * - Skill: Android-specific capabilities, business-level operations (tap, screenshot)
 */
interface Tool {
    /**
     * Tool name (corresponds to function name)
     */
    val name: String

    /**
     * Tool description
     */
    val description: String

    /**
     * Get Tool Definition (for LLM function calling)
     */
    fun getToolDefinition(): ToolDefinition

    /**
     * Execute tool
     * @param args Parameter map
     * @return ToolResult Execution result
     */
    suspend fun execute(args: Map<String, Any?>): ToolResult
}

// Tool and Skill share the same Result type
typealias ToolResult = SkillResult
