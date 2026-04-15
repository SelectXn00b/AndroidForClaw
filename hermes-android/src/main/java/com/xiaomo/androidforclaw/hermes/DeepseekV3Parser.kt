package com.xiaomo.androidforclaw.hermes

import android.util.Log
import com.xiaomo.androidforclaw.hermes.environments.ParsedToolCall
import com.xiaomo.androidforclaw.hermes.environments.ToolCallParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * DeepSeek V3 tool call parser
 * 1:1 对齐 hermes-agent/environments/tool_call_parsers/deepseek_v3_parser.py
 *
 * Format uses special unicode tokens:
 *     <｜tool▁calls▁begin｜>
 *     <｜tool▁call▁begin｜>type<｜tool▁sep｜>function_name
 *     ```json
 *     {"arg": "value"}
 *     ```
 *     <｜tool▁call▁end｜>
 *     <｜tool▁calls▁end｜>
 */
class DeepSeekV3ToolCallParser : ToolCallParser() {

    companion object {
        private const val TAG = "DeepSeekV3Parser"
        const val START_TOKEN = "<｜tool▁calls▁begin｜>"
        private val PATTERN = Pattern.compile(
            """<｜tool▁call▁begin｜>(?P<type>.*?)<｜tool▁sep｜>(?P<function_name>.*?)\s*```json\s*(?P<function_arguments>.*?)\s*```\s*<｜tool▁call▁end｜>""",
            Pattern.DOTALL
        )
    }

    override val supportedModels: List<String> = listOf("deepseek_v3")

    override fun parseToolCalls(response: String): List<ParsedToolCall> {
        if (START_TOKEN !in response) return emptyList()

        return try {
            val matcher = PATTERN.matcher(response)
            val toolCalls = mutableListOf<ParsedToolCall>()

            while (matcher.find()) {
                val funcName = matcher.group("function_name")?.trim() ?: continue
                val funcArgs = matcher.group("function_arguments")?.trim() ?: "{}"
                toolCalls.add(
                    ParsedToolCall(
                        id = "call_${UUID.randomUUID().toString().take(8)}",
                        name = funcName,
                        arguments = try {
                            JSONObject(funcArgs).let { obj ->
                                val map = mutableMapOf<String, Any>()
                                obj.keys().forEach { key -> map[key] = obj.get(key) }
                                map
                            }
                        } catch (e: Exception) { emptyMap() },
                        rawArguments = funcArgs
                    )
                )
            }
            toolCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DeepSeek V3 tool calls", e)
            emptyList()
        }
    }

    override fun formatToolCalls(toolCalls: List<ParsedToolCall>): String {
        val sb = StringBuilder()
        sb.append(START_TOKEN)
        for (tc in toolCalls) {
            sb.append("<｜tool▁call▁begin｜>function<｜tool▁sep｜>${tc.name}\n")
            sb.append("```json\n")
            sb.append(tc.rawArguments ?: JSONObject(tc.arguments as Map<*, *>).toString())
            sb.append("\n```\n")
            sb.append("<｜tool▁call▁end｜>")
        }
        sb.append("<｜tool▁calls▁end｜>")
        return sb.toString()
    }

    override fun hasToolCall(response: String): Boolean = START_TOKEN in response

    /** Parses the input text and extracts all available tool calls. */
    fun parse(text: String): Any? {
        throw NotImplementedError("parse")
    }

}
