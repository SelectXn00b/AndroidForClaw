package com.xiaomo.feishu.tools.common

/**
 * Feishu Common tool set.
 * Line-by-line translation from @larksuite/openclaw-lark JS source.
 * - feishu_get_user: get user info (self or by user_id)
 * - feishu_search_user: search users by keyword
 */

import android.util.Log
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeishuCommonTools"

// ─── feishu_get_user ───────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30 — line-by-line
class FeishuGetUserTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_get_user"
    override val description = "获取用户信息。不传 user_id 时获取当前用户自己的信息；传 user_id 时获取指定用户的信息。" +
        "返回用户姓名、头像、邮箱、手机号、部门等信息。"

    override fun isEnabled() = config.enableCommonTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val userId = args["user_id"] as? String
            val userIdType = args["user_id_type"] as? String ?: "open_id"

            // Mode 1: Get current user's own info
            if (userId == null) {
                Log.i(TAG, "get_user: fetching current user info")
                try {
                    // GET /open-apis/authen/v1/user_info
                    val result = client.get("/open-apis/authen/v1/user_info")
                    if (result.isFailure) {
                        // Check for error code 41050: org visibility restriction
                        val errMsg = result.exceptionOrNull()?.message ?: ""
                        if (errMsg.contains("41050")) {
                            return@withContext ToolResult.error(
                                "无权限查询该用户信息。\n\n" +
                                "说明：使用用户身份调用通讯录 API 时，可操作的权限范围不受应用的通讯录权限范围影响，" +
                                "而是受当前用户的组织架构可见范围影响。该范围限制了用户在企业内可见的组织架构数据范围。"
                            )
                        }
                        return@withContext ToolResult.error(errMsg.ifBlank { "Failed to get current user info" })
                    }
                    Log.i(TAG, "get_user: current user fetched successfully")
                    val response = JsonObject().apply {
                        add("user", result.getOrNull()?.getAsJsonObject("data"))
                    }
                    return@withContext ToolResult.success(response)
                } catch (invokeErr: Exception) {
                    // Check for error code 41050
                    if (invokeErr.message?.contains("41050") == true) {
                        return@withContext ToolResult.error(
                            "无权限查询该用户信息。\n\n" +
                            "说明：使用用户身份调用通讯录 API 时，可操作的权限范围不受应用的通讯录权限范围影响，" +
                            "而是受当前用户的组织架构可见范围影响。该范围限制了用户在企业内可见的组织架构数据范围。"
                        )
                    }
                    throw invokeErr
                }
            }

            // Mode 2: Get specified user's info
            Log.i(TAG, "get_user: fetching user $userId")
            try {
                // GET /open-apis/contact/v3/users/:user_id
                val result = client.get("/open-apis/contact/v3/users/$userId?user_id_type=$userIdType")
                if (result.isFailure) {
                    val errMsg = result.exceptionOrNull()?.message ?: ""
                    if (errMsg.contains("41050")) {
                        return@withContext ToolResult.error(
                            "无权限查询该用户信息。\n\n" +
                            "说明：使用用户身份调用通讯录 API 时，可操作的权限范围不受应用的通讯录权限范围影响，" +
                            "而是受当前用户的组织架构可见范围影响。该范围限制了用户在企业内可见的组织架构数据范围。\n\n" +
                            "建议：请联系管理员调整当前用户的组织架构可见范围，或使用应用身份（tenant_access_token）调用 API。"
                        )
                    }
                    return@withContext ToolResult.error(errMsg.ifBlank { "Failed to get user info" })
                }
                Log.i(TAG, "get_user: user $userId fetched successfully")
                val response = JsonObject().apply {
                    add("user", result.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("user"))
                }
                ToolResult.success(response)
            } catch (invokeErr: Exception) {
                if (invokeErr.message?.contains("41050") == true) {
                    return@withContext ToolResult.error(
                        "无权限查询该用户信息。\n\n" +
                        "说明：使用用户身份调用通讯录 API 时，可操作的权限范围不受应用的通讯录权限范围影响，" +
                        "而是受当前用户的组织架构可见范围影响。该范围限制了用户在企业内可见的组织架构数据范围。\n\n" +
                        "建议：请联系管理员调整当前用户的组织架构可见范围，或使用应用身份（tenant_access_token）调用 API。"
                    )
                }
                throw invokeErr
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_get_user failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "user_id" to PropertySchema("string", "用户 ID（格式如 ou_xxx）。若不传入，则获取当前用户自己的信息"),
                    "user_id_type" to PropertySchema("string", "用户 ID 类型（默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id"))
                ),
                required = emptyList()
            )
        )
    )
}

// ─── feishu_search_user ────────────────────────────────────────────

// @aligned openclaw-lark v2026.3.30 — line-by-line
class FeishuSearchUserTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_search_user"
    override val description = "搜索员工信息（通过关键词搜索姓名、手机号、邮箱）。返回匹配的员工列表，" +
        "包含姓名、部门、open_id 等信息。"

    override fun isEnabled() = config.enableCommonTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val query = args["query"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: query")
            val pageSize = (args["page_size"] as? Number)?.toInt() ?: 20
            val pageToken = args["page_token"] as? String

            Log.i(TAG, "search_user: query=\"$query\", page_size=$pageSize")

            val requestQuery = mutableListOf(
                "query=$query",
                "page_size=$pageSize"
            )
            if (pageToken != null) requestQuery.add("page_token=$pageToken")
            val queryString = requestQuery.joinToString("&")

            // GET /open-apis/search/v1/user
            val result = client.get("/open-apis/search/v1/user?$queryString")
            if (result.isFailure) {
                return@withContext ToolResult.error(
                    result.exceptionOrNull()?.message ?: "Failed to search users"
                )
            }
            val data = result.getOrNull()?.getAsJsonObject("data")
            val users = data?.getAsJsonArray("users")
            val userCount = users?.size() ?: 0
            Log.i(TAG, "search_user: found $userCount users")

            val response = JsonObject().apply {
                add("users", users)
                addProperty("has_more", data?.get("has_more")?.asBoolean ?: false)
                data?.get("page_token")?.let { add("page_token", it) }
            }
            ToolResult.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "feishu_search_user failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "query" to PropertySchema("string", "搜索关键词，用于匹配用户名（必填）"),
                    "page_size" to PropertySchema("integer", "分页大小，控制每次返回的用户数量（默认20，最大200）"),
                    "page_token" to PropertySchema("string", "分页标识。首次请求无需填写；当返回结果中包含 page_token 时，可传入该值继续请求下一页")
                ),
                required = listOf("query")
            )
        )
    )
}

// ─── Aggregator ────────────────────────────────────────────────────

class FeishuCommonTools(config: FeishuConfig, client: FeishuClient) {
    private val getUserTool = FeishuGetUserTool(config, client)
    private val searchUserTool = FeishuSearchUserTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(getUserTool, searchUserTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}
