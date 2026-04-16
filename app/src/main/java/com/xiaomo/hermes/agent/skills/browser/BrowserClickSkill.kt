package com.xiaomo.hermes.agent.skills.browser

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/browser/client-actions-core.ts
 */


import android.content.Context
import com.xiaomo.hermes.agent.tools.Skill
import com.xiaomo.hermes.agent.tools.SkillResult
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition
import com.xiaomo.hermes.browser.BrowserToolClient

/**
 * browser_click - Click element
 */
class BrowserClickSkill(private val context: Context) : Skill {
    override val name = "browser_click"
    override val description = "Click an element in the browser"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = "Click an element in the browser using a CSS selector. Provide 'selector' (CSS selector like '#login-button' or '.submit-btn') and optional 'index' (index when multiple elements match, default: 0). Example: {\"selector\": \"#search-button\", \"index\": 0}",
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "selector" to PropertySchema(
                            "string",
                            "CSS selector for the element"
                        ),
                        "index" to PropertySchema(
                            "integer",
                            "Index when multiple elements match (default: 0)"
                        )
                    ),
                    required = listOf("selector")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val selector = args["selector"] as? String
            ?: return SkillResult.error("Missing required parameter: selector")

        val index = (args["index"] as? Number)?.toInt() ?: 0

        return try {
            val browserClient = BrowserToolClient(context)
            val toolArgs = mapOf(
                "selector" to selector,
                "index" to index
            )
            val result = browserClient.executeToolAsync("browser_click", toolArgs)

            if (result.success) {
                SkillResult.success(
                    "Successfully clicked element: $selector",
                    result.data ?: emptyMap()
                )
            } else {
                SkillResult.error(result.error ?: "Click failed")
            }
        } catch (e: Exception) {
            SkillResult.error("Failed to click: ${e.message}")
        }
    }
}
