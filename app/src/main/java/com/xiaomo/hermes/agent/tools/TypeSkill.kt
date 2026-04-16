package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import android.content.Context
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.DeviceController
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * Type Skill
 * Type text into the currently focused input field
 */
class TypeSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "TypeSkill"
    }

    override val name = "type"
    override val description: String
        get() {
            val isAccessibilityEnabled = com.xiaomo.hermes.accessibility.AccessibilityProxy.isConnected.value == true &&
                                        com.xiaomo.hermes.accessibility.AccessibilityProxy.isServiceReady()
            val statusNote = if (!isAccessibilityEnabled) " ⚠️ **不可用**-无障碍服务未连接" else " ✅"
            return "Type text into focused input field (must tap input first)$statusNote"
        }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "text" to PropertySchema("string", "要输入的文本内容")
                    ),
                    required = listOf("text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val text = args["text"] as? String

        if (text == null) {
            return SkillResult.error("Missing required parameter: text")
        }

        Log.d(TAG, "Typing text: $text")
        return try {
            // Type text
            DeviceController.inputText(text, context)

            // Wait for input completion + IME response
            val waitTime = (100L + (text.length * 5L).coerceAtMost(300L)).coerceAtLeast(1000L)
            kotlinx.coroutines.delay(waitTime)

            SkillResult.success(
                "Typed: $text (${text.length} chars)",
                mapOf(
                    "text" to text,
                    "length" to text.length,
                    "wait_time_ms" to waitTime
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Type failed", e)
            SkillResult.error("Type failed: ${e.message}")
        }
    }
}
