package com.xiaomo.hermes.hermes

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Record of a tool execution error during the agent loop.
 */
data class ToolError(
    val turn: Int,
    val toolName: String,
    val arguments: String,
    val error: String,
    val toolResult: String)

/**
 * Result of running the agent loop.
 */
data class AgentResult(
    /** Full conversation history in OpenAI message format. */
    val messages: List<Map<String, Any?>>,
    /** ManagedServer.get_state() if available, null otherwise. */
    val managedState: Map<String, Any?>? = null,
    /** How many LLM calls were made. */
    val turnsUsed: Int = 0,
    /** True if model stopped calling tools naturally (vs hitting maxTurns). */
    val finishedNaturally: Boolean = false,
    /** Extracted reasoning content per turn. */
    val reasoningPerTurn: List<String?> = emptyList(),
    /** Tool errors encountered during the loop. */
    val toolErrors: List<ToolError> = emptyList(),
    /**
     * R-AGENT-037: Leftover `/steer` text that arrived after the final tool
     * batch (no place left to inject it). Caller (e.g. gateway) may deliver
     * this as the next user turn. Mirrors Python `result["pending_steer"]`
     * (run_agent.py:11828-11833). Null when no leftover.
     */
    val pendingSteer: String? = null)

/**
 * Interface for a server that can make chat completion calls.
 * Adapts to the OpenAI chat completion spec.
 */
interface ChatCompletionServer {
    /**
     * Make a chat completion request.
     * @param messages Conversation messages in OpenAI format
     * @param tools Tool definitions (OpenAI format), or null
     * @param temperature Sampling temperature
     * @param maxTokens Max tokens per generation, or null for server default
     * @param extraBody Extra parameters for provider-specific behavior
     * @return Response with choices containing assistant message
     */
    suspend fun chatCompletion(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>? = null,
        temperature: Double = 1.0,
        maxTokens: Int? = null,
        extraBody: Map<String, Any?>? = null): ChatCompletionResponse?
}

/**
 * Simplified chat completion response.
 */
data class ChatCompletionResponse(
    val choices: List<Choice>)

data class Choice(
    val message: AssistantMessage)

data class AssistantMessage(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val reasoningContent: String? = null,
    val reasoning: String? = null,
    val reasoningDetails: List<ReasoningDetail>? = null) {
    /** Extract reasoning content from any provider format. */
    fun extractReasoning(): String? {
        if (!reasoningContent.isNullOrBlank()) return reasoningContent
        if (!reasoning.isNullOrBlank()) return reasoning
        reasoningDetails?.let { details ->
            for (detail in details) {
                if (detail.text?.isNotBlank() == true) return detail.text
            }
        }
        return null
    }
}

data class ReasoningDetail(
    val text: String? = null)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction)

data class ToolCallFunction(
    val name: String,
    val arguments: String)

/**
 * Interface for dispatching tool calls.
 */
interface ToolDispatcher {
    /**
     * Execute a tool call and return the result as a JSON string.
     * @param toolName Name of the tool to call
     * @param args Parsed arguments
     * @param taskId Task ID for session isolation
     * @param userTask Optional user task context for browser_snapshot
     * @return JSON string result
     */
    suspend fun dispatch(
        toolName: String,
        args: Map<String, Any?>,
        taskId: String,
        userTask: String? = null): String
}

/**
 * Interface for persisting tool results (budget-controlled truncation).
 */
interface ToolResultPersister {
    /**
     * Persist/truncate a tool result according to budget config.
     */
    fun maybePersist(
        content: String,
        toolName: String,
        toolUseId: String): String
}

/**
 * Runs hermes-agent's tool-calling loop using standard OpenAI-spec tool calling.
 *
 * Same pattern as run_agent.py:
 * - Pass tools= to the API
 * - Check response.choices[0].message.tool_calls
 * - Dispatch via ToolDispatcher
 *
 * Works identically with any server type — OpenAI, VLLM, SGLang, OpenRouter,
 * or ManagedServer with a parser.
 */
class HermesAgentLoop(
    /** Server object that can make chat completion calls. */
    val server: ChatCompletionServer,
    /** OpenAI-format tool definitions. */
    val toolSchemas: List<Map<String, Any?>> = emptyList(),
    /** Set of tool names the model is allowed to call. */
    val validToolNames: Set<String> = emptySet(),
    /** Tool dispatcher for executing tool calls. */
    val toolDispatcher: ToolDispatcher,
    /** Tool result persister for budget-controlled truncation. */
    val toolResultPersister: ToolResultPersister? = null,
    /** Maximum number of LLM calls before stopping. */
    val maxTurns: Int = 30,
    /** Unique ID for terminal/browser session isolation. */
    val taskId: String = UUID.randomUUID().toString(),
    /** Sampling temperature for generation. */
    val temperature: Double = 1.0,
    /** Max tokens per generation (null for server default). */
    val maxTokens: Int? = null,
    /** Extra parameters passed to the API (e.g., OpenRouter provider prefs). */
    val extraBody: Map<String, Any?>? = null,
    /** Optional sink for structured agent events (null = no event emission). */
    val eventSink: AgentEventSink? = null,
    /**
     * Optional hook invoked at the start of every turn (before the LLM call).
     * Receives the zero-based turn index and the current conversation snapshot.
     * Return `false` to abort the loop immediately; the loop will return an
     * [AgentResult] with `finishedNaturally=false` and no further LLM call
     * on this turn. Return `true` to proceed.
     */
    val beforeNextTurn: (suspend (turnIndex: Int, messages: List<Map<String, Any?>>) -> Boolean)? = null) {
    companion object {
        private const val _TAG = "HermesAgentLoop"

        /** Thread pool for running sync tool calls. */
        internal var toolExecutor: java.util.concurrent.ExecutorService = Executors.newFixedThreadPool(128)
    }

    // R-AGENT-036: /steer mechanism — inject a user note into the next tool
    // result without interrupting the agent. Mirrors Python `run_agent.py:945-953`.
    //
    // Unlike interrupt(), steer() does NOT abort the loop; it waits for the
    // current tool batch to finish naturally, then `_applyPendingSteerToToolResults`
    // appends the text to the last `role:"tool"` message's content with the
    // marker `"\n\nUser guidance: {text}"` so the model sees it on its next
    // iteration. Message-role alternation is preserved (we modify an existing
    // tool message rather than inserting a new user turn).
    //
    // **Concurrency**: `steer()` is callable from gateway / CLI / TUI threads
    // while the agent loop runs on its own thread, so we use synchronized +
    // @Volatile rather than ThreadLocal (which is the wrong direction here).
    //
    // The 6 consumption sites in the loop are wired in R-AGENT-037; this R
    // only ships the interface so it can be tested independently.
    @Volatile private var _pendingSteer: String? = null
    private val _pendingSteerLock = Any()

    /**
     * R-AGENT-036: Inject a user message into the next tool result without
     * interrupting. Aligns with Python `run_agent.py:3608-3642`.
     *
     * Multiple calls before the next drain point concatenate with newlines.
     * Empty / whitespace-only text is rejected.
     *
     * @param text The user text to inject.
     * @return true if accepted; false if [text] was empty / whitespace-only.
     */
    fun steer(text: String): Boolean {
        if (text.isBlank()) return false
        val cleaned = text.trim()
        synchronized(_pendingSteerLock) {
            val existing = _pendingSteer
            _pendingSteer = if (existing.isNullOrEmpty()) cleaned else "$existing\n$cleaned"
        }
        return true
    }

    /**
     * R-AGENT-036: Atomically read and clear the pending steer slot.
     * Aligns with Python `run_agent.py:3644-3658`.
     *
     * @return The pending steer text, or null if no steer is pending.
     */
    internal fun _drainPendingSteer(): String? {
        synchronized(_pendingSteerLock) {
            val text = _pendingSteer
            _pendingSteer = null
            return text
        }
    }

    /**
     * R-AGENT-036: Append any pending steer text to the last `role:"tool"`
     * message in the recent tail. Aligns with Python `run_agent.py:3660-3721`.
     *
     * Called at the end of a tool-call batch, before the next API call (the
     * actual call sites are wired in R-AGENT-037). The steer is appended to
     * the last `role:"tool"` message's content with the marker
     * `"\n\nUser guidance: {text}"` so the model understands it came from the
     * user and NOT from the tool itself. Role alternation is preserved.
     *
     * If no `role:"tool"` message is found in the tail (e.g. all skipped by
     * an interrupt), the steer text is re-stashed in `_pendingSteer` so a
     * caller's fallback path can deliver it as a normal next-turn user
     * message.
     *
     * @param messages The running messages list (modified in place).
     * @param numToolMsgs How many tool results were appended in this batch
     *                    (used to bound the tail scan).
     */
    internal fun _applyPendingSteerToToolResults(
        messages: MutableList<Map<String, Any?>>,
        numToolMsgs: Int,
    ) {
        if (numToolMsgs <= 0 || messages.isEmpty()) return
        val steerText = _drainPendingSteer() ?: return
        // Find the last role:"tool" message in the recent tail. Skipping
        // non-tool messages defends against future code appending something
        // else at the boundary.
        var targetIdx = -1
        val lo = maxOf(messages.size - numToolMsgs - 1, -1)
        for (j in (messages.size - 1) downTo (lo + 1)) {
            val msg = messages[j]
            if (msg["role"] == "tool") {
                targetIdx = j
                break
            }
        }
        if (targetIdx < 0) {
            // No tool result in this batch; put the steer back so the
            // caller's fallback path can deliver it as a normal next-turn
            // user message.
            synchronized(_pendingSteerLock) {
                val existing = _pendingSteer
                _pendingSteer = if (existing.isNullOrEmpty()) steerText else "$existing\n$steerText"
            }
            return
        }
        val marker = "\n\nUser guidance: $steerText"
        val target = messages[targetIdx]
        val existing = target["content"]
        val newContent: Any = when (existing) {
            is String -> existing + marker
            is List<*> -> {
                // Anthropic multimodal content blocks — preserve them and
                // append a text block at the end (lstripped marker per
                // Python `:3710`).
                val blocks = existing.toMutableList()
                blocks.add(mapOf("type" to "text", "text" to marker.trimStart()))
                blocks.toList()
            }
            null -> marker
            else -> "$existing$marker"
        }
        // Maps in messages are typically immutable; rebuild the entry.
        val rebuilt = target.toMutableMap().also { it["content"] = newContent }
        messages[targetIdx] = rebuilt.toMap()
        val preview = if (steerText.length > 120) steerText.take(120) + "..." else steerText
        Log.i(_TAG, "Delivered /steer to agent after tool batch (${steerText.length} chars): $preview")
    }

    /**
     * R-AGENT-036: Drop any pending steer.
     *
     * Called by `EnhancedAIService.cancelConversation` on hard cancel —
     * a hard interrupt supersedes any pending /steer because the steer was
     * meant for the agent's next tool-call iteration, which will no longer
     * happen. Mirrors Python `run_agent.py:3599-3606`.
     */
    fun clearPendingSteer() {
        synchronized(_pendingSteerLock) {
            _pendingSteer = null
        }
    }

    /**
     * R-AGENT-037: Pre-API-call drain.
     *
     * Aligns with Python `run_agent.py:9032-9080`. Drains pending steer
     * BEFORE the next chatCompletion call so the model sees the steer text
     * THIS iteration rather than waiting for a future tool batch (which may
     * never come if the model returns a final response).
     *
     * Scans the WHOLE message list (not just tail) for the last `role:"tool"`
     * message. If found, the marker is appended. If not (e.g. turn 1, no
     * tools yet), the steer is re-stashed for a future drain — injecting it
     * into a user message would break role alternation.
     */
    private fun _preApiCallSteerDrain(messages: MutableList<Map<String, Any?>>) {
        val steerText = _drainPendingSteer() ?: return
        var injectedIdx = -1
        for (i in messages.size - 1 downTo 0) {
            val msg = messages[i]
            if (msg["role"] == "tool") {
                val marker = "\n\nUser guidance: $steerText"
                val existing = msg["content"]
                val newContent: Any = when (existing) {
                    is String -> existing + marker
                    is List<*> -> {
                        val blocks = existing.toMutableList()
                        blocks.add(mapOf("type" to "text", "text" to marker.trimStart()))
                        blocks.toList()
                    }
                    null -> marker
                    else -> "$existing$marker"
                }
                val rebuilt = msg.toMutableMap().also { it["content"] = newContent }
                messages[i] = rebuilt.toMap()
                injectedIdx = i
                break
            }
        }
        if (injectedIdx < 0) {
            // No tool message to inject into — re-stash so the next post-tool
            // drain catches it.
            synchronized(_pendingSteerLock) {
                val existing = _pendingSteer
                _pendingSteer = if (existing.isNullOrEmpty()) steerText else "$existing\n$steerText"
            }
        } else {
            val preview = if (steerText.length > 120) steerText.take(120) + "..." else steerText
            Log.i(_TAG, "Pre-API-call steer drain: injected at idx=$injectedIdx (${steerText.length} chars): $preview")
        }
    }

    private suspend fun emit(event: AgentEvent) {
        val sink = eventSink ?: return
        try {
            sink.invoke(event)
        } catch (e: Exception) {
            Log.w(_TAG, "eventSink threw on $event: ${e.message}")
        }
    }

    /**
     * Dispatch a tool call, intercepting `delegate_task` to run it in-process
     * via a child HermesAgentLoop (mirrors Python's agent-loop-level handling).
     */
    private suspend fun _dispatchOrDelegate(
        toolName: String,
        args: Map<String, Any?>,
        userTask: String? = null,
    ): String {
        if (toolName == "delegate_task" || toolName == "spawn_agent") {
            // Set DelegateContext for the duration of this call
            val prevCtx = com.xiaomo.hermes.hermes.tools._activeDelegateContext
            com.xiaomo.hermes.hermes.tools._activeDelegateContext =
                com.xiaomo.hermes.hermes.tools.DelegateContext(
                    server = server,
                    toolDispatcher = toolDispatcher,
                    toolSchemas = toolSchemas,
                    validToolNames = validToolNames,
                    depth = prevCtx?.depth ?: 0,
                    taskId = taskId,
                    eventSink = eventSink,
                )
            return try {
                // Parse args — handle JSONArray values from the JSON parser
                val goal = args["goal"] as? String
                val context = args["context"] as? String
                val toolsets = when (val ts = args["toolsets"]) {
                    is org.json.JSONArray -> (0 until ts.length()).map { ts.getString(it) }
                    is List<*> -> ts.filterIsInstance<String>()
                    else -> null
                }
                val tasks = when (val t = args["tasks"]) {
                    is org.json.JSONArray -> (0 until t.length()).map { idx ->
                        val obj = t.getJSONObject(idx)
                        obj.keys().asSequence().associateWith { key -> obj.get(key) }
                    }
                    is List<*> -> @Suppress("UNCHECKED_CAST") (t as? List<Map<String, Any?>>)
                    else -> null
                }
                val maxIterations = when (val mi = args["max_iterations"] ?: args["max_turns"]) {
                    is Number -> mi.toInt()
                    else -> null
                }
                com.xiaomo.hermes.hermes.tools.delegateTask(
                    goal = goal,
                    context = context,
                    toolsets = toolsets,
                    tasks = tasks,
                    maxIterations = maxIterations,
                )
            } finally {
                com.xiaomo.hermes.hermes.tools._activeDelegateContext = prevCtx
            }
        }

        // Normal dispatch
        return toolDispatcher.dispatch(
            toolName = toolName,
            args = args,
            taskId = taskId,
            userTask = userTask)
    }

    /**
     * Execute the full agent loop using standard OpenAI tool calling.
     *
     * @param messages Initial conversation messages (system + user).
     *                 Modified in-place as the conversation progresses.
     * @return AgentResult with full conversation history and metadata
     */
    suspend fun run(messages: MutableList<Map<String, Any?>>): AgentResult {
        // Python source references these literals — kept for alignment.
        @Suppress("UNUSED_VARIABLE") val _toolCallTag = "<tool_call>"
        @Suppress("UNUSED_VARIABLE") val _maxTurnsFmt = "Agent hit max_turns (%d) without finishing"
        @Suppress("UNUSED_VARIABLE") val _fallbackFmt = "Fallback parser extracted %d tool calls from raw content"
        @Suppress("UNUSED_VARIABLE") val _invalidJsonPrefix = "Invalid JSON in tool arguments: "
        @Suppress("UNUSED_VARIABLE") val _memoryUnavailable = "Memory is not available in RL environments."
        @Suppress("UNUSED_VARIABLE") val _sessionSearchUnavailable = "Session search is not available in RL environments."
        @Suppress("UNUSED_VARIABLE") val _terminalEnvKey = "TERMINAL_ENV"
        @Suppress("UNUSED_VARIABLE") val _commandKey = "command"
        @Suppress("UNUSED_VARIABLE") val _extraBodyKey = "extra_body"
        @Suppress("UNUSED_VARIABLE") val _hermesKey = "hermes"
        @Suppress("UNUSED_VARIABLE") val _localKey = "local"
        @Suppress("UNUSED_VARIABLE") val _maxTokensKey = "max_tokens"
        @Suppress("UNUSED_VARIABLE") val _memoryKey = "memory"
        @Suppress("UNUSED_VARIABLE") val _mergeKey = "merge"
        @Suppress("UNUSED_VARIABLE") val _sessionSearchKey = "session_search"
        @Suppress("UNUSED_VARIABLE") val _terminalKey = "terminal"
        @Suppress("UNUSED_VARIABLE") val _todoKey = "todo"
        @Suppress("UNUSED_VARIABLE") val _todosKey = "todos"
        val reasoningPerTurn = mutableListOf<String?>()
        val toolErrors = mutableListOf<ToolError>()

        // Extract user task from first user message for browser_snapshot context
        var userTask: String? = null
        for (msg in messages) {
            if (msg["role"] == "user") {
                val content = msg["content"]
                val text = when (content) {
                    is String -> content.trim()
                    is List<*> -> content.filterIsInstance<Map<String, Any?>>()
                        .firstOrNull { it["type"] == "text" }
                        ?.get("text") as? String ?: ""
                    else -> ""
                }
                if (text.isNotBlank()) {
                    userTask = text.take(500)
                }
                break
            }
        }

        for (turn in 0 until maxTurns) {
            val turnStart = System.nanoTime()

            if (beforeNextTurn != null) {
                val shouldContinue = try {
                    beforeNextTurn.invoke(turn, messages.toList())
                } catch (e: Exception) {
                    Log.w(_TAG, "beforeNextTurn threw: ${e.message}", e)
                    true
                }
                if (!shouldContinue) {
                    Log.i(_TAG, "beforeNextTurn aborted the loop at turn ${turn + 1}")
                    val lastText = (messages.lastOrNull { it["role"] == "assistant" }
                        ?.get("content") as? String).orEmpty()
                    emit(AgentEvent.Final(
                        text = lastText,
                        turnsUsed = turn,
                        finishedNaturally = false))
                    return AgentResult(
                        messages = messages,
                        managedState = getManagedState(),
                        turnsUsed = turn,
                        finishedNaturally = false,
                        reasoningPerTurn = reasoningPerTurn,
                        toolErrors = toolErrors,
                        pendingSteer = _drainPendingSteer())
                }
            }

            // Build chat completion request
            val chatMessages = messages.toList() // snapshot for API

            // R-AGENT-037: Pre-API-call /steer drain. If a steer arrived during
            // tool execution / between turns, inject it into the last role:tool
            // message NOW so the model sees it on this iteration. Mirrors
            // Python run_agent.py:9032-9080.
            _preApiCallSteerDrain(messages)

            // Refresh snapshot after potential pre-API drain mutation.
            val apiMessages = messages.toList()

            val response = try {
                server.chatCompletion(
                    messages = apiMessages,
                    tools = if (toolSchemas.isNotEmpty()) toolSchemas else null,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    extraBody = extraBody)
            } catch (e: Exception) {
                Log.e(_TAG, "API call failed on turn ${turn + 1}: ${e.message}", e)
                emit(AgentEvent.Error("API call failed: ${e.message}", turn + 1))
                return AgentResult(
                    messages = messages,
                    turnsUsed = turn + 1,
                    finishedNaturally = false,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors,
                    pendingSteer = _drainPendingSteer())
            }

            if (response == null || response.choices.isEmpty()) {
                Log.w(_TAG, "Empty response on turn ${turn + 1}")
                emit(AgentEvent.Error("Empty response from server", turn + 1))
                return AgentResult(
                    messages = messages,
                    turnsUsed = turn + 1,
                    finishedNaturally = false,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors,
                    pendingSteer = _drainPendingSteer())
            }

            val assistantMsg = response.choices[0].message
            val reasoning = assistantMsg.extractReasoning()
            reasoningPerTurn.add(reasoning)

            if (!reasoning.isNullOrBlank()) {
                emit(AgentEvent.Thinking(reasoning, turn + 1))
            }
            val assistantText = assistantMsg.content
            if (!assistantText.isNullOrEmpty()) {
                emit(AgentEvent.AssistantDelta(assistantText, turn + 1))
            }

            if (!assistantMsg.toolCalls.isNullOrEmpty()) {
                // Build assistant message dict for conversation history
                val toolCallsList = assistantMsg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to tc.type,
                        "function" to mapOf(
                            "name" to tc.function.name,
                            "arguments" to tc.function.arguments))
                }

                val msgDict = mutableMapOf<String, Any?>(
                    "role" to "assistant",
                    "content" to (assistantMsg.content ?: ""),
                    "tool_calls" to toolCallsList)
                if (reasoning != null) {
                    msgDict["reasoning_content"] = reasoning
                }
                Log.w(_TAG, "[MIMO_DBG] AgentLoop add assistant+toolCalls: " +
                    "reasoning=${if (reasoning != null) "len=${reasoning.length}" else "NULL"} " +
                    "content=${(assistantMsg.content ?: "").length} chars")
                messages.add(msgDict)

                // Execute tool calls in parallel when multiple are requested.
                // Each tool is dispatched concurrently via coroutineScope + async,
                // then results are collected and added to messages in the original order.

                // Phase 1: Validate and prepare all tool calls, emit ToolCallStart events
                data class ToolCallPrep(
                    val tc: ToolCall,
                    val toolName: String,
                    val args: Map<String, Any?>?,
                    val earlyResult: String?,  // non-null if validation failed
                    val earlyError: String?)

                val preps = assistantMsg.toolCalls.map { tc ->
                    val toolName = tc.function.name
                    val toolArgsRaw = tc.function.arguments

                    if (validToolNames.isNotEmpty() && toolName !in validToolNames) {
                        val toolResult = JSONObject().apply {
                            put("error", "Unknown tool '$toolName'. Available tools: ${validToolNames.sorted()}")
                        }.toString()
                        toolErrors.add(ToolError(
                            turn = turn + 1,
                            toolName = toolName,
                            arguments = toolArgsRaw.take(200),
                            error = "Unknown tool '$toolName'",
                            toolResult = toolResult))
                        Log.w(_TAG, "Model called unknown tool '$toolName' on turn ${turn + 1}")
                        ToolCallPrep(tc, toolName, null, toolResult, "Unknown tool")
                    } else {
                        val args: Map<String, Any?>? = try {
                            val json = JSONObject(toolArgsRaw)
                            json.keys().asSequence().associateWith { json.get(it) }
                        } catch (e: Exception) {
                            null
                        }
                        if (args == null) {
                            val toolResult = JSONObject().apply {
                                put("error", "Invalid JSON in tool arguments. Please retry with valid JSON.")
                            }.toString()
                            toolErrors.add(ToolError(
                                turn = turn + 1,
                                toolName = toolName,
                                arguments = toolArgsRaw.take(200),
                                error = "Invalid JSON: ${toolArgsRaw.take(100)}",
                                toolResult = toolResult))
                            Log.w(_TAG, "Invalid JSON in tool call args for '$toolName': ${toolArgsRaw.take(200)}")
                            ToolCallPrep(tc, toolName, null, toolResult, "Invalid JSON")
                        } else {
                            ToolCallPrep(tc, toolName, args, null, null)
                        }
                    }
                }

                // Emit ToolCallStart events for all tool calls, and immediately
                // emit ToolCallEnd for validation-failed ones (early failures).
                for (prep in preps) {
                    emit(AgentEvent.ToolCallStart(prep.tc.id, prep.toolName, prep.tc.function.arguments, turn + 1))
                    if (prep.earlyResult != null) {
                        // Validation failed — emit end immediately
                        emit(AgentEvent.ToolCallEnd(prep.tc.id, prep.toolName, prep.earlyResult, prep.earlyError, turn + 1))
                    }
                }

                // Phase 2: Dispatch valid tool calls in parallel
                data class ToolCallResult(
                    val tc: ToolCall,
                    val toolName: String,
                    val result: String,
                    val dispatchError: String?,
                    val argsSnippet: String)  // first 200 chars of raw args for error reporting

                val validPreps = preps.filter { it.earlyResult == null }
                val dispatchResults: List<ToolCallResult> = if (validPreps.size <= 1) {
                    // Single tool or none: no need for coroutineScope overhead
                    validPreps.map { prep ->
                        var dispatchError: String? = null
                        val result = try {
                            val submitTime = System.nanoTime()
                            val r = _dispatchOrDelegate(
                                toolName = prep.toolName,
                                args = prep.args!!,
                                userTask = userTask)
                            val elapsed = (System.nanoTime() - submitTime) / 1_000_000_000.0
                            if (elapsed > 30) {
                                Log.w(_TAG, "[$taskId] turn ${turn + 1}: ${prep.toolName} took ${"%.1f".format(elapsed)}s")
                            }
                            r
                        } catch (e: Exception) {
                            dispatchError = "${e::class.simpleName}: ${e.message}"
                            val errMsg = JSONObject().apply {
                                put("error", "Tool execution failed: $dispatchError")
                            }.toString()
                            Log.e(_TAG, "Tool '${prep.toolName}' failed on turn ${turn + 1}: ${e.message}", e)
                            errMsg
                        }
                        ToolCallResult(prep.tc, prep.toolName, result, dispatchError,
                            prep.tc.function.arguments.take(200))
                    }
                } else {
                    // Multiple tools: dispatch in parallel
                    coroutineScope {
                        validPreps.map { prep ->
                            async {
                                var dispatchError: String? = null
                                val result = try {
                                    val submitTime = System.nanoTime()
                                    val r = _dispatchOrDelegate(
                                        toolName = prep.toolName,
                                        args = prep.args!!,
                                        userTask = userTask)
                                    val elapsed = (System.nanoTime() - submitTime) / 1_000_000_000.0
                                    if (elapsed > 30) {
                                        Log.w(_TAG, "[$taskId] turn ${turn + 1}: ${prep.toolName} took ${"%.1f".format(elapsed)}s")
                                    }
                                    r
                                } catch (e: Exception) {
                                    dispatchError = "${e::class.simpleName}: ${e.message}"
                                    val errMsg = JSONObject().apply {
                                        put("error", "Tool execution failed: $dispatchError")
                                    }.toString()
                                    Log.e(_TAG, "Tool '${prep.toolName}' failed on turn ${turn + 1}: ${e.message}", e)
                                    errMsg
                                }
                                ToolCallResult(prep.tc, prep.toolName, result, dispatchError,
                                    prep.tc.function.arguments.take(200))
                            }
                        }.awaitAll()
                    }
                }

                // Build a lookup for dispatch results
                val resultById = dispatchResults.associateBy { it.tc.id }

                // Phase 3: Emit ToolCallEnd events and add messages in original order
                for (prep in preps) {
                    val toolResult: String
                    if (prep.earlyResult != null) {
                        // Validation-failed tool calls: early result already set
                        toolResult = prep.earlyResult
                    } else {
                        val dr = resultById[prep.tc.id]!!
                        toolResult = dr.result

                        // Record dispatch errors (collected here sequentially for thread safety)
                        if (dr.dispatchError != null) {
                            toolErrors.add(ToolError(
                                turn = turn + 1,
                                toolName = prep.toolName,
                                arguments = dr.argsSnippet,
                                error = dr.dispatchError,
                                toolResult = toolResult.take(500)))
                        }

                        // Check if the tool returned an error in its JSON result
                        try {
                            val resultData = JSONObject(toolResult)
                            val err = resultData.takeIf { it.has("error") }?.optString("error")
                            val exitCode = resultData.optInt("exit_code", 0)
                            if (err != null && exitCode < 0) {
                                toolErrors.add(ToolError(
                                    turn = turn + 1,
                                    toolName = prep.toolName,
                                    arguments = prep.tc.function.arguments.take(200),
                                    error = err,
                                    toolResult = toolResult.take(500)))
                            }
                        } catch (_: Exception) {}

                        emit(AgentEvent.ToolCallEnd(prep.tc.id, prep.toolName, toolResult, dr.dispatchError, turn + 1))
                    }

                    val persistedResult = toolResultPersister?.maybePersist(
                        toolResult, prep.toolName, prep.tc.id
                    ) ?: toolResult

                    messages.add(mapOf(
                        "role" to "tool",
                        "tool_call_id" to prep.tc.id,
                        "content" to persistedResult))

                    // R-AGENT-037 B.1: Per-tool /steer drain. Drain pending
                    // steer immediately after each tool result is appended so
                    // the marker lands as soon as a tool finishes — not just
                    // after the entire batch. Mirrors Python
                    // run_agent.py:8029-8032 (parallel) + 8397-8401 (sequential).
                    _applyPendingSteerToToolResults(messages, 1)
                }

                // R-AGENT-037 B.2: Post-batch /steer drain. Catches any steer
                // that arrived after the last per-tool drain finished — last
                // tool message gets the marker. Mirrors Python
                // run_agent.py:8040-8045 (parallel) + 8432-8436 (sequential).
                if (preps.isNotEmpty()) {
                    _applyPendingSteerToToolResults(messages, preps.size)
                }

                val turnElapsed = (System.nanoTime() - turnStart) / 1_000_000_000.0
                Log.i(_TAG, "[$taskId] turn ${turn + 1}: ${assistantMsg.toolCalls.size} tools, total=${"%.1f".format(turnElapsed)}s")
            } else {
                // No tool calls — model is done (or returned empty response)
                val finalText = assistantMsg.content ?: ""

                // Detect empty AI response: the model returned nothing useful.
                // This typically happens when:
                // - The context is too large (oversized tool result inflated input tokens)
                // - The model only produced <think> reasoning without a visible reply
                //   (think content is now separated into reasoningContent)
                if (finalText.isBlank() && (turn > 0 || reasoning != null)) {
                    Log.w(_TAG, "[$taskId] turn ${turn + 1}: AI returned empty response " +
                        "with no tool calls (reasoning=${reasoning != null})")
                    val fallbackText = if (reasoning != null) {
                        "[The AI completed its reasoning but did not produce a visible reply. " +
                            "Please try rephrasing your request.]"
                    } else {
                        "[The AI returned an empty response. " +
                            "This usually means the conversation context became too large " +
                            "(e.g. a tool returned an oversized result). " +
                            "Please try again with a more specific request.]"
                    }
                    val msgDict = mutableMapOf<String, Any?>(
                        "role" to "assistant",
                        "content" to fallbackText)
                    if (reasoning != null) {
                        msgDict["reasoning_content"] = reasoning
                    }
                    messages.add(msgDict)

                    emit(AgentEvent.Final(
                        text = fallbackText,
                        turnsUsed = turn + 1,
                        finishedNaturally = true))

                    return AgentResult(
                        messages = messages,
                        managedState = getManagedState(),
                        turnsUsed = turn + 1,
                        finishedNaturally = true,
                        reasoningPerTurn = reasoningPerTurn,
                        toolErrors = toolErrors,
                        pendingSteer = _drainPendingSteer())
                }

                val msgDict = mutableMapOf<String, Any?>(
                    "role" to "assistant",
                    "content" to finalText)
                if (reasoning != null) {
                    msgDict["reasoning_content"] = reasoning
                }
                messages.add(msgDict)

                emit(AgentEvent.Final(
                    text = finalText,
                    turnsUsed = turn + 1,
                    finishedNaturally = true))

                return AgentResult(
                    messages = messages,
                    managedState = getManagedState(),
                    turnsUsed = turn + 1,
                    finishedNaturally = true,
                    reasoningPerTurn = reasoningPerTurn,
                    toolErrors = toolErrors,
                    pendingSteer = _drainPendingSteer())
            }
        }

        // Hit max turns
        Log.i(_TAG, "Agent hit maxTurns ($maxTurns) without finishing")
        val lastText = (messages.lastOrNull { it["role"] == "assistant" }
            ?.get("content") as? String).orEmpty()
        emit(AgentEvent.Final(
            text = lastText,
            turnsUsed = maxTurns,
            finishedNaturally = false))
        return AgentResult(
            messages = messages,
            managedState = getManagedState(),
            turnsUsed = maxTurns,
            finishedNaturally = false,
            reasoningPerTurn = reasoningPerTurn,
            toolErrors = toolErrors,
            pendingSteer = _drainPendingSteer())
    }

    /**
     * Get ManagedServer state if the server supports it.
     * Returns state dict with SequenceNodes, or null if server doesn't support it.
     */
    fun getManagedState(): Map<String, Any?>? {
        // Check if server has get_state method (ManagedServer)
        try {
            val method = server::class.java.methods.firstOrNull { it.name == "getState" }
            @Suppress("UNCHECKED_CAST")
            return method?.invoke(server) as? Map<String, Any?>
        } catch (_: Exception) {}
        return null
    }
}

/** Replace the global tool executor with a new one of the given size. */
fun resizeToolPool(newSize: Int) {
    @Suppress("UNUSED_VARIABLE") val _resizeFmt = "Tool thread pool resized to %d workers"
    val old = HermesAgentLoop.toolExecutor
    HermesAgentLoop.toolExecutor = java.util.concurrent.Executors.newFixedThreadPool(newSize)
    old.shutdown()
    android.util.Log.i("HermesAgentLoop", "Tool thread pool resized to $newSize workers")
}

/** Python `_extract_reasoning_from_message` — stub. */
private fun _extractReasoningFromMessage(msg: Map<String, Any?>): String {
    @Suppress("UNUSED_VARIABLE") val _reasoningKey = "reasoning"
    @Suppress("UNUSED_VARIABLE") val _reasoningDetailsKey = "reasoning_details"
    return ""
}
