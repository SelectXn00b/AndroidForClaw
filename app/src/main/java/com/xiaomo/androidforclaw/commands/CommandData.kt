package com.xiaomo.androidforclaw.commands

/**
 * Built-in chat commands, aligned with OpenClaw slash command registry.
 */

val BUILTIN_COMMANDS = listOf(
    ChatCommandDefinition(key = "stop", description = "Stop the current agent run", textAliases = listOf("/stop"), scope = CommandScope.BOTH, category = CommandCategory.SESSION),
    ChatCommandDefinition(key = "clear", description = "Clear conversation history", textAliases = listOf("/clear"), scope = CommandScope.BOTH, category = CommandCategory.SESSION),
    ChatCommandDefinition(key = "model", description = "Switch the active model", textAliases = listOf("/model"), acceptsArgs = true, scope = CommandScope.BOTH, category = CommandCategory.OPTIONS),
    ChatCommandDefinition(key = "think", description = "Set thinking level", textAliases = listOf("/think"), acceptsArgs = true, scope = CommandScope.BOTH, category = CommandCategory.OPTIONS),
    ChatCommandDefinition(key = "status", description = "Show session status", textAliases = listOf("/status"), scope = CommandScope.BOTH, category = CommandCategory.STATUS),
    ChatCommandDefinition(key = "help", description = "Show available commands", textAliases = listOf("/help"), scope = CommandScope.BOTH, category = CommandCategory.DOCS),
    ChatCommandDefinition(key = "skill", description = "Run or manage skills", textAliases = listOf("/skill"), acceptsArgs = true, scope = CommandScope.BOTH, category = CommandCategory.TOOLS),
    ChatCommandDefinition(key = "verbose", description = "Toggle verbose output", textAliases = listOf("/verbose"), scope = CommandScope.TEXT, category = CommandCategory.OPTIONS),
    ChatCommandDefinition(key = "exec", description = "Run a shell command", textAliases = listOf("/exec"), acceptsArgs = true, scope = CommandScope.TEXT, category = CommandCategory.TOOLS),
    ChatCommandDefinition(key = "cron", description = "Manage scheduled tasks", textAliases = listOf("/cron"), acceptsArgs = true, scope = CommandScope.TEXT, category = CommandCategory.MANAGEMENT)
)
