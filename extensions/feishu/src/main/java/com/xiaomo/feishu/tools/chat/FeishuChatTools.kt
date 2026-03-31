package com.xiaomo.feishu.tools.chat

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书群聊工具集
 * 对齐 @larksuite/openclaw-lark chat-tools
 */
class FeishuChatTools(config: FeishuConfig, client: FeishuClient) {
    private val chatTool = FeishuChatTool(config, client)
    private val membersTool = FeishuChatMembersTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(chatTool, membersTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ---------------------------------------------------------------------------
// FeishuChatTool
// @aligned openclaw-lark v2026.3.30 — line-by-line
// JS source: openclaw-lark/src/tools/oapi/chat/chat.js
// ---------------------------------------------------------------------------

class FeishuChatTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuChatTool"
        // JS: 'X-Chat-Custom-Header': 'enable_chat_list_security_check'
        private val CHAT_SECURITY_HEADERS = mapOf(
            "X-Chat-Custom-Header" to "enable_chat_list_security_check"
        )
    }

    override val name = "feishu_chat"

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description = "以用户身份调用飞书群聊管理工具。Actions: search（搜索群列表，支持关键词匹配群名称、群成员）, " +
            "get（获取指定群的详细信息，包括群名称、描述、头像、群主、权限配置等）。"

    override fun isEnabled() = config.enableChatTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "search" -> executeSearch(args)
                "get" -> executeGet(args)
                else -> ToolResult.error("Unknown action: $action. Supported: search, get")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/im/v1/chats/search with user_id_type, query, page_size, page_token
    // JS returns: { items: data?.items, has_more: data?.has_more ?? false, page_token: data?.page_token }
    private suspend fun executeSearch(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.error("Missing required parameter: query")
        val pageSize = (args["page_size"] as? Number)?.toInt()
        val pageToken = args["page_token"] as? String
        val userIdType = (args["user_id_type"] as? String) ?: "open_id"

        Log.i(TAG, "search: query=\"$query\", page_size=${pageSize ?: 20}")

        val params = mutableListOf(
            "user_id_type=$userIdType",
            "query=$query"
        )
        pageSize?.let { params.add("page_size=$it") }
        pageToken?.let { params.add("page_token=$it") }

        val queryStr = "?${params.joinToString("&")}"
        val result = client.get("/open-apis/im/v1/chats/search$queryStr")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to search chats")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val chatCount = data?.getAsJsonArray("items")?.size() ?: 0
        Log.i(TAG, "search: found $chatCount chats")

        return ToolResult.success(mapOf(
            "items" to data?.get("items"),
            "has_more" to (data?.get("has_more") ?: false),
            "page_token" to data?.get("page_token")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/im/v1/chats/:chat_id with user_id_type param
    // JS: adds header X-Chat-Custom-Header: enable_chat_list_security_check
    // JS returns: { chat: res.data }
    private suspend fun executeGet(args: Map<String, Any?>): ToolResult {
        val chatId = args["chat_id"] as? String
            ?: return ToolResult.error("Missing required parameter: chat_id")
        val userIdType = (args["user_id_type"] as? String) ?: "open_id"

        Log.i(TAG, "get: chat_id=$chatId, user_id_type=$userIdType")

        val params = mutableListOf("user_id_type=$userIdType")
        val queryStr = "?${params.joinToString("&")}"

        // JS uses custom header: 'X-Chat-Custom-Header': 'enable_chat_list_security_check'
        val result = client.get(
            "/open-apis/im/v1/chats/$chatId$queryStr",
            headers = CHAT_SECURITY_HEADERS
        )

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get chat info")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "get: retrieved chat info for $chatId")

        // JS returns: json({ chat: res.data }) — the entire data object
        return ToolResult.success(mapOf(
            "chat" to data
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema(
                        type = "string",
                        description = "操作类型",
                        enum = listOf("search", "get")
                    ),
                    "chat_id" to PropertySchema(
                        type = "string",
                        description = "群 ID（格式如 oc_xxx）（get 操作必填）"
                    ),
                    "query" to PropertySchema(
                        type = "string",
                        description = "搜索关键词（search 操作必填）。支持匹配群名称、群成员名称。支持多语种、拼音、前缀等模糊搜索。"
                    ),
                    "user_id_type" to PropertySchema(
                        type = "string",
                        description = "用户 ID 类型（可选，默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id")
                    ),
                    "page_size" to PropertySchema(
                        type = "integer",
                        description = "分页大小（默认 20）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记。首次请求无需填写"
                    )
                ),
                required = listOf("action")
            )
        )
    )
}

// ---------------------------------------------------------------------------
// FeishuChatMembersTool
// @aligned openclaw-lark v2026.3.30 — line-by-line
// JS source: openclaw-lark/src/tools/oapi/chat/members.js
// ---------------------------------------------------------------------------

class FeishuChatMembersTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuChatMembersTool"
        // JS: 'X-Chat-Custom-Header': 'enable_chat_list_security_check'
        private val CHAT_SECURITY_HEADERS = mapOf(
            "X-Chat-Custom-Header" to "enable_chat_list_security_check"
        )
    }

    override val name = "feishu_chat_members"

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description = "以用户的身份获取指定群组的成员列表。" +
            "返回成员信息，包含成员 ID、姓名等。" +
            "注意：不会返回群组内的机器人成员。"

    override fun isEnabled() = config.enableChatTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/im/v1/chats/:chat_id/members (actually sdk.im.v1.chatMembers.get)
    // JS: with member_id_type, page_size, page_token params
    // JS: adds header X-Chat-Custom-Header: enable_chat_list_security_check
    // JS returns: { items: data?.items, has_more: data?.has_more ?? false, page_token: data?.page_token, member_total: memberTotal }
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val chatId = args["chat_id"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: chat_id")

            val memberIdType = (args["member_id_type"] as? String) ?: "open_id"
            val pageSize = (args["page_size"] as? Number)?.toInt()
            val pageToken = args["page_token"] as? String

            Log.i(TAG, "chat_members: chat_id=\"$chatId\", page_size=${pageSize ?: 20}")

            val params = mutableListOf("member_id_type=$memberIdType")
            pageSize?.let { params.add("page_size=$it") }
            pageToken?.let { params.add("page_token=$it") }

            val query = "?${params.joinToString("&")}"

            // JS uses custom header: 'X-Chat-Custom-Header': 'enable_chat_list_security_check'
            val result = client.get(
                "/open-apis/im/v1/chats/$chatId/members$query",
                headers = CHAT_SECURITY_HEADERS
            )

            if (result.isFailure) {
                return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get chat members")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            val memberCount = data?.getAsJsonArray("items")?.size() ?: 0
            val memberTotal = data?.get("member_total")?.asInt ?: 0
            Log.i(TAG, "chat_members: found $memberCount members (total: $memberTotal)")

            ToolResult.success(mapOf(
                "items" to data?.get("items"),
                "has_more" to (data?.get("has_more") ?: false),
                "page_token" to data?.get("page_token"),
                "member_total" to memberTotal
            ))
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
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
                    "chat_id" to PropertySchema(
                        type = "string",
                        description = "群 ID（格式如 oc_xxx）。可以通过 feishu_chat_search 工具搜索获取"
                    ),
                    "member_id_type" to PropertySchema(
                        type = "string",
                        description = "成员 ID 类型（可选，默认 open_id）",
                        enum = listOf("open_id", "union_id", "user_id")
                    ),
                    "page_size" to PropertySchema(
                        type = "integer",
                        description = "分页大小（默认 20）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记。首次请求无需填写"
                    )
                ),
                required = listOf("chat_id")
            )
        )
    )
}
