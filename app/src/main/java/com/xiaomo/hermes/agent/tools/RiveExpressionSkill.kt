package com.xiaomo.hermes.agent.tools

import android.content.Context
import ai.openclaw.app.rive.RiveStateHolder
import com.xiaomo.hermes.config.ConfigLoader
import com.xiaomo.hermes.config.RiveConfig
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * Rive Expression Skill — AI 通过 tool call 控制 Rive 机器人表情。
 * 替代之前的文本标签 [rive:TAG] 方案，实现纯 Skill 解耦。
 */
class RiveExpressionSkill(private val context: Context) : Skill {

    companion object {
        private const val TAG = "RiveExpressionSkill"

        private val DEFAULT_EMOTION_MAP = mapOf(
            "happy" to 1f, "smile" to 1f, "excited" to 2f,
            "sad" to 3f, "scared" to 4f, "angry" to 4f,
            "surprised" to 5f, "thinking" to 0f, "neutral" to 0f,
            "sleepy" to 0f, "idle" to 0f,
        )
    }

    override val name = "rive_expression"
    override val description = "Set the Rive robot avatar's facial expression on the user's screen"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "emotion" to PropertySchema(
                            "string",
                            "Named emotion: happy, smile, excited, sad, scared, angry, surprised, thinking, neutral, sleepy, idle"
                        ),
                        "expressions" to PropertySchema(
                            "number",
                            "Direct Expressions value (0=idle, 1=smile, 2=super happy, 3=sad, 4=scared, 5=surprised). Overrides emotion if both provided."
                        ),
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val emotion = (args["emotion"] as? String)?.lowercase()
        val directValue = (args["expressions"] as? Number)?.toFloat()

        val emotionMap = try {
            val config = ConfigLoader.getInstance().loadOpenClawConfig()
            config.rive.emotionMap.ifEmpty { DEFAULT_EMOTION_MAP }
        } catch (_: Exception) {
            DEFAULT_EMOTION_MAP
        }

        val exprValue = directValue
            ?: emotion?.let { emotionMap[it] }

        if (exprValue == null) {
            return SkillResult.error("No valid emotion or expressions value provided. Use emotion name (e.g. happy) or expressions number (0-5).")
        }

        RiveStateHolder.setNumberInput("Expressions", exprValue)
        val label = emotion ?: "expressions=$exprValue"
        Log.d(TAG, "Set expression: $label -> Expressions=$exprValue")

        return SkillResult.success(
            "Expression set to $label (Expressions=$exprValue)",
            mapOf("emotion" to emotion, "expressions" to exprValue)
        )
    }
}
