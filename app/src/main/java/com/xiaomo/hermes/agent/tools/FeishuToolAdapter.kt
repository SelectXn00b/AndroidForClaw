package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import com.xiaomo.hermes.logging.Log

/**
 * Adapter: Bridges Feishu extension tools into the main ToolRegistry
 *
 * Problem: FeishuToolBase (extensions/feishu) and Tool (app) have similar interfaces
 * but different type systems (different ToolDefinition, ToolResult classes).
 * This adapter converts between them so feishu tools (doc, wiki, drive, bitable, etc.)
 * are available to the AgentLoop.
 *
 * Aligned with OpenClaw: extension tools are automatically registered when the channel starts.
 */
class FeishuToolAdapter(
    private val feishuTool: com.xiaomo.feishu.tools.FeishuToolBase
) : Tool {

    companion object {
        private const val TAG = "FeishuToolAdapter"
    }

    override val name: String = feishuTool.name

    override val description: String = feishuTool.description

    override fun getToolDefinition(): com.xiaomo.hermes.providers.ToolDefinition {
        val feishuDef = feishuTool.getToolDefinition()
        return com.xiaomo.hermes.providers.ToolDefinition(
            type = feishuDef.type,
            function = com.xiaomo.hermes.providers.FunctionDefinition(
                name = feishuDef.function.name,
                description = feishuDef.function.description,
                parameters = convertParametersSchema(feishuDef.function.parameters)
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        return try {
            val feishuResult = feishuTool.execute(args)

            if (feishuResult.success) {
                SkillResult.success(
                    content = feishuResult.data?.toString() ?: "OK",
                    metadata = feishuResult.metadata.mapValues { it.value as Any? }
                )
            } else {
                val meta = feishuResult.metadata
                val detailedError = feishuResult.error
                    ?: (meta["message"] as? String)
                    ?: (meta["error"] as? String)
                    ?: (meta["status"]?.toString()?.let { "HTTP $it" })
                    ?: feishuResult.data?.toString()
                    ?: "Unknown error"

                SkillResult.error(message = detailedError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feishu tool execution failed: $name", e)
            SkillResult.error("Feishu tool error: ${e.message}")
        }
    }

    private fun convertParametersSchema(
        feishuSchema: com.xiaomo.feishu.tools.ParametersSchema
    ): com.xiaomo.hermes.providers.ParametersSchema {
        return com.xiaomo.hermes.providers.ParametersSchema(
            type = feishuSchema.type,
            properties = feishuSchema.properties.mapValues { (_, prop) ->
                convertPropertySchema(prop)
            },
            required = feishuSchema.required
        )
    }

    private fun convertPropertySchema(
        prop: com.xiaomo.feishu.tools.PropertySchema
    ): com.xiaomo.hermes.providers.PropertySchema {
        return com.xiaomo.hermes.providers.PropertySchema(
            type = prop.type,
            description = prop.description,
            enum = prop.enum,
            items = prop.items?.let { convertPropertySchema(it) },
            properties = prop.properties?.mapValues { (_, child) -> convertPropertySchema(child) }
        )
    }
}

/**
 * Register all enabled feishu tools into a ToolRegistry
 *
 * @param registry The main ToolRegistry to register into
 * @param feishuToolRegistry The feishu extension's tool registry
 * @return Number of tools registered
 */
fun registerFeishuTools(
    registry: ToolRegistry,
    feishuToolRegistry: com.xiaomo.feishu.tools.FeishuToolRegistry
): Int {
    var count = 0
    for (tool in feishuToolRegistry.getAllTools()) {
        if (tool.isEnabled()) {
            val adapter = FeishuToolAdapter(tool)
            registry.register(adapter)
            count++
        }
    }
    Log.i("FeishuToolAdapter", "✅ Registered $count feishu tools into ToolRegistry")
    return count
}
