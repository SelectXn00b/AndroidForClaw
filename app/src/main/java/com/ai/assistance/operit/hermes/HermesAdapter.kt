package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.stream
import com.xiaomo.hermes.hermes.AgentEvent
import com.xiaomo.hermes.hermes.AgentEventSink
import com.xiaomo.hermes.hermes.HermesAgentLoop
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Entry point Operit UI code calls instead of [EnhancedAIService.sendMessage].
 *
 * Streams structured XML chunks (`<think>`, `<tool>`, `<tool_result>`, plain
 * assistant text) into Operit's [Stream] so [CustomXmlRenderer] can render
 * them incrementally, exactly as it would for the legacy provider output.
 */
class HermesAdapter private constructor(private val context: Context) {

    private val serviceManager = MultiServiceManager(context.applicationContext)

    suspend fun sendMessage(
        message: String,
        chatId: String,
        chatHistory: List<PromptTurn> = emptyList(),
        functionType: FunctionType = FunctionType.CHAT,
        maxTokens: Int? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        gatewayMode: Boolean = false
    ): Stream<String> {
        val resolvedService = if (chatModelConfigIdOverride != null && chatModelIndexOverride != null) {
            serviceManager.getServiceForConfig(chatModelConfigIdOverride, chatModelIndexOverride)
        } else {
            serviceManager.getServiceForFunction(functionType)
        }

        // Gateway mode: caller explicitly sets gatewayMode=true.
        // Legacy behaviour: treat empty chatHistory as gateway call.
        val isGatewayCall = gatewayMode || chatHistory.isEmpty()

        val apiPrefs = ApiPreferences.getInstance(context.applicationContext)
        val enableTools = apiPrefs.enableToolsFlow.first()
        val useEnglish = LocaleUtils.getCurrentLanguage(context.applicationContext)
            .lowercase().startsWith("en")

        // --- Tool list ---------------------------------------------------
        // Use getAIAllCategoriesEn/Cn (NOT getAllCategories) — same as
        // Update 48.  This gives the AI only basic tools (sleep,
        // use_package, file tools, http, memory).  UI automation tools
        // (get_page_info, tap, etc.) and system tools (start_app, etc.)
        // are accessed via use_package, NOT included directly.
        // Including internal tools (browser_*, execute_shell, etc.)
        // causes the AI to pick wrong tools (e.g., browser_navigate
        // instead of start_app).
        val allTools = if (isGatewayCall && enableTools) {
            val categories = if (useEnglish) {
                SystemToolPrompts.getAIAllCategoriesEn()
            } else {
                SystemToolPrompts.getAIAllCategoriesCn()
            }
            categories.flatMap { it.tools }
        } else null

        val openAiToolSchemas = allTools?.let(::toolPromptsToOpenAiSchemas) ?: emptyList()
        val validNames = extractToolNames(openAiToolSchemas)

        // Pass availableTools = null so the provider does NOT send a
        // `tools` JSON field in the HTTP request.  This keeps the model in
        // pure text-completion mode where it can freely emit any
        // `<tool name="system_tools:start_app">` XML — including package
        // tools discovered via use_package.  The tool definitions are already
        // embedded in the system prompt (useToolCallApi = false).
        // If we passed the tool list here, the provider's enableToolCall
        // would lock the model into function-calling mode, preventing it
        // from calling dynamically-discovered package tools.
        val server = OperitChatCompletionServer(
            context = context.applicationContext,
            service = resolvedService,
            availableTools = null,
            streamFromProvider = true
        )
        val dispatcher = OperitToolDispatcher(context.applicationContext)

        // --- Messages (with system prompt for gateway) -------------------
        val openAiMessages = if (isGatewayCall) {
            val pkgMgr = PackageManager.getInstance(
                context.applicationContext,
                AIToolHandler.getInstance(context.applicationContext))
            val rawSystemPrompt = SystemPromptConfig.getSystemPrompt(
                context = context.applicationContext,
                packageManager = pkgMgr,
                useEnglish = useEnglish,
                enableTools = enableTools,
                enableMemoryQuery = apiPrefs.enableMemoryQueryFlow.first(),
                useToolCallApi = false,  // XML-in-text: tools described inside prompt
                thinkingGuidance = true  // Critical: includes tool-call example that
                                         // teaches the model to emit <tool> XML
            )

            // Resolve the active character card's intro prompt so the gateway
            // AI has the same identity/personality as the APP UI path.
            val introPrompt = try {
                val activeCardId = ActivePromptManager.getInstance(context.applicationContext)
                    .resolveActiveCardIdForSend()
                if (activeCardId != null) {
                    CharacterCardManager.getInstance(context.applicationContext)
                        .combinePrompts(activeCardId, promptFunctionType = PromptFunctionType.CHAT)
                } else ""
            } catch (e: Throwable) {
                Log.w(TAG, "failed to resolve character card intro: ${e.message}")
                ""
            }

            val systemPrompt = SystemPromptConfig.applyCustomPrompts(rawSystemPrompt, introPrompt)

            // Build message list: system + history (if any) + current user message
            val msgs = ArrayList<Map<String, Any?>>(chatHistory.size + 2)
            msgs.add(mapOf("role" to "system", "content" to systemPrompt))

            // Include prior conversation turns from gateway history so the
            // model has multi-turn context (memory across messages).
            for (turn in chatHistory) {
                val role = when (turn.kind) {
                    PromptTurnKind.SYSTEM -> "system"
                    PromptTurnKind.USER -> "user"
                    PromptTurnKind.ASSISTANT -> "assistant"
                    PromptTurnKind.TOOL_CALL -> "assistant"
                    PromptTurnKind.TOOL_RESULT -> "tool"
                    PromptTurnKind.SUMMARY -> "system"
                }
                msgs.add(mapOf("role" to role, "content" to turn.content))
            }

            msgs.add(mapOf("role" to "user", "content" to message))
            msgs
        } else {
            buildOpenAiMessages(chatHistory, message)
        }

        Log.d(TAG, "sendMessage: chatId=$chatId historyTurns=${chatHistory.size} " +
            "msgLen=${message.length} functionType=$functionType maxTokens=$maxTokens " +
            "configOverride=$chatModelConfigIdOverride gateway=$isGatewayCall " +
            "tools=${allTools?.size ?: 0}")

        return stream {
            val collector: StreamCollector<String> = this
            val sink: AgentEventSink = { event -> collector.emitAgentEvent(event) }

            val prefs = HermesGatewayPreferences.getInstance(context.applicationContext)
            val configuredMaxTurns = prefs.agentMaxTurnsFlow.first()

            val loop = HermesAgentLoop(
                server = server,
                toolSchemas = openAiToolSchemas,
                validToolNames = emptySet(),
                toolDispatcher = dispatcher,
                maxTurns = configuredMaxTurns,
                taskId = chatId,
                maxTokens = maxTokens,
                eventSink = sink
            )

            loop.run(openAiMessages.toMutableList())
        }
    }

    private suspend fun StreamCollector<String>.emitAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Thinking -> {
                Log.v(TAG, "event Thinking turn=${event.turn} len=${event.text.length}")
                emit("<think>${escapeXml(event.text)}</think>")
            }
            is AgentEvent.AssistantDelta -> {
                if (event.text.isNotEmpty()) {
                    Log.v(TAG, "event AssistantDelta turn=${event.turn} len=${event.text.length}")
                    emit(event.text)
                }
            }
            is AgentEvent.ToolCallStart -> {
                Log.d(TAG, "event ToolCallStart turn=${event.turn} id=${event.toolCallId} name=${event.name}")
                emit(renderToolCallXml(event.name, event.argsJson))
            }
            is AgentEvent.ToolCallEnd -> {
                Log.d(TAG, "event ToolCallEnd turn=${event.turn} id=${event.toolCallId} " +
                    "name=${event.name} error=${event.error?.take(100)}")
                val synthetic = ToolResult(
                    toolName = event.name,
                    success = event.error == null,
                    result = StringResultData(event.resultJson),
                    error = event.error
                )
                emit(ConversationMarkupManager.formatToolResultForMessage(synthetic))
            }
            is AgentEvent.Error -> {
                Log.w(TAG, "event Error turn=${event.turn} msg=${event.message}")
                emit("<status type=\"error\">${escapeXml(event.message)}</status>")
            }
            is AgentEvent.Final -> {
                Log.i(TAG, "event Final turn=${event.turnsUsed} finishedNaturally=${event.finishedNaturally}")
                // Emit a sentinel carrying the final-turn assistant text so the
                // gateway controller can extract the clean reply without regex
                // heuristics on the concatenated raw stream.
                if (event.text.isNotEmpty()) {
                    emit("${FINAL_REPLY_SENTINEL}${event.text}${FINAL_REPLY_SENTINEL_END}")
                }
            }
        }
    }

    private fun renderToolCallXml(toolName: String, argsJson: String): String {
        val params = try {
            val json = JSONObject(argsJson)
            buildString {
                json.keys().forEach { key ->
                    val value = json.opt(key)?.toString().orEmpty()
                    append("<param name=\"").append(escapeXml(key)).append("\">")
                    append(escapeXml(value))
                    append("</param>")
                }
            }
        } catch (_: Exception) {
            "<param name=\"raw\">${escapeXml(argsJson)}</param>"
        }
        return "<tool name=\"${escapeXml(toolName)}\">$params</tool>"
    }

    private fun escapeXml(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun buildOpenAiMessages(
        history: List<PromptTurn>,
        currentUserMessage: String
    ): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>(history.size + 1)
        for (turn in history) {
            val role = when (turn.kind) {
                PromptTurnKind.SYSTEM -> "system"
                PromptTurnKind.USER -> "user"
                PromptTurnKind.ASSISTANT -> "assistant"
                PromptTurnKind.TOOL_CALL -> "assistant"
                PromptTurnKind.TOOL_RESULT -> "tool"
                PromptTurnKind.SUMMARY -> "system"
            }
            out.add(mapOf("role" to role, "content" to turn.content))
        }
        if (currentUserMessage.isNotEmpty() && out.lastOrNull()?.get("role") != "user") {
            out.add(mapOf("role" to "user", "content" to currentUserMessage))
        }
        return out
    }

    companion object {
        private const val TAG = "HermesBridge/Adapter"

        /** Sentinels wrapping the final-turn assistant text in the raw stream. */
        const val FINAL_REPLY_SENTINEL = "\u0000__FINAL_REPLY__\u0000"
        const val FINAL_REPLY_SENTINEL_END = "\u0000__/FINAL_REPLY__\u0000"

        @Volatile
        private var instance: HermesAdapter? = null

        fun getInstance(context: Context): HermesAdapter {
            return instance ?: synchronized(this) {
                instance ?: HermesAdapter(context.applicationContext).also { instance = it }
            }
        }
    }
}
