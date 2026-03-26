package com.xiaomo.androidforclaw.agent.tools

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/skills-install.ts
 */

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.agent.skills.ClawHubClient
import com.xiaomo.androidforclaw.agent.skills.ClawHubRateLimitException
import com.xiaomo.androidforclaw.agent.skills.SkillInstaller
import com.xiaomo.androidforclaw.providers.FunctionDefinition
import com.xiaomo.androidforclaw.providers.ParametersSchema
import com.xiaomo.androidforclaw.providers.PropertySchema
import com.xiaomo.androidforclaw.providers.ToolDefinition

private const val RATE_LIMIT_HINT = """ClawHub API 请求被限流 (HTTP 429)。
当前为匿名请求模式，请让用户提供 ClawHub token 以解除限流。
用户可在 clawhub.com 账户设置中获取 token。
获取后请调用: clawhub_config(action="set", token="用户提供的token")
然后重试之前的操作。"""

/**
 * skills_search — Search ClawHub for available skills
 */
class SkillsSearchTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "SkillsSearchTool"
    }

    private val client = ClawHubClient(context)

    override val name = "skills_search"
    override val description = "Search ClawHub skill hub for available skills. Returns skill names, descriptions, and versions."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "query" to PropertySchema(
                            type = "string",
                            description = "Search query (empty string lists all skills)"
                        ),
                        "limit" to PropertySchema(
                            type = "number",
                            description = "Max results to return (default: 20)"
                        )
                    ),
                    required = emptyList()
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = (args["query"] as? String) ?: ""
        val limit = (args["limit"] as? Number)?.toInt() ?: 20

        Log.d(TAG, "Searching ClawHub: query='$query', limit=$limit")

        return try {
            val result = client.searchSkills(query, limit)
            result.fold(
                onSuccess = { searchResult ->
                    val formatted = buildString {
                        appendLine("Found ${searchResult.total} skills on ClawHub:")
                        appendLine()
                        for (skill in searchResult.skills) {
                            appendLine("• **${skill.name}** (`${skill.slug}`)")
                            if (skill.description.isNotBlank()) {
                                appendLine("  ${skill.description}")
                            }
                            appendLine("  Version: ${skill.version}")
                            appendLine()
                        }
                        if (searchResult.skills.isEmpty()) {
                            appendLine("No skills found matching '$query'")
                        }
                    }
                    ToolResult.success(formatted)
                },
                onFailure = { e ->
                    Log.e(TAG, "Search failed", e)
                    if (e is ClawHubRateLimitException) {
                        return@execute ToolResult.error(RATE_LIMIT_HINT)
                    }
                    ToolResult.error("Failed to search ClawHub: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            ToolResult.error("Failed to search ClawHub: ${e.message}")
        }
    }
}

/**
 * skills_install — Install a skill from ClawHub
 */
class SkillsInstallTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "SkillsInstallTool"
    }

    private val installer = SkillInstaller(context)

    override val name = "skills_install"
    override val description = "Install a skill from ClawHub by slug name"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "slug" to PropertySchema(
                            type = "string",
                            description = "Skill slug name (e.g. 'weather', 'x-twitter')"
                        ),
                        "version" to PropertySchema(
                            type = "string",
                            description = "Version to install (default: latest)"
                        )
                    ),
                    required = listOf("slug")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val slug = args["slug"] as? String
            ?: return ToolResult.error("Missing required parameter: slug")
        val version = args["version"] as? String ?: "latest"

        Log.d(TAG, "Installing skill: $slug@$version")

        return try {
            val result = installer.installFromClawHub(slug, version)
            result.fold(
                onSuccess = { installResult ->
                    ToolResult.success(buildString {
                        appendLine("✅ Skill installed: ${installResult.name} ($slug@${installResult.version})")
                        appendLine("Location: ${installResult.path}")
                    })
                },
                onFailure = { e ->
                    Log.e(TAG, "Install failed", e)
                    if (e is ClawHubRateLimitException) {
                        return@execute ToolResult.error(RATE_LIMIT_HINT)
                    }
                    ToolResult.error("Failed to install skill '$slug': ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            ToolResult.error("Failed to install skill '$slug': ${e.message}")
        }
    }
}

/**
 * clawhub_config — 配置 ClawHub token
 *
 * 对齐 OpenClaw src/infra/clawhub.ts 的 token 机制。
 * 遇到 429 限流时，AI 可以让用户提供 token 并通过此工具保存。
 */
class ClawHubConfigTool(private val context: Context) : Tool {
    companion object {
        private const val TAG = "ClawHubConfigTool"
    }

    override val name = "clawhub_config"
    override val description = "Configure ClawHub authentication token. Use 'set' to save a token, 'get' to check current status, 'clear' to remove token."

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema(
                            type = "string",
                            description = "Action: 'set' (save token), 'get' (check status), 'clear' (remove token)"
                        ),
                        "token" to PropertySchema(
                            type = "string",
                            description = "ClawHub auth token (required for 'set' action)"
                        )
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing required parameter: action")

        return when (action) {
            "set" -> {
                val token = args["token"] as? String
                if (token.isNullOrBlank()) {
                    return ToolResult.error("Missing required parameter: token")
                }
                ClawHubClient.saveToken(context, token)
                Log.i(TAG, "ClawHub token 已配置")
                ToolResult.success("✅ ClawHub token 已保存，后续请求将自动附带认证信息。")
            }
            "get" -> {
                val existing = ClawHubClient.getToken(context)
                if (existing != null) {
                    // 只显示前 8 位，隐藏其余
                    val masked = if (existing.length > 8) {
                        existing.take(8) + "..." + " (${existing.length} chars)"
                    } else {
                        "***"
                    }
                    ToolResult.success("ClawHub token 已配置: $masked")
                } else {
                    ToolResult.success("ClawHub token 未配置，请求为匿名模式（可能被限流）。")
                }
            }
            "clear" -> {
                ClawHubClient.clearToken(context)
                Log.i(TAG, "ClawHub token 已清除")
                ToolResult.success("✅ ClawHub token 已清除，后续请求将使用匿名模式。")
            }
            else -> {
                ToolResult.error("Unknown action: $action. Use 'set', 'get', or 'clear'.")
            }
        }
    }
}
