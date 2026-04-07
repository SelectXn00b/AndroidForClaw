package com.xiaomo.androidforclaw.commands

/**
 * OpenClaw module: commands
 * Source: OpenClaw/src/commands/, auto-reply/commands-registry.types.ts
 */

enum class CommandScope { TEXT, NATIVE, BOTH }
enum class CommandCategory { SESSION, OPTIONS, STATUS, MANAGEMENT, MEDIA, TOOLS, DOCS }
enum class CommandArgType { STRING, NUMBER, BOOLEAN }

data class CommandArgChoice(val value: String, val label: String? = null)

data class CommandArgDefinition(
    val name: String,
    val description: String,
    val type: CommandArgType = CommandArgType.STRING,
    val required: Boolean = false,
    val choices: List<CommandArgChoice>? = null,
    val captureRemaining: Boolean = false
)

data class ChatCommandDefinition(
    val key: String,
    val nativeName: String? = null,
    val description: String,
    val textAliases: List<String> = emptyList(),
    val acceptsArgs: Boolean = false,
    val args: List<CommandArgDefinition>? = null,
    val scope: CommandScope = CommandScope.BOTH,
    val category: CommandCategory? = null
)

data class CommandDetection(
    val exact: Set<String>,
    val regex: Regex
)
