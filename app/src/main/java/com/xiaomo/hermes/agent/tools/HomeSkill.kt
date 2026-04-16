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
 * Home Skill
 * Press Home button to return to main screen
 */
class HomeSkill : Skill {
    companion object {
        private const val TAG = "HomeSkill"
    }

    override val name = "home"
    override val description = "Press Home button to return to launcher"

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

        Log.d(TAG, "Pressing home button")
        return try {
            val success = AccessibilityProxy.pressHome()
            if (!success) {
                return SkillResult.error("Home button press failed")
            }

            // Wait for launcher to load
            kotlinx.coroutines.delay(1000)

            SkillResult.success(
                "Home button pressed (waited 1000ms for launcher)",
                mapOf("wait_time_ms" to 1000)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Home button press failed", e)
            SkillResult.error("Home button press failed: ${e.message}")
        }
    }
}
