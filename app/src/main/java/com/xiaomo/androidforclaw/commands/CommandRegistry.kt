package com.xiaomo.androidforclaw.commands

/**
 * OpenClaw module: commands
 * Source: OpenClaw/src/commands/, auto-reply/commands-registry.ts
 *
 * Command registry for slash commands and text-based command detection.
 */

object CommandRegistry {

    private val commands = mutableListOf<ChatCommandDefinition>().apply {
        addAll(BUILTIN_COMMANDS)
    }

    fun getChatCommands(): List<ChatCommandDefinition> = commands.toList()

    fun registerCommand(command: ChatCommandDefinition) {
        commands.add(command)
    }

    fun buildTextCommandDetection(): CommandDetection {
        val aliases = commands.flatMap { it.textAliases }.toSet()
        val pattern = aliases.joinToString("|") { Regex.escape(it) }
        return CommandDetection(
            exact = aliases,
            regex = Regex("^($pattern)(?:\\s|$)", RegexOption.IGNORE_CASE)
        )
    }

    fun detectCommand(text: String): String? {
        val detection = buildTextCommandDetection()
        val match = detection.regex.find(text.trim()) ?: return null
        return match.groupValues[1].lowercase()
    }
}
