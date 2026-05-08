package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.hermes.gateway.GatewayFileLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.xiaomo.hermes.hermes.AssistantMessage
import com.xiaomo.hermes.hermes.ChatCompletionResponse
import com.xiaomo.hermes.hermes.ChatCompletionServer
import com.xiaomo.hermes.hermes.Choice
import com.xiaomo.hermes.hermes.ToolCall
import com.xiaomo.hermes.hermes.ToolCallFunction
import org.json.JSONObject
import java.util.UUID

/**
 * Reverse bridge that lets the Hermes agent loop drive an Operit [AIService]
 * provider.
 *
 * Operit providers do NOT emit OpenAI-spec `tool_calls`: they stream
 * `<tool name="X"><param>…</param></tool>` XML inside the assistant text.
 * For HermesAgentLoop's standard tool-calling path to work we:
 *   1. Collect the provider's [com.ai.assistance.operit.util.stream.Stream] into a full string.
 *   2. Regex-parse `<tool>` blocks and synthesize [ToolCall] entries.
 *   3. Rebuild the tool_call_id → tool_name map so subsequent
 *      `role="tool"` messages round-trip back into `<tool_result>` XML
 *      the provider recognizes.
 */
class OperitChatCompletionServer(
    private val context: Context,
    private val service: AIService,
    private val modelParameters: List<ModelParameter<*>> = emptyList(),
    private val enableThinking: Boolean = false,
    private val availableTools: List<ToolPrompt>? = null,
    private val streamFromProvider: Boolean = false,
    private val onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit =
        { _, _, _ -> },
    private val onTurnComplete: suspend (
        inputFinal: Int,
        cachedInputFinal: Int,
        outputFinal: Int
    ) -> Unit = { _, _, _ -> },
    private val onNonFatalError: suspend (error: String) -> Unit = {}
) : ChatCompletionServer {

    override suspend fun chatCompletion(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>?,
        temperature: Double,
        maxTokens: Int?,
        extraBody: Map<String, Any?>?
    ): ChatCompletionResponse? {
        val roleCounts = messages.groupingBy { it["role"] as? String ?: "?" }.eachCount()
        Log.d(TAG, "chatCompletion IN: msgs=${messages.size} roles=$roleCounts " +
            "tools=${tools?.size ?: 0} temp=$temperature maxTokens=$maxTokens " +
            "stream=$streamFromProvider thinking=$enableThinking")

        val toolCallIdToName = buildToolCallIdToNameMap(messages)
        if (toolCallIdToName.isNotEmpty()) {
            Log.d(TAG, "chatCompletion IN: toolCallIdToName=$toolCallIdToName")
        }
        val chatHistory = messages.map { it.toPromptTurn(toolCallIdToName) }

        val aggregated = StringBuilder()
        val apiStartMs = System.currentTimeMillis()
        Log.d(TAG, "chatCompletion: calling service.sendMessage...")
        service.sendMessage(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters,
            enableThinking = enableThinking,
            stream = streamFromProvider,
            availableTools = availableTools,
            onTokensUpdated = onTokensUpdated,
            onNonFatalError = onNonFatalError
        ).collect { chunk -> aggregated.append(chunk) }
        val apiElapsedMs = System.currentTimeMillis() - apiStartMs
        Log.d(TAG, "chatCompletion: service.sendMessage completed in ${apiElapsedMs}ms")

        onTurnComplete(
            service.inputTokenCount,
            service.cachedInputTokenCount,
            service.outputTokenCount
        )

        val fullText = aggregated.toString()
        val extractResult = extractToolCalls(fullText)
        val toolCalls = extractResult.toolCalls
        val displayText = extractResult.cleanedText

        Log.d(TAG, "chatCompletion OUT: textLen=${fullText.length} " +
            "toolCalls=${toolCalls?.size ?: 0} " +
            "tokens(in=${service.inputTokenCount} cached=${service.cachedInputTokenCount} " +
            "out=${service.outputTokenCount})")
        if (!toolCalls.isNullOrEmpty()) {
            Log.d(TAG, "chatCompletion OUT: toolNames=${toolCalls.map { it.function.name }}")
        }

        // Gateway file log: full AI response (capped at 2000 chars) + token usage
        GatewayFileLogger.d(TAG, "  AI_RESPONSE turn: ${apiElapsedMs}ms " +
            "tokens(in=${service.inputTokenCount} cached=${service.cachedInputTokenCount} out=${service.outputTokenCount}) " +
            "toolCalls=${toolCalls?.size ?: 0}")
        val responsePreview = if (fullText.length > 2000) {
            fullText.take(2000) + "…[${fullText.length} total]"
        } else fullText
        GatewayFileLogger.d(TAG, "  AI_TEXT: $responsePreview")

        return ChatCompletionResponse(
            choices = listOf(
                Choice(
                    message = AssistantMessage(
                        content = displayText,
                        toolCalls = toolCalls
                    )
                )
            )
        )
    }

    internal fun buildToolCallIdToNameMap(
        messages: List<Map<String, Any?>>
    ): Map<String, String> {
        val out = HashMap<String, String>()
        for (msg in messages) {
            if (msg["role"] != "assistant") continue
            val toolCalls = msg["tool_calls"] as? List<*> ?: continue
            for (tc in toolCalls) {
                val tcMap = tc as? Map<*, *> ?: continue
                val id = tcMap["id"] as? String ?: continue
                val function = tcMap["function"] as? Map<*, *> ?: continue
                val name = function["name"] as? String ?: continue
                out[id] = name
            }
        }
        return out
    }

    internal data class ToolCallExtractResult(
        val toolCalls: List<ToolCall>?,
        val cleanedText: String
    )

    internal fun extractToolCalls(text: String): ToolCallExtractResult {
        val matches = ChatMarkupRegex.toolCallPattern.findAll(text).toList()
        if (matches.isNotEmpty()) {
            val toolCalls = matches.map { match ->
                val toolName = match.groupValues[2]
                val body = match.groupValues[3]
                val paramsJson = JSONObject()
                ChatMarkupRegex.toolParamPattern.findAll(body).forEach { paramMatch ->
                    val key = paramMatch.groupValues[1]
                    val value = unescapeXml(paramMatch.groupValues[2])
                    paramsJson.put(key, value)
                }
                ToolCall(
                    id = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    type = "function",
                    function = ToolCallFunction(
                        name = toolName,
                        arguments = paramsJson.toString()
                    )
                )
            }
            // XML tool calls are rendered as structured UI widgets by the frontend,
            // so we don't need to strip them from the text.
            return ToolCallExtractResult(toolCalls = toolCalls, cleanedText = text)
        }

        // Fallback: some models occasionally output tool calls as inline JSON
        // instead of the expected XML format, e.g.:
        //   "我来读取文件。{"path":"/sdcard/file.txt","start_line":1}"
        // Try to extract a JSON object and infer the tool name from its keys.
        val jsonResult = tryExtractJsonToolCall(text)
        return jsonResult ?: ToolCallExtractResult(toolCalls = null, cleanedText = text)
    }

    /**
     * Attempt to parse inline JSON objects from the assistant text and
     * match them to known tools based on parameter keys.
     * Supports multiple JSON objects in a single message.
     * Returns both the tool calls and text with JSON objects stripped out.
     */
    private fun tryExtractJsonToolCall(text: String): ToolCallExtractResult? {
        val jsonMatches = JSON_OBJECT_REGEX.findAll(text).toList()
        if (jsonMatches.isEmpty()) return null

        val toolCalls = mutableListOf<ToolCall>()
        val matchedRanges = mutableListOf<IntRange>()
        for (jsonMatch in jsonMatches) {
            val jsonStr = jsonMatch.value
            val params = try { JSONObject(jsonStr) } catch (_: Exception) { continue }
            val keys = params.keys().asSequence().toSet()
            if (keys.isEmpty()) continue
            val toolName = inferToolNameFromKeys(keys) ?: continue

            Log.w(TAG, "extractToolCalls: recovered JSON-format tool call as '$toolName' " +
                "(model did not use XML format). Keys: $keys")

            toolCalls.add(ToolCall(
                id = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                type = "function",
                function = ToolCallFunction(
                    name = toolName,
                    arguments = params.toString()
                )
            ))
            matchedRanges.add(jsonMatch.range)
        }

        if (toolCalls.isEmpty()) return null

        // Strip the matched JSON objects from the display text
        val cleanedText = buildString {
            var lastEnd = 0
            for (range in matchedRanges) {
                append(text.substring(lastEnd, range.first))
                lastEnd = range.last + 1
            }
            append(text.substring(lastEnd))
        }.trim()

        return ToolCallExtractResult(toolCalls = toolCalls, cleanedText = cleanedText)
    }

    /**
     * Infer tool name from a set of JSON parameter keys.
     * Uses distinctive/required parameters to identify tools with high confidence.
     */
    private fun inferToolNameFromKeys(keys: Set<String>): String? {
        // --- File tools (most specific first) ---
        if ("pattern" in keys && "use_path_pattern" in keys) return "find_files"
        if ("pattern" in keys && "file_pattern" in keys) return "grep_code"
        if ("pattern" in keys && "context_lines" in keys) return "grep_code"
        if ("intent" in keys && "file_pattern" in keys) return "grep_context"
        if ("start_line" in keys || "end_line" in keys) {
            if ("path" in keys) return "read_file_part"
        }
        if ("old" in keys && "new" in keys && "path" in keys) return "apply_file"
        if ("old_str" in keys && "path" in keys) return "edit_file"
        if ("base64Content" in keys) return "write_file_binary"
        if ("content" in keys && "path" in keys && "append" in keys) return "write_file"
        if ("content" in keys && "path" in keys) return "write_file"
        if ("recursive" in keys && "path" in keys && "source" !in keys) return "delete_file"
        if ("create_parents" in keys) return "make_directory"
        if ("source" in keys && "destination" in keys && "recursive" in keys) return "copy_file"
        if ("source" in keys && "destination" in keys) {
            // Could be move_file, copy_file, zip_files, unzip_files
            if ("source_environment" in keys || "dest_environment" in keys) return "copy_file"
            return "move_file"
        }
        if ("text_only" in keys && "path" in keys) return "read_file_full"
        if ("direct_image" in keys || "direct_audio" in keys || "direct_video" in keys) return "read_file"

        // --- Command / terminal tools ---
        if ("session_id" in keys && "command" in keys) return "execute_in_terminal_session"
        if ("session_id" in keys && "input" in keys) return "input_in_terminal_session"
        if ("session_id" in keys) return "get_terminal_session_screen"
        if ("session_name" in keys) return "create_terminal_session"
        if ("command" in keys && "executor_key" in keys) return "execute_hidden_terminal_command"
        if ("command" in keys) return "execute_shell"

        // --- HTTP tools ---
        if ("url" in keys && "method" in keys && "form_data" in keys) return "multipart_request"
        if ("url" in keys && "method" in keys) return "http_request"
        if ("url" in keys && "visit_key" in keys && "destination" in keys) return "download_file"
        if ("url" in keys && "visit_key" in keys) return "visit_web"
        if ("url" in keys && "include_image_links" in keys) return "visit_web"
        if ("url" in keys && "user_agent" in keys) return "visit_web"

        // --- UI / accessibility tools ---
        if ("resourceId" in keys || "contentDesc" in keys || "className" in keys) return "click_element"
        if ("start_x" in keys && "end_x" in keys) return "swipe"
        if ("key_code" in keys) return "press_key"
        if ("format" in keys && "detail" in keys) return "get_page_info"
        if ("ref" in keys && "selector" in keys) return "browser_click"
        if ("max_steps" in keys && "intent" in keys) return "run_ui_subagent"

        // --- Memory tools ---
        if ("query" in keys && "folder_path" in keys) return "query_memory"
        if ("query" in keys && "snapshot_id" in keys) return "query_memory"
        if ("title" in keys && "chunk_index" in keys) return "get_memory_by_title"
        if ("title" in keys && "content" in keys && "content_type" in keys) return "create_memory"
        if ("old_title" in keys) return "update_memory"
        if ("source_title" in keys && "target_title" in keys) return "link_memories"
        if ("link_id" in keys || "link_type" in keys) return "query_memory_links"

        // --- Chat tools ---
        if ("message" in keys && "chat_id" in keys && "chat_history" in keys) return "send_message_to_ai_advanced"
        if ("message" in keys && "chat_id" in keys) return "send_message_to_ai"
        if ("chat_id" in keys && "title" in keys) return "update_chat_title"
        if ("chat_id" in keys && "order" in keys) return "get_chat_messages"

        // --- Search ---
        if ("query" in keys && "engine" in keys) return "web_search"
        if ("query" in keys && keys.size <= 3) return "web_search"

        // --- Intent / broadcast ---
        if ("action" in keys && "uri" in keys && "extras" in keys) return "execute_intent"
        if ("action" in keys && "extra_key" in keys) return "send_broadcast"

        // --- Workflow ---
        if ("workflow_id" in keys && "nodes" in keys) return "update_workflow"
        if ("workflow_id" in keys && "node_patches" in keys) return "patch_workflow"
        if ("workflow_id" in keys) return "trigger_workflow"
        if ("nodes" in keys && "connections" in keys) return "create_workflow"

        // --- Settings ---
        if ("setting" in keys && "namespace" in keys && "value" in keys) return "modify_system_setting"
        if ("setting" in keys && "namespace" in keys) return "get_system_setting"
        if ("config_id" in keys && "model_index" in keys) return "test_model_config_connection"
        if ("function_type" in keys && "config_id" in keys) return "set_function_model_config"
        if ("expression" in keys) return "calculate"
        if ("task_type" in keys) return "trigger_tasker_event"

        // --- Simple tools (fewer distinctive keys, check last) ---
        if ("duration_ms" in keys) return "sleep"
        if ("package_name" in keys && "enabled" in keys) return "set_sandbox_package_enabled"
        if ("package_name" in keys && "activity" in keys) return "start_app"
        if ("package_name" in keys) return "use_package"
        if ("url" in keys) return "visit_web"
        if ("path" in keys && "pattern" in keys) return "find_files"
        if ("path" in keys && keys.size <= 3) return "read_file"
        return null
    }

    internal fun Map<String, Any?>.toPromptTurn(
        toolCallIdToName: Map<String, String>
    ): PromptTurn {
        val role = (this["role"] as? String) ?: "user"
        val rawContent = when (val c = this["content"]) {
            is String -> c
            is List<*> -> c.filterIsInstance<Map<*, *>>()
                .mapNotNull { it["text"] as? String }
                .joinToString("\n")
            null -> ""
            else -> c.toString()
        }
        if (role == "tool") {
            val toolCallId = this["tool_call_id"] as? String
            val toolName = toolCallId?.let { toolCallIdToName[it] } ?: "tool"
            val wrapped = if (rawContent.trimStart().startsWith("<tool_result")) {
                rawContent
            } else {
                val status = detectToolResultStatus(rawContent)
                "<tool_result name=\"${escapeAttr(toolName)}\" status=\"$status\">" +
                    "<content>${escapeXmlText(rawContent)}</content>" +
                    "</tool_result>"
            }
            return PromptTurn(
                kind = PromptTurnKind.TOOL_RESULT,
                content = wrapped,
                toolName = toolName
            )
        }

        // When the assistant message carries structured tool_calls, rebuild
        // the content as clean XML from the structured data so the provider
        // re-parses the exact same set of tool calls on the next turn.
        // This prevents the "User cancelled" injection that happens when
        // positional matching between re-parsed tool calls and tool results
        // gets out of sync due to regex mismatches on the raw content.
        val toolCalls = this["tool_calls"] as? List<*>
        if (role == "assistant" && toolCalls != null && toolCalls.isNotEmpty()) {
            // Strip XML tool tags from content — keep only plain text
            val textOnly = ChatMarkupRegex.toolTag.replace(rawContent, "").trim()
            // Rebuild tool XML from the structured tool_calls list
            val toolXml = StringBuilder()
            for (tc in toolCalls) {
                val tcMap = tc as? Map<*, *> ?: continue
                val function = tcMap["function"] as? Map<*, *> ?: continue
                val name = function["name"] as? String ?: continue
                val argsStr = function["arguments"] as? String ?: "{}"
                toolXml.append("<tool name=\"").append(escapeAttr(name)).append("\">")
                try {
                    val argsJson = JSONObject(argsStr)
                    argsJson.keys().forEach { key ->
                        val value = argsJson.opt(key)?.toString().orEmpty()
                        toolXml.append("<param name=\"").append(escapeAttr(key)).append("\">")
                        toolXml.append(escapeXmlText(value))
                        toolXml.append("</param>")
                    }
                } catch (_: Exception) {
                    toolXml.append("<param name=\"raw\">")
                    toolXml.append(escapeXmlText(argsStr))
                    toolXml.append("</param>")
                }
                toolXml.append("</tool>")
            }
            val rebuiltContent = if (textOnly.isNotEmpty()) {
                "$textOnly\n$toolXml"
            } else {
                toolXml.toString()
            }
            return PromptTurn(
                kind = PromptTurnKind.TOOL_CALL,
                content = rebuiltContent
            )
        }

        val kind = PromptTurnKind.fromRole(role)
        val toolName = this["name"] as? String
        return PromptTurn(kind = kind, content = rawContent, toolName = toolName)
    }

    internal fun detectToolResultStatus(json: String): String {
        return try {
            val obj = JSONObject(json)
            val success = obj.optBoolean("success", true)
            if (success && obj.optString("error").isNullOrBlank()) "success" else "error"
        } catch (_: Exception) {
            "success"
        }
    }

    private fun escapeAttr(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun escapeXmlText(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val TAG = "HermesBridge/Server"

        /** Matches a JSON object {...} embedded in assistant text. */
        private val JSON_OBJECT_REGEX = Regex("""\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""")

        /** Unescape XML entities so param values round-trip correctly. */
        private fun unescapeXml(text: String): String =
            text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
    }
}
