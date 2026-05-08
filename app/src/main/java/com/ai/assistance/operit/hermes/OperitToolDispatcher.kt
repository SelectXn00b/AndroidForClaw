package com.ai.assistance.operit.hermes

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.hermes.gateway.GatewayFileLogger
import com.google.gson.Gson
import com.xiaomo.hermes.hermes.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OperitToolDispatcher(context: Context) : ToolDispatcher {
    private val toolHandler = AIToolHandler.getInstance(context)
    private val gson = Gson()

    override suspend fun dispatch(
        toolName: String,
        args: Map<String, Any?>,
        taskId: String,
        userTask: String?
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "dispatch IN: tool=$toolName argKeys=${args.keys} " +
            "taskId=$taskId userTaskLen=${userTask?.length ?: 0}")
        // Log tool call with arguments (truncate large values)
        val argsSummary = args.entries.joinToString(", ") { (k, v) ->
            val vs = v?.toString() ?: "null"
            "$k=${if (vs.length > 300) vs.take(300) + "…[${vs.length}]" else vs}"
        }
        GatewayFileLogger.d(TAG, "  TOOL_CALL: $toolName($argsSummary)")

        val startNs = System.nanoTime()
        val params = args.map { (key, value) ->
            ToolParameter(name = key, value = value?.toString() ?: "")
        }
        val tool = AITool(name = toolName, parameters = params, description = "")
        val result = toolHandler.executeTool(tool)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        var resultText = result.result.toString()
        Log.d(TAG, "dispatch OUT: tool=$toolName success=${result.success} " +
            "resultLen=${resultText.length} errorLen=${result.error?.length ?: 0} ms=$elapsedMs")

        // Truncate excessively large tool results to prevent context overflow.
        // A single tool result > 30K chars can inflate the context to 300K+ tokens,
        // causing the AI to return an empty response (0 output tokens).
        if (resultText.length > MAX_RESULT_CHARS) {
            val originalLen = resultText.length
            resultText = resultText.take(MAX_RESULT_CHARS) +
                "\n\n[... truncated: result was $originalLen chars, showing first $MAX_RESULT_CHARS chars. " +
                "Consider using more specific queries to reduce result size.]"
            Log.w(TAG, "dispatch: truncated $toolName result from $originalLen to ${resultText.length} chars")
            GatewayFileLogger.w(TAG, "  TOOL_RESULT_TRUNCATED: $toolName original=${originalLen} truncatedTo=${resultText.length}")
        }

        // Log tool result (truncate for log preview)
        val resultPreview = if (resultText.length > 500) {
            resultText.take(500) + "…[${resultText.length}]"
        } else resultText
        val errorInfo = if (result.error != null) " error=${result.error}" else ""
        GatewayFileLogger.d(TAG, "  TOOL_RESULT: $toolName success=${result.success} ${elapsedMs}ms$errorInfo result=$resultPreview")

        // For use_package skill results, return the skill instructions directly
        // without JSON wrapping. Skill content starts with "=== SKILL ACTIVATED"
        // and should be presented as-is to maximize the LLM's instruction adherence.
        // JSON wrapping nests instructions inside a string value, reducing their
        // directive authority in the LLM's attention.
        if (toolName == "use_package" && result.success && result.error == null &&
            resultText.startsWith("=== SKILL ACTIVATED")) {
            resultText
        } else {
            gson.toJson(
                mapOf(
                    "success" to result.success,
                    "result" to resultText,
                    "error" to result.error
                )
            )
        }
    }

    companion object {
        private const val TAG = "HermesBridge/Tool"
        /** Maximum characters for a single tool result before truncation.
         *  30K chars ≈ 7.5K-10K tokens — leaves ample room for the rest of
         *  the conversation context. */
        private const val MAX_RESULT_CHARS = 30_000
    }
}
