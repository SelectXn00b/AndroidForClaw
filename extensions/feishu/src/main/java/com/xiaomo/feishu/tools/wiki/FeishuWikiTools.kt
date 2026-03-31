package com.xiaomo.feishu.tools.wiki

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 飞书知识库工具集
 * 对齐 @larksuite/openclaw-lark wiki-tools
 */
class FeishuWikiTools(config: FeishuConfig, client: FeishuClient) {
    private val spaceTool = FeishuWikiSpaceTool(config, client)
    private val nodeTool = FeishuWikiSpaceNodeTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(spaceTool, nodeTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ---------------------------------------------------------------------------
// FeishuWikiSpaceTool
// @aligned openclaw-lark v2026.3.30 — line-by-line
// JS source: openclaw-lark/src/tools/oapi/wiki/space.js
// ---------------------------------------------------------------------------

class FeishuWikiSpaceTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuWikiSpaceTool"
    }

    override val name = "feishu_wiki_space"

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description = "飞书知识空间管理工具。当用户要求查看知识库列表、获取知识库信息、创建知识库时使用。" +
            "Actions: list（列出知识空间）, get（获取知识空间信息）, create（创建知识空间）。" +
            "【重要】space_id 可以从浏览器 URL 中获取，或通过 list 接口获取。" +
            "【重要】知识空间（Space）是知识库的基本组成单位，包含多个具有层级关系的文档节点。"

    override fun isEnabled() = config.enableWikiTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "list" -> executeList(args)
                "get" -> executeGet(args)
                "create" -> executeCreate(args)
                else -> ToolResult.error("Unknown action: $action. Supported: list, get, create")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/wiki/v2/spaces with page_size, page_token params
    // JS returns: { spaces: data?.items, has_more: data?.has_more, page_token: data?.page_token }
    private suspend fun executeList(args: Map<String, Any?>): ToolResult {
        val pageSize = (args["page_size"] as? Number)?.toInt()
        val pageToken = args["page_token"] as? String

        Log.i(TAG, "list: page_size=${pageSize ?: 10}")

        val params = mutableListOf<String>()
        pageSize?.let { params.add("page_size=$it") }
        pageToken?.let { params.add("page_token=$it") }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val result = client.get("/open-apis/wiki/v2/spaces$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list wiki spaces")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val items = data?.getAsJsonArray("items")
        Log.i(TAG, "list: returned ${items?.size() ?: 0} spaces")

        return ToolResult.success(mapOf(
            "spaces" to items,
            "has_more" to data?.get("has_more"),
            "page_token" to data?.get("page_token")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/wiki/v2/spaces/:space_id
    // JS returns: { space: res.data?.space }
    private suspend fun executeGet(args: Map<String, Any?>): ToolResult {
        val spaceId = args["space_id"] as? String
            ?: return ToolResult.error("Missing required parameter: space_id")

        Log.i(TAG, "get: space_id=$spaceId")

        val result = client.get("/open-apis/wiki/v2/spaces/$spaceId")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get wiki space")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "get: retrieved space $spaceId")

        return ToolResult.success(mapOf(
            "space" to data?.get("space")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/wiki/v2/spaces with { name, description }
    // JS returns: { space: res.data?.space }
    private suspend fun executeCreate(args: Map<String, Any?>): ToolResult {
        val name = args["name"] as? String
        val desc = args["description"] as? String

        Log.i(TAG, "create: name=${name ?: "(empty)"}, description=${desc ?: "(empty)"}")

        val body = mutableMapOf<String, Any>()
        name?.let { body["name"] = it }
        desc?.let { body["description"] = it }

        val result = client.post("/open-apis/wiki/v2/spaces", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create wiki space")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "create: created space_id=${data?.getAsJsonObject("space")?.get("space_id")}")

        return ToolResult.success(mapOf(
            "space" to data?.get("space")
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
                        enum = listOf("list", "get", "create")
                    ),
                    "space_id" to PropertySchema(
                        type = "string",
                        description = "知识空间 ID（get 操作必填）"
                    ),
                    "name" to PropertySchema(
                        type = "string",
                        description = "知识空间名称（create 操作可选）"
                    ),
                    "description" to PropertySchema(
                        type = "string",
                        description = "知识空间描述（create 操作可选）"
                    ),
                    "page_size" to PropertySchema(
                        type = "integer",
                        description = "分页大小（默认 10，最大 50）"
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
// FeishuWikiSpaceNodeTool
// @aligned openclaw-lark v2026.3.30 — line-by-line
// JS source: openclaw-lark/src/tools/oapi/wiki/space-node.js
// ---------------------------------------------------------------------------

class FeishuWikiSpaceNodeTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuWikiSpaceNodeTool"
    }

    override val name = "feishu_wiki_space_node"

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description = "飞书知识库节点管理工具。操作：list（列表）、get（获取）、create（创建）、move（移动）、copy（复制）。" +
            "节点是知识库中的文档，包括 doc、bitable(多维表表格)、sheet(电子表格) 等类型。" +
            "node_token 是节点的唯一标识符，obj_token 是实际文档的 token。可通过 get 操作将 wiki 类型的 node_token 转换为实际文档的 obj_token。"

    override fun isEnabled() = config.enableWikiTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "list" -> executeList(args)
                "get" -> executeGet(args)
                "create" -> executeCreate(args)
                "move" -> executeMove(args)
                "copy" -> executeCopy(args)
                else -> ToolResult.error("Unknown action: $action. Supported: list, get, create, move, copy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/wiki/v2/spaces/:space_id/nodes with page_size, page_token, parent_node_token
    // JS returns: { nodes: data?.items, has_more: data?.has_more, page_token: data?.page_token }
    private suspend fun executeList(args: Map<String, Any?>): ToolResult {
        val spaceId = args["space_id"] as? String
            ?: return ToolResult.error("Missing required parameter: space_id")
        val parentNodeToken = args["parent_node_token"] as? String
        val pageSize = (args["page_size"] as? Number)?.toInt()
        val pageToken = args["page_token"] as? String

        Log.i(TAG, "list: space_id=$spaceId, parent=${parentNodeToken ?: "(root)"}, page_size=${pageSize ?: 50}")

        val params = mutableListOf<String>()
        pageSize?.let { params.add("page_size=$it") }
        pageToken?.let { params.add("page_token=$it") }
        parentNodeToken?.let { params.add("parent_node_token=$it") }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val result = client.get("/open-apis/wiki/v2/spaces/$spaceId/nodes$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list wiki nodes")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val items = data?.getAsJsonArray("items")
        Log.i(TAG, "list: returned ${items?.size() ?: 0} nodes")

        return ToolResult.success(mapOf(
            "nodes" to items,
            "has_more" to data?.get("has_more"),
            "page_token" to data?.get("page_token")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/wiki/v2/spaces/get_node with token, obj_type params
    // JS defaults obj_type to 'wiki'
    // JS returns: { node: res.data?.node }
    private suspend fun executeGet(args: Map<String, Any?>): ToolResult {
        val token = args["token"] as? String
            ?: return ToolResult.error("Missing required parameter: token")
        val objType = (args["obj_type"] as? String)?.ifEmpty { "wiki" } ?: "wiki"

        Log.i(TAG, "get: token=$token, obj_type=$objType")

        val result = client.get("/open-apis/wiki/v2/spaces/get_node?token=$token&obj_type=$objType")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get wiki node")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "get: retrieved node $token")

        return ToolResult.success(mapOf(
            "node" to data?.get("node")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/wiki/v2/spaces/:space_id/nodes
    // JS body: { obj_type, parent_node_token, node_type, origin_node_token, title }
    // JS returns: { node: res.data?.node }
    private suspend fun executeCreate(args: Map<String, Any?>): ToolResult {
        val spaceId = args["space_id"] as? String
            ?: return ToolResult.error("Missing required parameter: space_id")
        val objType = args["obj_type"] as? String
            ?: return ToolResult.error("Missing required parameter: obj_type")
        val nodeType = args["node_type"] as? String
            ?: return ToolResult.error("Missing required parameter: node_type")
        val parentNodeToken = args["parent_node_token"] as? String
        val originNodeToken = args["origin_node_token"] as? String
        val title = args["title"] as? String

        Log.i(TAG, "create: space_id=$spaceId, obj_type=$objType, parent=${parentNodeToken ?: "(root)"}, title=${title ?: "(empty)"}, node_type=$nodeType, original_node_token=${originNodeToken ?: "(empty)"}")

        val body = mutableMapOf<String, Any>(
            "obj_type" to objType,
            "node_type" to nodeType
        )
        parentNodeToken?.let { body["parent_node_token"] = it }
        originNodeToken?.let { body["origin_node_token"] = it }
        title?.let { body["title"] = it }

        val result = client.post("/open-apis/wiki/v2/spaces/$spaceId/nodes", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create wiki node")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "create: created node_token=${data?.getAsJsonObject("node")?.get("node_token")}")

        return ToolResult.success(mapOf(
            "node" to data?.get("node")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/wiki/v2/spaces/:space_id/nodes/:node_token/move
    // JS body: { target_parent_token }
    // JS returns: { node: res.data?.node }
    private suspend fun executeMove(args: Map<String, Any?>): ToolResult {
        val spaceId = args["space_id"] as? String
            ?: return ToolResult.error("Missing required parameter: space_id")
        val nodeToken = args["node_token"] as? String
            ?: return ToolResult.error("Missing required parameter: node_token")
        val targetParentToken = args["target_parent_token"] as? String

        Log.i(TAG, "move: space_id=$spaceId, node_token=$nodeToken, target_parent=${targetParentToken ?: "(root)"}")

        val body = mutableMapOf<String, Any>()
        targetParentToken?.let { body["target_parent_token"] = it }

        val result = client.post("/open-apis/wiki/v2/spaces/$spaceId/nodes/$nodeToken/move", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to move wiki node")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "move: moved node $nodeToken")

        return ToolResult.success(mapOf(
            "node" to data?.get("node")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/wiki/v2/spaces/:space_id/nodes/:node_token/copy
    // JS body: { target_space_id, target_parent_token, title }
    // JS returns: { node: res.data?.node }
    private suspend fun executeCopy(args: Map<String, Any?>): ToolResult {
        val spaceId = args["space_id"] as? String
            ?: return ToolResult.error("Missing required parameter: space_id")
        val nodeToken = args["node_token"] as? String
            ?: return ToolResult.error("Missing required parameter: node_token")
        val targetSpaceId = args["target_space_id"] as? String
        val targetParentToken = args["target_parent_token"] as? String
        val title = args["title"] as? String

        Log.i(TAG, "copy: space_id=$spaceId, node_token=$nodeToken, target_space=${targetSpaceId ?: "(same)"}, target_parent=${targetParentToken ?: "(root)"}")

        val body = mutableMapOf<String, Any>()
        targetSpaceId?.let { body["target_space_id"] = it }
        targetParentToken?.let { body["target_parent_token"] = it }
        title?.let { body["title"] = it }

        val result = client.post("/open-apis/wiki/v2/spaces/$spaceId/nodes/$nodeToken/copy", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to copy wiki node")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "copy: copied to node_token=${data?.getAsJsonObject("node")?.get("node_token")}")

        return ToolResult.success(mapOf(
            "node" to data?.get("node")
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
                        enum = listOf("list", "get", "create", "move", "copy")
                    ),
                    "space_id" to PropertySchema(
                        type = "string",
                        description = "知识空间 ID（list/create/move/copy 操作必填）"
                    ),
                    "token" to PropertySchema(
                        type = "string",
                        description = "节点 token（get 操作必填，可以是 node_token 或 obj_token）"
                    ),
                    "obj_type" to PropertySchema(
                        type = "string",
                        description = "文档类型（get 操作可选，默认 wiki；create 操作必填）",
                        enum = listOf("doc", "sheet", "mindnote", "bitable", "file", "docx", "slides", "wiki")
                    ),
                    "node_token" to PropertySchema(
                        type = "string",
                        description = "节点 token（move/copy 操作必填）"
                    ),
                    "parent_node_token" to PropertySchema(
                        type = "string",
                        description = "父节点 token（list 操作可选，不传则列出根节点；create 操作可选）"
                    ),
                    "node_type" to PropertySchema(
                        type = "string",
                        description = "节点类型（create 操作必填）",
                        enum = listOf("origin", "shortcut")
                    ),
                    "origin_node_token" to PropertySchema(
                        type = "string",
                        description = "源节点 token（create 操作可选，node_type 为 shortcut 时使用）"
                    ),
                    "title" to PropertySchema(
                        type = "string",
                        description = "节点标题（create/copy 操作可选）"
                    ),
                    "target_parent_token" to PropertySchema(
                        type = "string",
                        description = "目标父节点 token（move/copy 操作可选）"
                    ),
                    "target_space_id" to PropertySchema(
                        type = "string",
                        description = "目标知识空间 ID（copy 操作可选）"
                    ),
                    "page_size" to PropertySchema(
                        type = "integer",
                        description = "分页大小（list 操作可选）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记（list 操作可选）"
                    )
                ),
                required = listOf("action")
            )
        )
    )
}
