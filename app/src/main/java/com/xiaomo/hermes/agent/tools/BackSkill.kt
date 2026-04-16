package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.accessibility.AccessibilityProxy
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.ToolDefinition

/**
 * Back Skill
 * Press back button
 */
class BackSkill : Skill {
    companion object {
        private const val TAG = "BackSkill"
    }

    override val name = "back"
    override val description = "Press Back button to go to previous screen"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        if (!AccessibilityProxy.isConnected.value!!) {
            return SkillResult.error("Accessibility service not connected")
        }

        Log.d(TAG, "Pressing back button")
        return try {
            val success = AccessibilityProxy.pressBack()
            if (!success) {
                return SkillResult.error("Back button press failed")
            }

            // Wait for page return animation
            kotlinx.coroutines.delay(1000)

            SkillResult.success(
                "Back button pressed (waited 1000ms for transition)",
                mapOf("wait_time_ms" to 1000)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Back button press failed", e)
            SkillResult.error("Back button press failed: ${e.message}")
        }
    }
}
