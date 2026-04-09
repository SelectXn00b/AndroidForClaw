package com.xiaomo.androidforclaw.agent.loop

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-subscribe.ts
 *
 * Streaming tool execution callbacks and response post-processing.
 * Extracted from AgentLoop for 1:1 file alignment.
 */

import com.xiaomo.androidforclaw.providers.LLMToolCall
import com.xiaomo.androidforclaw.providers.ToolDefinition

// Anthropic refusal magic string scrub (aligned with OpenClaw scrubAnthropicRefusalMagic)
internal const val ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL = "ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL"
internal const val ANTHROPIC_MAGIC_STRING_REPLACEMENT = "ANTHROPIC MAGIC STRING TRIGGER REFUSAL (redacted)"

/**
 * Scrub Anthropic refusal magic string from system prompt messages.
 * Aligned with OpenClaw scrubAnthropicRefusalMagic:
 * Replaces "ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL" (which triggers Anthropic's
 * refusal filter) with a redacted version.
 * Only applied to system messages (the system prompt).
 */
internal fun scrubAnthropicRefusalMagic(messages: List<com.xiaomo.androidforclaw.providers.llm.Message>): List<com.xiaomo.androidforclaw.providers.llm.Message> {
    // Fast path: no magic string present
    val hasMagic = messages.any { it.role == "system" && it.content?.contains(ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL) == true }
    if (!hasMagic) return messages

    return messages.map { msg ->
        if (msg.role == "system" && msg.content?.contains(ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL) == true) {
            msg.copy(content = msg.content.replace(ANTHROPIC_MAGIC_STRING_TRIGGER_REFUSAL, ANTHROPIC_MAGIC_STRING_REPLACEMENT))
        } else {
            msg
        }
    }
}

/**
 * Sanitize and repair tool calls from LLM response.
 * 对齐 OpenClaw stream wrapper chain:
 * 1. ToolCallNormalization.normalizeToolCallNameForDispatch — 工具名规范化（候选名 + toolId 推断）
 * 2. ToolCallArgumentRepair.tryExtractUsableToolCallArguments — 参数修复（平衡 JSON 提取）
 * 3. ToolCallArgumentRepair.decodeHtmlEntitiesInObject — HTML 实体解码
 *
 * @param toolCalls Raw tool calls from LLM
 * @param allowedToolNames Set of valid tool names for normalization
 * @param writeLog Logging callback
 * @return Sanitized tool calls
 */
internal fun sanitizeToolCalls(
    toolCalls: List<LLMToolCall>,
    allowedToolNames: Set<String>,
    writeLog: (String) -> Unit
): List<LLMToolCall> {
    return toolCalls.map { tc ->
        // 1. 工具名规范化（对齐 OpenClaw normalizeToolCallNameForDispatch）
        val normalizedName = normalizeToolCallNameForDispatch(
            rawName = tc.name,
            allowedToolNames = allowedToolNames,
            rawToolCallId = tc.id
        )

        // 2. 参数修复（对齐 OpenClaw tryExtractUsableToolCallArguments）
        val repairedArgs = if (tc.arguments.isBlank()) {
            tc.arguments
        } else {
            val repairResult = tryExtractUsableToolCallArguments(tc.arguments)
            if (repairResult != null && repairResult.kind == "repaired") {
                writeLog("🔧 Repaired tool arguments for $normalizedName " +
                    "(leading: ${repairResult.leadingPrefix.length} chars, " +
                    "trailing: ${repairResult.trailingSuffix.length} chars)")
                com.google.gson.Gson().toJson(repairResult.args)
            } else if (repairResult != null) {
                tc.arguments  // preserved, no change needed
            } else {
                // Fallback: 旧版修复逻辑（补充缺失括号）
                repairMalformedJsonFallback(tc.arguments)
            }
        }

        // 3. HTML 实体解码（对齐 OpenClaw decodeHtmlEntitiesInObject）
        val decodedArgs = if (repairedArgs.contains("&#") || repairedArgs.contains("&amp;") ||
            repairedArgs.contains("&lt;") || repairedArgs.contains("&gt;")) {
            val decoded = decodeHtmlEntities(repairedArgs)
            if (decoded != repairedArgs) writeLog("🔧 Decoded HTML entities in tool call args for $normalizedName")
            decoded
        } else {
            repairedArgs
        }

        if (normalizedName != tc.name) {
            writeLog("🔧 Normalized tool name: '${tc.name}' → '${normalizedName}'")
        }

        LLMToolCall(id = tc.id, name = normalizedName, arguments = decodedArgs)
    }
}

/**
 * Fallback JSON repair — 仅在 tryExtractUsableToolCallArguments 失败时使用。
 * 补充缺失的闭合括号/花括号。
 */
internal fun repairMalformedJsonFallback(arguments: String): String {
    val trimmed = arguments.trim()
    try {
        com.google.gson.JsonParser.parseString(trimmed)
        return trimmed
    } catch (_: Exception) { /* continue */ }

    var repaired = trimmed
    val openBraces = repaired.count { it == '{' }
    val closeBraces = repaired.count { it == '}' }
    val openBrackets = repaired.count { it == '[' }
    val closeBrackets = repaired.count { it == ']' }
    repaired += "}".repeat((openBraces - closeBraces).coerceAtLeast(0))
    repaired += "]".repeat((openBrackets - closeBrackets).coerceAtLeast(0))

    try {
        com.google.gson.JsonParser.parseString(repaired)
        return repaired
    } catch (_: Exception) { /* give up */ }

    return trimmed
}
