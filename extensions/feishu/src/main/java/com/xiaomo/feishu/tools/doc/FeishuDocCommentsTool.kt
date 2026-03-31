package com.xiaomo.feishu.tools.doc

// @aligned openclaw-lark v2026.3.30 — line-by-line translation
/**
 * Line-by-line translation of official @larksuite/openclaw-lark feishu_doc_comments OAPI tool.
 * Official JS source: /tools/oapi/drive/doc-comments.js
 *
 * Actions: list (get comments with replies), create (whole-doc comment), patch (resolve/restore).
 */

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeishuDocComments"

class FeishuDocCommentsTool(config: FeishuConfig, client: FeishuClient) : FeishuToolBase(config, client) {
    override val name = "feishu_doc_comments"
    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official description)
    override val description = "【以用户身份】管理云文档评论。支持: " +
        "(1) list - 获取评论列表(含完整回复); " +
        "(2) create - 添加全文评论(支持文本、@用户、超链接); " +
        "(3) patch - 解决/恢复评论。" +
        "支持 wiki token。"

    override fun isEnabled() = config.enableDocTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            // Matching official: extract parameters
            val fileToken = args["file_token"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: file_token")
            val fileType = args["file_type"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: file_type")
            val userIdType = args["user_id_type"] as? String ?: "open_id"

            // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official wiki token conversion)
            var actualFileToken = fileToken
            var actualFileType = fileType
            if (fileType == "wiki") {
                Log.i(TAG, "doc_comments: detected wiki token=\"$fileToken\", converting to obj_token...")
                try {
                    val wikiNodeRes = client.get("/open-apis/wiki/v2/spaces/get_node?token=$fileToken&obj_type=wiki")
                    if (wikiNodeRes.isFailure) {
                        return@withContext ToolResult.error("failed to resolve wiki token \"$fileToken\": ${wikiNodeRes.exceptionOrNull()?.message}")
                    }

                    val node = wikiNodeRes.getOrNull()?.getAsJsonObject("data")?.getAsJsonObject("node")
                    val objToken = node?.get("obj_token")?.asString
                    val objType = node?.get("obj_type")?.asString

                    if (objToken == null || objType == null) {
                        return@withContext ToolResult.error("failed to resolve wiki token \"$fileToken\" to document object (may be a folder node rather than a document)")
                    }

                    actualFileToken = objToken
                    actualFileType = objType
                    Log.i(TAG, "doc_comments: wiki token converted: obj_token=\"$actualFileToken\", obj_type=\"$actualFileType\"")
                } catch (err: Exception) {
                    Log.e(TAG, "doc_comments: failed to convert wiki token", err)
                    return@withContext ToolResult.error("failed to resolve wiki token \"$fileToken\": ${err.message}")
                }
            }

            // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official action dispatch)
            when (action) {
                "list" -> doList(args, actualFileToken, actualFileType, userIdType)
                "create" -> doCreate(args, actualFileToken, actualFileType, userIdType)
                "patch" -> doPatch(args, actualFileToken, actualFileType)
                else -> ToolResult.error("未知的 action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_doc_comments failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official list action)
    private suspend fun doList(
        args: Map<String, Any?>,
        actualFileToken: String,
        actualFileType: String,
        userIdType: String
    ): ToolResult {
        val isWhole = args["is_whole"] as? Boolean
        val isSolved = args["is_solved"] as? Boolean
        val pageSize = (args["page_size"] as? Number)?.toInt() ?: 50 // Matching official default
        val pageToken = args["page_token"] as? String

        Log.i(TAG, "doc_comments.list: file_token=\"$actualFileToken\", file_type=$actualFileType")

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official API call)
        var path = "/open-apis/drive/v1/files/$actualFileToken/comments" +
                "?file_type=$actualFileType&user_id_type=$userIdType&page_size=$pageSize"
        if (isWhole != null) path += "&is_whole=$isWhole"
        if (isSolved != null) path += "&is_solved=$isSolved"
        if (pageToken != null) path += "&page_token=$pageToken"

        val result = client.get(path)
        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list comments")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val items = data?.getAsJsonArray("items") ?: JsonArray()
        val hasMore = data?.get("has_more")?.asBoolean ?: false
        val nextPageToken = data?.get("page_token")?.asString

        Log.i(TAG, "doc_comments.list: found ${items.size()} comments")

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official assembleCommentsWithReplies)
        val assembledItems = assembleCommentsWithReplies(
            client, actualFileToken, actualFileType, items, userIdType
        )

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official return format)
        val resultMap = mutableMapOf<String, Any?>(
            "items" to assembledItems,
            "has_more" to hasMore
        )
        if (nextPageToken != null) resultMap["page_token"] = nextPageToken

        return ToolResult.success(resultMap)
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official create action)
    private suspend fun doCreate(
        args: Map<String, Any?>,
        actualFileToken: String,
        actualFileType: String,
        userIdType: String
    ): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val elements = args["elements"] as? List<Map<String, Any?>>

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official validation)
        if (elements == null || elements.isEmpty()) {
            return ToolResult.error("elements 参数必填且不能为空")
        }

        Log.i(TAG, "doc_comments.create: file_token=\"$actualFileToken\", elements=${elements.size}")

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official convertElementsToSDKFormat)
        val sdkElements = convertElementsToSDKFormat(elements)

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official API call)
        val body = mapOf(
            "reply_list" to mapOf(
                "replies" to listOf(
                    mapOf("content" to mapOf("elements" to sdkElements))
                )
            )
        )

        val result = client.post(
            "/open-apis/drive/v1/files/$actualFileToken/comments?file_type=$actualFileType&user_id_type=$userIdType",
            body
        )

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to create comment")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val commentId = data?.get("comment_id")?.asString

        Log.i(TAG, "doc_comments.create: created comment $commentId")

        return ToolResult.success(data ?: JsonObject())
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official patch action)
    private suspend fun doPatch(
        args: Map<String, Any?>,
        actualFileToken: String,
        actualFileType: String
    ): ToolResult {
        val commentId = args["comment_id"] as? String

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official validation)
        if (commentId == null) {
            return ToolResult.error("comment_id 参数必填")
        }

        val isSolvedValue = args["is_solved_value"] as? Boolean
        if (isSolvedValue == null) {
            return ToolResult.error("is_solved_value 参数必填")
        }

        Log.i(TAG, "doc_comments.patch: comment_id=\"$commentId\", is_solved=$isSolvedValue")

        // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official API call)
        val body = mapOf("is_solved" to isSolvedValue)
        val result = client.patch(
            "/open-apis/drive/v1/files/$actualFileToken/comments/$commentId?file_type=$actualFileType",
            body
        )

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to patch comment")
        }

        Log.i(TAG, "doc_comments.patch: success")

        return ToolResult.success(mapOf("success" to true))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official convertElementsToSDKFormat)
    private fun convertElementsToSDKFormat(elements: List<Map<String, Any?>>): List<Map<String, Any?>> {
        return elements.map { el ->
            val type = el["type"] as? String
            when (type) {
                "text" -> mapOf(
                    "type" to "text_run",
                    "text_run" to mapOf("text" to (el["text"] ?: ""))
                )
                "mention" -> mapOf(
                    "type" to "person",
                    "person" to mapOf("user_id" to (el["open_id"] ?: ""))
                )
                "link" -> mapOf(
                    "type" to "docs_link",
                    "docs_link" to mapOf("url" to (el["url"] ?: ""))
                )
                else -> mapOf(
                    "type" to "text_run",
                    "text_run" to mapOf("text" to "")
                )
            }
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official assembleCommentsWithReplies)
    /**
     * Assemble comments with complete reply lists.
     * For each comment that has replies, fetches the full reply list
     * via fileCommentReply.list in a pagination loop.
     * Matches official assembleCommentsWithReplies function.
     */
    private suspend fun assembleCommentsWithReplies(
        client: FeishuClient,
        fileToken: String,
        fileType: String,
        comments: JsonArray,
        userIdType: String
    ): JsonArray {
        val result = JsonArray()
        for (i in 0 until comments.size()) {
            val comment = comments[i].asJsonObject.deepCopy()
            val commentId = comment.get("comment_id")?.asString

            // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official condition check)
            val replyList = comment.getAsJsonObject("reply_list")
            val replies = replyList?.getAsJsonArray("replies")
            val hasReplies = replies?.size()?.let { it > 0 } ?: false
            val hasMore = comment.get("has_more")?.asBoolean ?: false

            // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official reply fetching logic)
            if (commentId != null && (hasReplies || hasMore)) {
                try {
                    val allReplies = JsonArray()
                    var replyPageToken: String? = null
                    var replyHasMore = true

                    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official pagination loop)
                    while (replyHasMore) {
                        var replyPath = "/open-apis/drive/v1/files/$fileToken/comments/$commentId/replies" +
                                "?file_type=$fileType&page_size=50&user_id_type=$userIdType"
                        if (replyPageToken != null) replyPath += "&page_token=$replyPageToken"

                        val replyRes = client.get(replyPath)
                        if (replyRes.isFailure) break

                        val replyData = replyRes.getOrNull()?.getAsJsonObject("data")
                        val replyItems = replyData?.getAsJsonArray("items")

                        if (replyItems != null && replyItems.size() > 0) {
                            for (j in 0 until replyItems.size()) {
                                allReplies.add(replyItems[j])
                            }
                            replyHasMore = replyData.get("has_more")?.asBoolean ?: false
                            replyPageToken = replyData.get("page_token")?.asString
                        } else {
                            break
                        }
                    }

                    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official reply_list update)
                    val newReplyList = JsonObject()
                    newReplyList.add("replies", allReplies)
                    comment.add("reply_list", newReplyList)

                    Log.d(TAG, "Assembled ${allReplies.size()} replies for comment $commentId")
                } catch (err: Exception) {
                    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official error handling)
                    Log.w(TAG, "Failed to fetch replies for comment $commentId: ${err.message}")
                    // Keep original reply data
                }
            }
            result.add(comment)
        }
        return result
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line (matching official DocCommentsSchema)
    override fun getToolDefinition() = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = ParametersSchema(
                properties = mapOf(
                    "action" to PropertySchema("string", "Action: list, create, or patch",
                        enum = listOf("list", "create", "patch")),
                    "file_token" to PropertySchema("string", "云文档token或wiki节点token(可从文档URL获取)。如果是wiki token，会自动转换为实际文档的obj_token"),
                    "file_type" to PropertySchema("string", "文档类型。wiki类型会自动解析为实际文档类型(docx/sheet/bitable等)",
                        enum = listOf("doc", "docx", "sheet", "file", "slides", "wiki")),
                    "is_whole" to PropertySchema("boolean", "是否只获取全文评论(action=list时可选)"),
                    "is_solved" to PropertySchema("boolean", "是否只获取已解决的评论(action=list时可选)"),
                    "page_size" to PropertySchema("integer", "分页大小"),
                    "page_token" to PropertySchema("string", "分页标记"),
                    "elements" to PropertySchema("array", "评论内容元素数组(action=create时必填)。支持text(纯文本)、mention(@用户)、link(超链接)三种类型",
                        items = PropertySchema("object", "Comment element")),
                    "comment_id" to PropertySchema("string", "评论ID(action=patch时必填)"),
                    "is_solved_value" to PropertySchema("boolean", "解决状态:true=解决,false=恢复(action=patch时必填)"),
                    "user_id_type" to PropertySchema("string", "User ID type: open_id, union_id, user_id",
                        enum = listOf("open_id", "union_id", "user_id"))
                ),
                required = listOf("action", "file_token", "file_type")
            )
        )
    )
}
