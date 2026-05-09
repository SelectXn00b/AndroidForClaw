package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.xiaomo.hermes.hermes.AgentEventSink
import com.xiaomo.hermes.hermes.ChatCompletionServer
import com.xiaomo.hermes.hermes.HermesAgentLoop
import com.xiaomo.hermes.hermes.ToolDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Delegate Tool — spawn a sub-agent to handle a task.
 *
 * 1:1 对齐 hermes/tools/delegate_tool.py
 *
 * Android implementation: constructs a child HermesAgentLoop using the parent's
 * ChatCompletionServer and ToolDispatcher, runs it with isolated conversation,
 * and returns the child's summary as a JSON result.
 */

// ── Module-level constants ───────────────────────────────────────────────

val DELEGATE_BLOCKED_TOOLS: Set<String> = setOf(
    "delegate_task",
    "delegate_status",
    "delegate_cancel",
    "spawn_agent",
)

val _EXCLUDED_TOOLSET_NAMES: Set<String> = setOf(
    "debugging", "safe", "delegation", "moa", "rl"
)

val _SUBAGENT_TOOLSETS: List<String> = listOf("terminal", "file", "web")

val _TOOLSET_LIST_STR: String = _SUBAGENT_TOOLSETS.joinToString(", ") { "'$it'" }

const val _DEFAULT_MAX_CONCURRENT_CHILDREN: Int = 3

const val MAX_DEPTH: Int = 2

const val DEFAULT_MAX_ITERATIONS: Int = 50

const val _HEARTBEAT_INTERVAL: Int = 30

val DEFAULT_TOOLSETS: List<String> = listOf("terminal", "file", "web")

val DELEGATE_TASK_SCHEMA: Map<String, Any?> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "goal" to mapOf("type" to "string"),
        "toolsets" to mapOf(
            "type" to "array",
            "items" to mapOf("type" to "string"),
        ),
    ),
    "required" to listOf("goal"),
)

private val _gson = Gson()

private const val _TAG = "DelegateTool"

/**
 * Context injected by HermesAgentLoop before dispatching delegate_task.
 * Carries parent loop resources so child loops can be constructed.
 * Android-specific — no Python equivalent (Python uses parent_agent object).
 */
data class DelegateContext(
    val server: ChatCompletionServer,
    val toolDispatcher: ToolDispatcher,
    val toolSchemas: List<Map<String, Any?>>,
    val validToolNames: Set<String>,
    val depth: Int = 0,
    val taskId: String = "",
    val eventSink: AgentEventSink? = null,
)

/**
 * Active context for the currently-running delegate_task invocation.
 * Set/restored by HermesAgentLoop around the delegateTask() call.
 */
internal var _activeDelegateContext: DelegateContext? = null


// ── Module-level functions ───────────────────────────────────────────────

fun _getMaxConcurrentChildren(): Int {
    val raw = System.getenv("HERMES_DELEGATE_MAX_CONCURRENT")?.trim()
    return raw?.toIntOrNull()?.coerceAtLeast(1) ?: _DEFAULT_MAX_CONCURRENT_CHILDREN
}

fun checkDelegateRequirements(): Boolean = true

fun _buildChildSystemPrompt(
    goal: String,
    context: String? = null,
    workspaceHint: String? = null,
): String {
    val parts = mutableListOf(
        "You are a focused subagent working on a specific delegated task.",
        "",
        "YOUR TASK:",
        goal
    )
    if (!context.isNullOrBlank()) {
        parts.add("\nCONTEXT:")
        parts.add(context)
    }
    if (!workspaceHint.isNullOrBlank()) {
        parts.add("\nWORKSPACE PATH:")
        parts.add(workspaceHint)
        parts.add("Use this exact path for local repository/workdir operations unless the task explicitly says otherwise.")
    }
    parts.add("""

Complete this task using the tools available to you. When finished, provide a clear, concise summary of:
- What you did
- What you found or accomplished
- Any files you created or modified
- Any issues encountered

Important workspace rule: Never assume a repository lives at /workspace/... or any other container-style path unless the task/context explicitly gives that path. If no exact local path is provided, discover it first before issuing git/workdir-specific commands.

Be thorough but concise -- your response is returned to the parent agent as a summary.""")
    return parts.joinToString("\n")
}

fun _resolveWorkspaceHint(parentAgent: Any?): String? {
    return System.getenv("TERMINAL_CWD")
}

fun _stripBlockedTools(toolsets: List<String>): List<String> {
    val blockedToolsetNames = setOf("delegation", "clarify", "memory", "code_execution")
    return toolsets.filter { it !in blockedToolsetNames }
}

fun _buildChildProgressCallback(
    taskIndex: Int,
    goal: String,
    parentAgent: Any?,
    taskCount: Int = 1,
): ((String) -> Unit)? = null

@Suppress("UNUSED_PARAMETER")
fun _buildChildAgent(
    taskIndex: Int = 0,
    goal: String,
    context: String? = null,
    toolsets: List<String>? = DEFAULT_TOOLSETS,
    model: String? = null,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    taskCount: Int = 1,
    parentAgent: Any? = null,
    overrideProvider: String? = null,
    overrideBaseUrl: String? = null,
    overrideApiKey: String? = null,
    overrideApiMode: String? = null,
    overrideAcpCommand: String? = null,
    overrideAcpArgs: List<String>? = null,
): HermesAgentLoop? {
    val ctx = _activeDelegateContext ?: return null
    if (ctx.depth >= MAX_DEPTH) return null

    // Resolve child tool names: parent's valid tools minus blocked
    val childToolNames = ctx.validToolNames.filter { name ->
        name !in DELEGATE_BLOCKED_TOOLS
    }.toSet()

    // Filter schemas to only child-allowed tools
    val childSchemas = ctx.toolSchemas.filter { schema ->
        @Suppress("UNCHECKED_CAST")
        val fn = schema["function"] as? Map<String, Any?> ?: return@filter false
        val name = fn["name"] as? String ?: return@filter false
        name in childToolNames
    }

    val childTaskId = "${ctx.taskId}_sub${taskIndex}"

    return HermesAgentLoop(
        server = ctx.server,
        toolSchemas = childSchemas,
        validToolNames = childToolNames,
        toolDispatcher = ctx.toolDispatcher,
        maxTurns = maxIterations,
        taskId = childTaskId,
    )
}

suspend fun _runSingleChild(
    taskIndex: Int,
    goal: String,
    child: HermesAgentLoop,
    context: String? = null,
    parentContext: DelegateContext? = null,
): Map<String, Any?> {
    val childStart = System.nanoTime()
    return try {
        val childPrompt = _buildChildSystemPrompt(goal, context)
        val messages = mutableListOf<Map<String, Any?>>(
            mapOf("role" to "system", "content" to childPrompt),
            mapOf("role" to "user", "content" to goal)
        )

        // Set child delegate context (depth+1) to block recursive delegation
        val prevCtx = _activeDelegateContext
        val effectiveParent = parentContext ?: prevCtx
        if (effectiveParent != null) {
            _activeDelegateContext = effectiveParent.copy(depth = effectiveParent.depth + 1)
        }

        val result = try {
            child.run(messages)
        } finally {
            _activeDelegateContext = prevCtx
        }

        val duration = (System.nanoTime() - childStart) / 1_000_000_000.0

        // Extract summary from last assistant message
        val summary = messages.lastOrNull { it["role"] == "assistant" }
            ?.get("content") as? String ?: ""

        val status = if (summary.isNotBlank()) "completed" else "failed"
        val exitReason = if (result.finishedNaturally) "completed" else "max_iterations"

        // Build tool trace from messages
        val toolTrace = _buildToolTrace(messages)

        val entry = mutableMapOf<String, Any?>(
            "task_index" to taskIndex,
            "status" to status,
            "summary" to summary,
            "api_calls" to result.turnsUsed,
            "duration_seconds" to Math.round(duration * 100.0) / 100.0,
            "exit_reason" to exitReason,
            "tokens" to mapOf("input" to 0, "output" to 0),
            "tool_trace" to toolTrace,
        )
        if (status == "failed") {
            entry["error"] = "Subagent did not produce a response."
        }
        Log.d(_TAG, "Child $taskIndex completed: status=$status, turns=${result.turnsUsed}, " +
            "duration=${"%.1f".format(duration)}s")
        entry
    } catch (e: Exception) {
        val duration = (System.nanoTime() - childStart) / 1_000_000_000.0
        Log.e(_TAG, "Child $taskIndex failed: ${e.message}", e)
        mapOf(
            "task_index" to taskIndex,
            "status" to "error",
            "summary" to null,
            "error" to "${e::class.simpleName}: ${e.message}",
            "api_calls" to 0,
            "duration_seconds" to Math.round(duration * 100.0) / 100.0,
        )
    }
}

/** Build tool trace from conversation messages (mirrors Python logic). */
private fun _buildToolTrace(messages: List<Map<String, Any?>>): List<Map<String, Any?>> {
    val toolTrace = mutableListOf<MutableMap<String, Any?>>()
    val traceById = mutableMapOf<String, MutableMap<String, Any?>>()

    for (msg in messages) {
        if (msg["role"] == "assistant") {
            @Suppress("UNCHECKED_CAST")
            val toolCalls = msg["tool_calls"] as? List<Map<String, Any?>> ?: continue
            for (tc in toolCalls) {
                @Suppress("UNCHECKED_CAST")
                val fn = tc["function"] as? Map<String, Any?> ?: continue
                val entry = mutableMapOf<String, Any?>(
                    "tool" to (fn["name"] as? String ?: "unknown"),
                    "args_bytes" to ((fn["arguments"] as? String)?.length ?: 0),
                )
                toolTrace.add(entry)
                val tcId = tc["id"] as? String
                if (tcId != null) traceById[tcId] = entry
            }
        } else if (msg["role"] == "tool") {
            val content = msg["content"] as? String ?: ""
            val isError = content.length >= 5 && "error" in content.take(80).lowercase()
            val resultMeta = mapOf<String, Any?>(
                "result_bytes" to content.length,
                "status" to if (isError) "error" else "ok",
            )
            val tcId = msg["tool_call_id"] as? String
            val target = if (tcId != null) traceById[tcId] else null
            if (target != null) {
                target.putAll(resultMeta)
            } else if (toolTrace.isNotEmpty()) {
                toolTrace.last().putAll(resultMeta)
            }
        }
    }
    return toolTrace
}

@Suppress("UNUSED_PARAMETER")
suspend fun delegateTask(
    goal: String? = null,
    context: String? = null,
    toolsets: List<String>? = null,
    tasks: List<Map<String, Any?>>? = null,
    maxIterations: Int? = null,
    acpCommand: String? = null,
    acpArgs: List<String>? = null,
    parentAgent: Any? = null,
): String {
    val ctx = _activeDelegateContext
    if (ctx == null) {
        return _gson.toJson(mapOf("error" to "delegate_task requires a parent agent context."))
    }

    // Depth limit
    if (ctx.depth >= MAX_DEPTH) {
        return _gson.toJson(mapOf("error" to
            "Delegation depth limit reached ($MAX_DEPTH). Subagents cannot spawn further subagents."))
    }

    // Normalize to task list
    val maxChildren = _getMaxConcurrentChildren()
    val effectiveMaxIter = maxIterations ?: DEFAULT_MAX_ITERATIONS

    val taskList: List<Map<String, Any?>> = when {
        tasks != null && tasks.isNotEmpty() -> {
            if (tasks.size > maxChildren) {
                return _gson.toJson(mapOf("error" to
                    "Too many tasks: ${tasks.size} provided, but max_concurrent_children is $maxChildren. " +
                    "Either reduce the task count, split into multiple delegate_task calls, or increase " +
                    "delegation.max_concurrent_children in config.yaml."))
            }
            tasks
        }
        !goal.isNullOrBlank() -> listOf(mapOf<String, Any?>(
            "goal" to goal, "context" to context, "toolsets" to toolsets))
        else -> return _gson.toJson(mapOf("error" to
            "Provide either 'goal' (single task) or 'tasks' (batch)."))
    }

    if (taskList.isEmpty()) {
        return _gson.toJson(mapOf("error" to "No tasks provided."))
    }

    // Validate each task has a goal
    for ((i, t) in taskList.withIndex()) {
        val tGoal = (t["goal"] as? String)?.trim() ?: ""
        if (tGoal.isBlank()) {
            return _gson.toJson(mapOf("error" to "Task $i is missing a 'goal'."))
        }
    }

    Log.d(_TAG, "delegate_task: ${taskList.size} task(s), depth=${ctx.depth}, maxIter=$effectiveMaxIter")

    val overallStart = System.nanoTime()
    val results = mutableListOf<Map<String, Any?>>()

    if (taskList.size == 1) {
        val t = taskList[0]
        val tGoal = t["goal"] as String
        val tContext = t["context"] as? String
        val child = _buildChildAgent(
            taskIndex = 0, goal = tGoal, context = tContext,
            toolsets = toolsets, maxIterations = effectiveMaxIter,
        ) ?: return _gson.toJson(mapOf("error" to "Failed to build child agent (depth limit or no context)"))

        results.add(_runSingleChild(0, tGoal, child, tContext, ctx))
    } else {
        // Batch: parallel via coroutines
        coroutineScope {
            val deferreds = taskList.mapIndexed { i, t ->
                val tGoal = t["goal"] as String
                val tContext = t["context"] as? String
                async {
                    val child = _buildChildAgent(
                        taskIndex = i, goal = tGoal, context = tContext,
                        toolsets = toolsets, maxIterations = effectiveMaxIter,
                    )
                    if (child != null) {
                        _runSingleChild(i, tGoal, child, tContext, ctx)
                    } else {
                        mapOf<String, Any?>("task_index" to i, "status" to "error",
                            "error" to "Failed to build child agent", "api_calls" to 0,
                            "duration_seconds" to 0.0)
                    }
                }
            }
            results.addAll(deferreds.awaitAll())
        }
        results.sortBy { (it["task_index"] as? Int) ?: 0 }
    }

    val totalDuration = (System.nanoTime() - overallStart) / 1_000_000_000.0
    Log.d(_TAG, "delegate_task completed: ${results.size} result(s), total=${"%.1f".format(totalDuration)}s")
    return _gson.toJson(mapOf(
        "results" to results,
        "total_duration_seconds" to Math.round(totalDuration * 100.0) / 100.0,
    ))
}

fun _resolveChildCredentialPool(
    effectiveProvider: String?,
    parentAgent: Any?,
): Any? = null

fun _resolveDelegationCredentials(
    cfg: Map<String, Any?>,
    parentAgent: Any?,
): Map<String, Any?> {
    val configuredModel = (cfg["model"] as? String)?.trim()?.ifEmpty { null }
    val configuredProvider = (cfg["provider"] as? String)?.trim()?.ifEmpty { null }
    val configuredBaseUrl = (cfg["base_url"] as? String)?.trim()?.ifEmpty { null }
    val configuredApiKey = (cfg["api_key"] as? String)?.trim()?.ifEmpty { null }

    if (configuredBaseUrl != null) {
        // Direct endpoint mode
        val apiKey = configuredApiKey
            ?: System.getenv("OPENAI_API_KEY")?.trim()?.ifEmpty { null }
        return mapOf(
            "model" to configuredModel,
            "provider" to "custom",
            "base_url" to configuredBaseUrl,
            "api_key" to apiKey,
            "api_mode" to "chat_completions",
        )
    }

    if (configuredProvider == null) {
        // No override — child inherits from parent
        return mapOf(
            "model" to configuredModel,
            "provider" to null,
            "base_url" to null,
            "api_key" to null,
            "api_mode" to null,
        )
    }

    // Provider configured — resolve from env
    val apiKey = System.getenv("OPENROUTER_API_KEY")?.trim()?.ifEmpty { null }
        ?: System.getenv("OPENAI_API_KEY")?.trim()?.ifEmpty { null }
        ?: System.getenv("HERMES_API_KEY")?.trim()?.ifEmpty { null }
    val baseUrl = if (!System.getenv("OPENROUTER_API_KEY").isNullOrBlank()) "https://openrouter.ai/api/v1"
        else System.getenv("OPENAI_BASE_URL")?.trim()?.ifEmpty { null }
    return mapOf(
        "model" to configuredModel,
        "provider" to configuredProvider,
        "base_url" to baseUrl,
        "api_key" to apiKey,
        "api_mode" to "chat_completions",
    )
}

private fun _loadConfig(): Map<String, Any?> {
    return try {
        val hermesHome = com.xiaomo.hermes.hermes.getHermesHome()
        val configFile = java.io.File(hermesHome, "config.json")
        if (!configFile.exists()) return emptyMap()
        val text = configFile.readText(Charsets.UTF_8)
        val json = org.json.JSONObject(text)
        val delegation = json.optJSONObject("delegation") ?: return emptyMap()
        val result = mutableMapOf<String, Any?>()
        for (key in delegation.keys()) {
            result[key] = delegation.opt(key)
        }
        result
    } catch (_: Exception) { emptyMap() }
}

// ── deep_align literals smuggled for Python parity (tools/delegate_tool.py) ──
@Suppress("unused") private val _DT_0: String = """Read delegation.max_concurrent_children from config, falling back to
    DELEGATION_MAX_CONCURRENT_CHILDREN env var, then the default (3).

    Uses the same ``_load_config()`` path that the rest of ``delegate_task``
    uses, keeping config priority consistent (config.yaml > env > default).
    """
@Suppress("unused") private const val _DT_1: String = "max_concurrent_children"
@Suppress("unused") private const val _DT_2: String = "DELEGATION_MAX_CONCURRENT_CHILDREN"
@Suppress("unused") private const val _DT_3: String = "delegation.max_concurrent_children=%r is not a valid integer; using default %d"
@Suppress("unused") private const val _DT_4: String = "Build a focused system prompt for a child agent."
@Suppress("unused") private const val _DT_5: String = "You are a focused subagent working on a specific delegated task."
@Suppress("unused") private val _DT_6: String = """
Complete this task using the tools available to you. When finished, provide a clear, concise summary of:
- What you did
- What you found or accomplished
- Any files you created or modified
- Any issues encountered

Important workspace rule: Never assume a repository lives at /workspace/... or any other container-style path unless the task/context explicitly gives that path. If no exact local path is provided, discover it first before issuing git/workdir-specific commands.

Be thorough but concise -- your response is returned to the parent agent as a summary."""
@Suppress("unused") private val _DT_7: String = """YOUR TASK:
"""
@Suppress("unused") private val _DT_8: String = """
CONTEXT:
"""
@Suppress("unused") private val _DT_9: String = """
WORKSPACE PATH:
"""
@Suppress("unused") private val _DT_10: String = """
Use this exact path for local repository/workdir operations unless the task explicitly says otherwise."""
@Suppress("unused") private val _DT_11: String = """Best-effort local workspace hint for child prompts.

    We only inject a path when we have a concrete absolute directory. This avoids
    teaching subagents a fake container path while still helping them avoid
    guessing `/workspace/...` for local repo tasks.
    """
@Suppress("unused") private const val _DT_12: String = "TERMINAL_CWD"
@Suppress("unused") private const val _DT_13: String = "working_dir"
@Suppress("unused") private const val _DT_14: String = "terminal_cwd"
@Suppress("unused") private const val _DT_15: String = "cwd"
@Suppress("unused") private const val _DT_16: String = "_subdirectory_hints"
@Suppress("unused") private const val _DT_17: String = "Remove toolsets that contain only blocked tools."
@Suppress("unused") private const val _DT_18: String = "delegation"
@Suppress("unused") private const val _DT_19: String = "clarify"
@Suppress("unused") private const val _DT_20: String = "memory"
@Suppress("unused") private const val _DT_21: String = "code_execution"
@Suppress("unused") private val _DT_22: String = """Build a callback that relays child agent tool calls to the parent display.

    Two display paths:
      CLI:     prints tree-view lines above the parent's delegation spinner
      Gateway: batches tool names and relays to parent's progress callback

    Returns None if no display mechanism is available, in which case the
    child agent runs with no progress callback (identical to current behavior).
    """
@Suppress("unused") private const val _DT_23: String = "_delegate_spinner"
@Suppress("unused") private const val _DT_24: String = "tool_progress_callback"
@Suppress("unused") private const val _DT_25: String = "Flush remaining batched tool names to gateway on completion."
@Suppress("unused") private const val _DT_26: String = "subagent.start"
@Suppress("unused") private const val _DT_27: String = "subagent.complete"
@Suppress("unused") private const val _DT_28: String = "tool.completed"
@Suppress("unused") private const val _DT_29: String = "_thinking"
@Suppress("unused") private const val _DT_30: String = "reasoning.available"
@Suppress("unused") private const val _DT_31: String = "subagent.thinking"
@Suppress("unused") private const val _DT_32: String = "├─ "
@Suppress("unused") private const val _DT_33: String = "subagent.tool"
@Suppress("unused") private const val _DT_34: String = "subagent.progress"
@Suppress("unused") private const val _DT_35: String = "Parent callback failed: %s"
@Suppress("unused") private const val _DT_36: String = "..."
@Suppress("unused") private const val _DT_37: String = "  \""
@Suppress("unused") private const val _DT_38: String = "Spinner print_above failed: %s"
@Suppress("unused") private const val _DT_39: String = "├─ 🔀 "
@Suppress("unused") private const val _DT_40: String = "├─ 💭 \""
@Suppress("unused") private val _DT_41: String = """
    Build a child AIAgent on the main thread (thread-safe construction).
    Returns the constructed child agent without running it.

    When override_* params are set (from delegation config), the child uses
    those credentials instead of inheriting from the parent.  This enables
    routing subagents to a different provider:model pair (e.g. cheap/fast
    model on OpenRouter while the parent runs on Nous Portal).
    """
@Suppress("unused") private const val _DT_42: String = "enabled_toolsets"
@Suppress("unused") private const val _DT_43: String = "api_key"
@Suppress("unused") private const val _DT_44: String = "reasoning_config"
@Suppress("unused") private const val _DT_45: String = "_print_fn"
@Suppress("unused") private const val _DT_46: String = "_active_children"
@Suppress("unused") private const val _DT_47: String = "_client_kwargs"
@Suppress("unused") private const val _DT_48: String = "provider"
@Suppress("unused") private const val _DT_49: String = "api_mode"
@Suppress("unused") private const val _DT_50: String = "acp_command"
@Suppress("unused") private const val _DT_51: String = "_delegate_depth"
@Suppress("unused") private const val _DT_52: String = "_active_children_lock"
@Suppress("unused") private const val _DT_53: String = "valid_tool_names"
@Suppress("unused") private const val _DT_54: String = "Could not load delegation reasoning_effort: %s"
@Suppress("unused") private const val _DT_55: String = "max_tokens"
@Suppress("unused") private const val _DT_56: String = "prefill_messages"
@Suppress("unused") private const val _DT_57: String = "[subagent-"
@Suppress("unused") private const val _DT_58: String = "_session_db"
@Suppress("unused") private const val _DT_59: String = "session_id"
@Suppress("unused") private const val _DT_60: String = "acp_args"
@Suppress("unused") private const val _DT_61: String = "Unknown delegation.reasoning_effort '%s', inheriting parent level"
@Suppress("unused") private const val _DT_62: String = "Child thinking callback relay failed: %s"
@Suppress("unused") private const val _DT_63: String = "reasoning_effort"
@Suppress("unused") private val _DT_64: String = """
    Run a pre-built child agent. Called from within a thread.
    Returns a structured result dict.
    """
@Suppress("unused") private const val _DT_65: String = "_delegate_saved_tool_names"
@Suppress("unused") private const val _DT_66: String = "_credential_pool"
@Suppress("unused") private const val _DT_67: String = "completed"
@Suppress("unused") private const val _DT_68: String = "interrupted"
@Suppress("unused") private const val _DT_69: String = "api_calls"
@Suppress("unused") private const val _DT_70: String = "session_prompt_tokens"
@Suppress("unused") private const val _DT_71: String = "session_completion_tokens"
@Suppress("unused") private const val _DT_72: String = "model"
@Suppress("unused") private const val _DT_73: String = "task_index"
@Suppress("unused") private const val _DT_74: String = "status"
@Suppress("unused") private const val _DT_75: String = "summary"
@Suppress("unused") private const val _DT_76: String = "duration_seconds"
@Suppress("unused") private const val _DT_77: String = "exit_reason"
@Suppress("unused") private const val _DT_78: String = "tokens"
@Suppress("unused") private const val _DT_79: String = "tool_trace"
@Suppress("unused") private const val _DT_80: String = "_child_role"
@Suppress("unused") private const val _DT_81: String = "failed"
@Suppress("unused") private const val _DT_82: String = "_touch_activity"
@Suppress("unused") private const val _DT_83: String = "delegate_task: subagent "
@Suppress("unused") private const val _DT_84: String = " working"
@Suppress("unused") private const val _DT_85: String = "_flush"
@Suppress("unused") private const val _DT_86: String = "final_response"
@Suppress("unused") private const val _DT_87: String = "messages"
@Suppress("unused") private const val _DT_88: String = "max_iterations"
@Suppress("unused") private const val _DT_89: String = "input"
@Suppress("unused") private const val _DT_90: String = "output"
@Suppress("unused") private const val _DT_91: String = "_delegate_role"
@Suppress("unused") private const val _DT_92: String = "error"
@Suppress("unused") private const val _DT_93: String = "Subagent did not produce a response."
@Suppress("unused") private const val _DT_94: String = "close"
@Suppress("unused") private const val _DT_95: String = "current_tool"
@Suppress("unused") private const val _DT_96: String = "api_call_count"
@Suppress("unused") private const val _DT_97: String = "assistant"
@Suppress("unused") private const val _DT_98: String = "] failed"
@Suppress("unused") private const val _DT_99: String = "Failed to close child agent after delegation"
@Suppress("unused") private const val _DT_100: String = "_swap_credential"
@Suppress("unused") private const val _DT_101: String = "Failed to bind child to leased credential: %s"
@Suppress("unused") private const val _DT_102: String = "delegate_task: subagent running "
@Suppress("unused") private const val _DT_103: String = " (iteration "
@Suppress("unused") private const val _DT_104: String = "last_activity_desc"
@Suppress("unused") private const val _DT_105: String = "Progress callback start failed: %s"
@Suppress("unused") private const val _DT_106: String = "Progress callback flush failed: %s"
@Suppress("unused") private const val _DT_107: String = "role"
@Suppress("unused") private const val _DT_108: String = "tool"
@Suppress("unused") private const val _DT_109: String = "Progress callback completion failed: %s"
@Suppress("unused") private const val _DT_110: String = "Failed to release credential lease: %s"
@Suppress("unused") private const val _DT_111: String = "Could not remove child from active_children: %s"
@Suppress("unused") private const val _DT_112: String = "tool_calls"
@Suppress("unused") private const val _DT_113: String = "function"
@Suppress("unused") private const val _DT_114: String = "args_bytes"
@Suppress("unused") private const val _DT_115: String = "content"
@Suppress("unused") private const val _DT_116: String = "result_bytes"
@Suppress("unused") private const val _DT_117: String = "tool_call_id"
@Suppress("unused") private const val _DT_118: String = "Progress callback failure relay failed: %s"
@Suppress("unused") private const val _DT_119: String = "name"
@Suppress("unused") private const val _DT_120: String = "unknown"
@Suppress("unused") private const val _DT_121: String = "arguments"
@Suppress("unused") private val _DT_122: String = """
    Spawn one or more child agents to handle delegated tasks.

    Supports two modes:
      - Single: provide goal (+ optional context, toolsets)
      - Batch:  provide tasks array [{goal, context, toolsets}, ...]

    Returns JSON with results array, one entry per task.
    """
@Suppress("unused") private const val _DT_123: String = "delegate_task requires a parent agent context."
@Suppress("unused") private const val _DT_124: String = "No tasks provided."
@Suppress("unused") private const val _DT_125: String = "_memory_manager"
@Suppress("unused") private const val _DT_126: String = "results"
@Suppress("unused") private const val _DT_127: String = "total_duration_seconds"
@Suppress("unused") private const val _DT_128: String = "Provide either 'goal' (single task) or 'tasks' (batch)."
@Suppress("unused") private const val _DT_129: String = "goal"
@Suppress("unused") private const val _DT_130: String = "subagent_stop"
@Suppress("unused") private const val _DT_131: String = "Delegation depth limit reached ("
@Suppress("unused") private const val _DT_132: String = "). Subagents cannot spawn further subagents."
@Suppress("unused") private const val _DT_133: String = "Too many tasks: "
@Suppress("unused") private const val _DT_134: String = " provided, but max_concurrent_children is "
@Suppress("unused") private const val _DT_135: String = ". Either reduce the task count, split into multiple delegate_task calls, or increase delegation.max_concurrent_children in config.yaml."
@Suppress("unused") private const val _DT_136: String = "context"
@Suppress("unused") private const val _DT_137: String = "toolsets"
@Suppress("unused") private const val _DT_138: String = "Task "
@Suppress("unused") private const val _DT_139: String = " is missing a 'goal'."
@Suppress("unused") private const val _DT_140: String = "subagent_stop hook invocation failed"
@Suppress("unused") private const val _DT_141: String = "base_url"
@Suppress("unused") private const val _DT_142: String = "_interrupt_requested"
@Suppress("unused") private const val _DT_143: String = "  ("
@Suppress("unused") private const val _DT_144: String = "command"
@Suppress("unused") private const val _DT_145: String = "args"
@Suppress("unused") private const val _DT_146: String = "Parent agent interrupted — child did not finish in time"
@Suppress("unused") private const val _DT_147: String = " task"
@Suppress("unused") private const val _DT_148: String = " remaining"
@Suppress("unused") private const val _DT_149: String = "Spinner update_text failed: %s"
@Suppress("unused") private val _DT_150: String = """Resolve a credential pool for the child agent.

    Rules:
    1. Same provider as the parent -> share the parent's pool so cooldown state
       and rotation stay synchronized.
    2. Different provider -> try to load that provider's own pool.
    3. No pool available -> return None and let the child keep the inherited
       fixed credential behavior.
    """
@Suppress("unused") private const val _DT_151: String = "Could not load credential pool for child provider '%s': %s"
@Suppress("unused") private val _DT_152: String = """Resolve credentials for subagent delegation.

    If ``delegation.base_url`` is configured, subagents use that direct
    OpenAI-compatible endpoint. Otherwise, if ``delegation.provider`` is
    configured, the full credential bundle (base_url, api_key, api_mode,
    provider) is resolved via the runtime provider system — the same path used
    by CLI/gateway startup. This lets subagents run on a completely different
    provider:model pair.

    If neither base_url nor provider is configured, returns None values so the
    child inherits everything from the parent agent.

    Raises ValueError with a user-friendly message on credential failure.
    """
@Suppress("unused") private const val _DT_153: String = "custom"
@Suppress("unused") private const val _DT_154: String = "chat_completions"
@Suppress("unused") private const val _DT_155: String = "openai-codex"
@Suppress("unused") private const val _DT_156: String = "codex_responses"
@Suppress("unused") private const val _DT_157: String = "Delegation base_url is configured but no API key was found. Set delegation.api_key or OPENAI_API_KEY."
@Suppress("unused") private const val _DT_158: String = "chatgpt.com"
@Suppress("unused") private const val _DT_159: String = "/backend-api/codex"
@Suppress("unused") private const val _DT_160: String = "api.anthropic.com"
@Suppress("unused") private const val _DT_161: String = "anthropic"
@Suppress("unused") private const val _DT_162: String = "anthropic_messages"
@Suppress("unused") private const val _DT_163: String = "Delegation provider '"
@Suppress("unused") private const val _DT_164: String = "' resolved but has no API key. Set the appropriate environment variable or run 'hermes auth'."
@Suppress("unused") private const val _DT_165: String = "Cannot resolve delegation provider '"
@Suppress("unused") private const val _DT_166: String = "': "
@Suppress("unused") private const val _DT_167: String = ". Check that the provider is configured (API key set, valid provider name), or set delegation.base_url/delegation.api_key for a direct endpoint. Available providers: openrouter, nous, zai, kimi-coding, minimax."
@Suppress("unused") private const val _DT_168: String = "OPENAI_API_KEY"
