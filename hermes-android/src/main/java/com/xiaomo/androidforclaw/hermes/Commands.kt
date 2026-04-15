package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class CommandDef {
    // Hermes: CommandDef
}

class SlashCommandCompleter(
    val skill_commands_provider: String,
    val command_filter: String
) {
    private fun commandAllowed(slash_command: String): Unit {
    // Hermes: _command_allowed
        // Hermes: commandAllowed
    }
    private fun iterSkillCommands(): Unit {
    // Hermes: _iter_skill_commands
        // Hermes: iterSkillCommands
    }
    private fun completionText(cmd_name: String, word: String): Unit {
    // Hermes: _completion_text
        // Hermes: completionText
    }
    private fun extractPathWord(text: String): Any? {
    // Hermes: _extract_path_word
        return null
        // Hermes: extractPathWord
        return null
    }
    private fun pathCompletions(word: String, limit: String): Unit {
    // Hermes: _path_completions
        // Hermes: pathCompletions
    }
    private fun extractContextWord(text: String): Any? {
    // Hermes: _extract_context_word
        return null
        // Hermes: extractContextWord
        return null
    }
    private fun contextCompletions(word: String, limit: String): Unit {
    // Hermes: _context_completions
        // Hermes: contextCompletions
    }
    private fun getProjectFiles(): List<Any> {
    // Hermes: _get_project_files
        return emptyList()
    }
    private fun scorePath(filepath: String, query: String): Unit {
    // Hermes: _score_path
        // Hermes: scorePath
    }
    private fun fuzzyFileCompletions(word: String, query: String, limit: String): Unit {
    // Hermes: _fuzzy_file_completions
        // Hermes: fuzzyFileCompletions
    }
    private fun modelCompletions(sub_text: String, sub_lower: String): Unit {
    // Hermes: _model_completions
        // Hermes: modelCompletions
    }
    fun getCompletions(document: String, complete_event: String): List<Any> {
    // Hermes: get_completions
        return emptyList()
    }
}

class SlashCommandAutoSuggest(
    val history_suggest: String,
    val completer: String
) {
    fun getSuggestion(buffer: String, document: String): Any? {
    // Hermes: get_suggestion
        return null
        // Hermes: getSuggestion
        return null
    }
}
