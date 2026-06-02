package com.ai.assistance.operit.core.chat.hooks

enum class PromptTurnKind {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
    SUMMARY;

    companion object {
        fun fromRole(role: String): PromptTurnKind {
            return when (role.trim().lowercase()) {
                "system" -> SYSTEM
                "user" -> USER
                "assistant", "ai" -> ASSISTANT
                "tool", "tool_result" -> TOOL_RESULT
                "tool_call", "tool_use" -> TOOL_CALL
                "summary" -> SUMMARY
                else -> USER
            }
        }
    }
}

data class PromptTurn(
    val kind: PromptTurnKind,
    val content: String,
    val toolName: String? = null,
    val reasoningContent: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    val role: String
        get() =
            when (kind) {
                PromptTurnKind.SYSTEM -> "system"
                PromptTurnKind.USER -> "user"
                PromptTurnKind.ASSISTANT -> "assistant"
                PromptTurnKind.TOOL_CALL -> "tool_call"
                PromptTurnKind.TOOL_RESULT -> "tool_result"
                PromptTurnKind.SUMMARY -> "summary"
            }

    companion object {
        fun fromRole(
            role: String,
            content: String,
            toolName: String? = null,
            reasoningContent: String? = null,
            metadata: Map<String, Any?> = emptyMap()
        ): PromptTurn {
            return PromptTurn(
                kind = PromptTurnKind.fromRole(role),
                content = content,
                toolName = toolName,
                reasoningContent = reasoningContent,
                metadata = metadata
            )
        }
    }
}

fun PromptTurn.withContent(newContent: String): PromptTurn {
    return if (newContent == content) this else copy(content = newContent)
}

fun List<PromptTurn>.appendUserTurnIfMissing(message: String): List<PromptTurn> {
    if (message.isBlank()) {
        return this
    }
    val lastTurn = lastOrNull()
    return if (lastTurn?.kind == PromptTurnKind.USER && lastTurn.content == message) {
        this
    } else {
        this + PromptTurn(kind = PromptTurnKind.USER, content = message)
    }
}

fun List<PromptTurn>.mergeAdjacentTurns(
    shouldMerge: (PromptTurn, PromptTurn) -> Boolean = { previous, current ->
        previous.kind == current.kind &&
            previous.kind !in setOf(PromptTurnKind.SYSTEM, PromptTurnKind.TOOL_CALL, PromptTurnKind.TOOL_RESULT) &&
            previous.toolName == current.toolName
    }
): List<PromptTurn> {
    if (size <= 1) {
        return this
    }

    val merged = mutableListOf<PromptTurn>()
    for (turn in this) {
        val previous = merged.lastOrNull()
        if (previous != null && shouldMerge(previous, turn)) {
            merged[merged.lastIndex] =
                previous.copy(
                    content = previous.content + "\n" + turn.content,
                    metadata = if (turn.metadata.isEmpty()) previous.metadata else previous.metadata + turn.metadata
                )
        } else {
            merged.add(turn)
        }
    }
    return merged
}

fun List<Pair<String, String>>.toPromptTurns(): List<PromptTurn> {
    return map { (role, content) ->
        // For assistant messages, extract inline <think>...</think> into reasoningContent
        // so MiMo thinking-mode can roundtrip it. Other providers ignore the field.
        if (role.trim().lowercase() in setOf("assistant", "ai")) {
            val (cleaned, thinking) = com.ai.assistance.operit.util.ChatUtils.extractThinkingContent(content)
            val rc = thinking.ifBlank { null }
            if (rc != null) {
                com.ai.assistance.operit.hermes.gateway.GatewayFileLogger.w("PromptTurn",
                    "[MIMO_DBG] toPromptTurns assistant: extracted reasoning=len=${rc.length}")
            }
            PromptTurn.fromRole(role = role, content = cleaned, reasoningContent = rc)
        } else {
            PromptTurn.fromRole(role = role, content = content)
        }
    }
}

fun List<PromptTurn>.toRoleContentPairs(): List<Pair<String, String>> {
    return map { turn ->
        turn.role to turn.content
    }
}

/**
 * OpenAI / MIMO chat-completion wire role for a [PromptTurnKind].
 *
 * NOTE the asymmetry vs [PromptTurn.role]: the wire format only accepts
 * `system | user | assistant | tool`, so [PromptTurnKind.TOOL_CALL] collapses
 * to "assistant" and **[PromptTurnKind.SUMMARY] collapses to "user"**
 * (not "system" — putting it on "system" sends two consecutive `role=system`
 * messages back to MIMO which rejects with 400 "Errors in message queue
 * response"). Aligns with `OpenAIProvider.kt` which already treats SUMMARY as
 * a `user_boundary`.
 *
 * Single source of truth: `EnhancedAIService.toOpenAiMessages` and the two
 * branches in `HermesAdapter` (gateway + non-gateway) all call this. See
 * TC-AGENT-245-a/b/c/d.
 */
fun PromptTurnKind.toOpenAiRole(): String = when (this) {
    PromptTurnKind.SYSTEM -> "system"
    PromptTurnKind.USER -> "user"
    PromptTurnKind.ASSISTANT -> "assistant"
    PromptTurnKind.TOOL_CALL -> "assistant"
    PromptTurnKind.TOOL_RESULT -> "tool"
    PromptTurnKind.SUMMARY -> "user"
}
