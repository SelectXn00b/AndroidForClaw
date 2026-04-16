package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition
import kotlinx.coroutines.delay

/**
 * Wait Skill
 * Wait for specified duration
 */
class WaitSkill : Skill {
    companion object {
        private const val TAG = "WaitSkill"
    }

    override val name = "wait"
    override val description = "Wait for specified duration in seconds"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "seconds" to PropertySchema("number", "等待的秒数")
                    ),
                    required = listOf("seconds")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val seconds = (args["seconds"] as? Number)?.toDouble()

        if (seconds == null) {
            return SkillResult.error("Missing required parameter: seconds")
        }

        val milliseconds = (seconds * 1000).toLong()
        Log.d(TAG, "Waiting for $seconds seconds")
        return try {
            delay(milliseconds)
            SkillResult.success("Waited for $seconds seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Wait failed", e)
            SkillResult.error("Wait failed: ${e.message}")
        }
    }
}
