package com.xiaomo.androidforclaw.autoreply

import com.xiaomo.androidforclaw.commands.ChatCommandDefinition
import com.xiaomo.androidforclaw.commands.CommandDetection

fun buildCommandDetectionRegex(commands: List<ChatCommandDefinition>): CommandDetection {
    val aliases = commands.flatMap { it.textAliases }.toSet()
    val pattern = aliases.joinToString("|") { Regex.escape(it) }
    return CommandDetection(
        exact = aliases,
        regex = Regex("^($pattern)(?:\\s|$)", RegexOption.IGNORE_CASE)
    )
}

fun detectCommand(text: String, detection: CommandDetection): String? {
    val match = detection.regex.find(text.trim()) ?: return null
    return match.groupValues[1].lowercase()
}
