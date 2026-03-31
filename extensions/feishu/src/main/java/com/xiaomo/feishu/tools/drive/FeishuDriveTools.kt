package com.xiaomo.feishu.tools.drive

import android.util.Base64
import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 飞书云空间工具集
 * 对齐 @larksuite/openclaw-lark drive-tools
 */
class FeishuDriveTools(config: FeishuConfig, client: FeishuClient) {
    private val fileTool = FeishuDriveFileTool(config, client)

    fun getAllTools(): List<FeishuToolBase> = listOf(fileTool)

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ---------------------------------------------------------------------------
// FeishuDriveFileTool
// @aligned openclaw-lark v2026.3.30 — line-by-line
// JS source: openclaw-lark/src/tools/oapi/drive/file.js
// ---------------------------------------------------------------------------

class FeishuDriveFileTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    companion object {
        private const val TAG = "FeishuDriveFileTool"
        // 分片上传配置 — aligned with JS: const SMALL_FILE_THRESHOLD = 15 * 1024 * 1024
        private const val SMALL_FILE_THRESHOLD = 15 * 1024 * 1024 // 15MB
    }

    override val name = "feishu_drive_file"

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description = "【以用户身份】飞书云空间文件管理工具。当用户要求查看云空间(云盘)中的文件列表、获取文件信息、" +
            "复制/移动/删除文件、上传/下载文件时使用。消息中的文件读写**禁止**使用该工具!" +
            "\n\nActions:" +
            "\n- list（列出文件）：列出文件夹下的文件。不提供 folder_token 时获取根目录清单" +
            "\n- get_meta（批量获取元数据）：批量查询文档元信息，使用 request_docs 数组参数，格式：[{doc_token: '...', doc_type: 'sheet'}]" +
            "\n- copy（复制文件）：复制文件到指定位置" +
            "\n- move（移动文件）：移动文件到指定文件夹" +
            "\n- delete（删除文件）：删除文件" +
            "\n- upload（上传文件）：上传本地文件到云空间。提供 file_path（本地文件路径）或 file_content_base64（Base64 编码）" +
            "\n- download（下载文件）：下载文件到本地。提供 output_path（本地保存路径）则保存到本地，否则返回 Base64 编码" +
            "\n\n【重要】copy/move/delete 操作需要 file_token 和 type 参数。get_meta 使用 request_docs 数组参数。" +
            "\n【重要】upload 优先使用 file_path（自动读取文件、提取文件名和大小），也支持 file_content_base64（需手动提供 file_name 和 size）。" +
            "\n【重要】download 提供 output_path 时保存到本地（可以是文件路径或文件夹路径+file_name），不提供则返回 Base64。"

    override fun isEnabled() = config.enableDriveTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                "list" -> executeList(args)
                "get_meta" -> executeGetMeta(args)
                "copy" -> executeCopy(args)
                "move" -> executeMove(args)
                "delete" -> executeDelete(args)
                "upload" -> executeUpload(args)
                "download" -> executeDownload(args)
                else -> ToolResult.error("Unknown action: $action. Supported: list, get_meta, copy, move, delete, upload, download")
            }
        } catch (e: Exception) {
            Log.e(TAG, "execute failed", e)
            ToolResult.error(e.message ?: "Unknown error")
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/drive/v1/files with folder_token, page_size, page_token, order_by, direction
    // JS returns: { files: data?.files, has_more: data?.has_more, page_token: data?.next_page_token }
    private suspend fun executeList(args: Map<String, Any?>): ToolResult {
        val folderToken = args["folder_token"] as? String
        val pageSize = (args["page_size"] as? Number)?.toInt()
        val pageToken = args["page_token"] as? String
        val orderBy = args["order_by"] as? String
        val direction = args["direction"] as? String

        Log.i(TAG, "list: folder_token=${folderToken ?: "(root)"}, page_size=${pageSize ?: 200}")

        val params = mutableListOf<String>()
        folderToken?.let { params.add("folder_token=$it") }
        pageSize?.let { params.add("page_size=$it") }
        pageToken?.let { params.add("page_token=$it") }
        orderBy?.let { params.add("order_by=$it") }
        direction?.let { params.add("direction=$it") }

        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val result = client.get("/open-apis/drive/v1/files$query")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to list drive files")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "list: returned ${data?.getAsJsonArray("files")?.size() ?: 0} files")

        return ToolResult.success(mapOf(
            "files" to data?.get("files"),
            "has_more" to data?.get("has_more"),
            "page_token" to data?.get("next_page_token")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/drive/v1/metas/batch_query with { request_docs }
    // JS validates: request_docs must be non-empty array
    // JS returns: { metas: res.data?.metas ?? [] }
    @Suppress("UNCHECKED_CAST")
    private suspend fun executeGetMeta(args: Map<String, Any?>): ToolResult {
        val requestDocs = args["request_docs"] as? List<Map<String, Any?>>

        // JS: if (!p.request_docs || !Array.isArray(p.request_docs) || p.request_docs.length === 0)
        if (requestDocs == null || requestDocs.isEmpty()) {
            return ToolResult.success(mapOf(
                "error" to "request_docs must be a non-empty array. Correct format: {action: 'get_meta', request_docs: [{doc_token: '...', doc_type: 'sheet'}]}"
            ))
        }

        Log.i(TAG, "get_meta: querying ${requestDocs.size} documents")

        val body = mapOf("request_docs" to requestDocs)
        val result = client.post("/open-apis/drive/v1/metas/batch_query", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to get file meta")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "get_meta: returned ${data?.getAsJsonArray("metas")?.size() ?: 0} metas")

        return ToolResult.success(mapOf(
            "metas" to (data?.get("metas") ?: emptyList<Any>())
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/drive/v1/files/:file_token/copy with { name, type, folder_token }
    // JS: folder_token || parent_node (alias)
    // JS returns: { file: data?.file }
    private suspend fun executeCopy(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val name = args["name"] as? String
            ?: return ToolResult.error("Missing required parameter: name")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")

        // JS: const targetFolderToken = p.folder_token || p.parent_node
        val targetFolderToken = (args["folder_token"] as? String) ?: (args["parent_node"] as? String)

        Log.i(TAG, "copy: file_token=$fileToken, name=$name, type=$type, folder_token=${targetFolderToken ?: "(root)"}")

        val body = mutableMapOf<String, Any>(
            "name" to name,
            "type" to type
        )
        targetFolderToken?.let { body["folder_token"] = it }

        val result = client.post("/open-apis/drive/v1/files/$fileToken/copy", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to copy file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        Log.i(TAG, "copy: new file_token=${data?.getAsJsonObject("file")?.get("token") ?: "unknown"}")

        return ToolResult.success(mapOf(
            "file" to data?.get("file")
        ))
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: POST /open-apis/drive/v1/files/:file_token/move with { type, folder_token }
    // JS returns: { success: true, task_id (if present), file_token, target_folder_token }
    private suspend fun executeMove(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")
        val folderToken = args["folder_token"] as? String
            ?: return ToolResult.error("Missing required parameter: folder_token")

        Log.i(TAG, "move: file_token=$fileToken, type=$type, folder_token=$folderToken")

        val body = mapOf(
            "type" to type,
            "folder_token" to folderToken
        )

        val result = client.post("/open-apis/drive/v1/files/$fileToken/move", body)

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to move file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val taskId = data?.get("task_id")?.asString
        Log.i(TAG, "move: success${if (taskId != null) ", task_id=$taskId" else ""}")

        val response = mutableMapOf<String, Any?>(
            "success" to true,
            "file_token" to fileToken,
            "target_folder_token" to folderToken
        )
        taskId?.let { response["task_id"] = it }

        return ToolResult.success(response)
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: DELETE /open-apis/drive/v1/files/:file_token with type as query param
    // JS returns: { success: true, task_id (if present), file_token }
    private suspend fun executeDelete(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val type = args["type"] as? String
            ?: return ToolResult.error("Missing required parameter: type")

        Log.i(TAG, "delete: file_token=$fileToken, type=$type")

        val result = client.delete("/open-apis/drive/v1/files/$fileToken?type=$type")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to delete file")
        }

        val data = result.getOrNull()?.getAsJsonObject("data")
        val taskId = data?.get("task_id")?.asString
        Log.i(TAG, "delete: success${if (taskId != null) ", task_id=$taskId" else ""}")

        val response = mutableMapOf<String, Any?>(
            "success" to true,
            "file_token" to fileToken
        )
        taskId?.let { response["task_id"] = it }

        return ToolResult.success(response)
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: upload action — supports file_path (priority) or file_content_base64
    // JS: small files (<=15MB) use upload_all, large files use chunked upload (prepare → part → finish)
    private suspend fun executeUpload(args: Map<String, Any?>): ToolResult {
        val filePath = args["file_path"] as? String
        val fileContentBase64 = args["file_content_base64"] as? String
        val parentNode = args["parent_node"] as? String ?: ""

        val fileBuffer: ByteArray
        val fileName: String
        val fileSize: Int

        // JS: if (p.file_path) { ... } else if (p.file_content_base64) { ... } else { error }
        if (filePath != null) {
            // 优先使用 file_path
            Log.i(TAG, "upload: reading from local file: $filePath")
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return ToolResult.success(mapOf(
                        "error" to "failed to read local file: File not found: $filePath"
                    ))
                }
                fileBuffer = file.readBytes()
                fileName = (args["file_name"] as? String) ?: file.name
                fileSize = (args["size"] as? Number)?.toInt() ?: fileBuffer.size
                Log.i(TAG, "upload: file_name=$fileName, size=$fileSize, parent=${parentNode.ifEmpty { "(root)" }}")
            } catch (e: Exception) {
                // JS: return json({ error: `failed to read local file: ${err.message}` })
                return ToolResult.success(mapOf(
                    "error" to "failed to read local file: ${e.message ?: e.toString()}"
                ))
            }
        } else if (fileContentBase64 != null) {
            // JS: if (!p.file_name || !p.size) return json({ error: '...' })
            val providedFileName = args["file_name"] as? String
            val providedSize = (args["size"] as? Number)?.toInt()
            if (providedFileName == null || providedSize == null) {
                return ToolResult.success(mapOf(
                    "error" to "file_name and size are required when using file_content_base64"
                ))
            }

            Log.i(TAG, "upload: using base64 content, file_name=$providedFileName, size=$providedSize, parent=$parentNode")
            fileBuffer = Base64.decode(fileContentBase64, Base64.DEFAULT)
            fileName = providedFileName
            fileSize = providedSize
        } else {
            // JS: return json({ error: 'either file_path or file_content_base64 is required' })
            return ToolResult.success(mapOf(
                "error" to "either file_path or file_content_base64 is required"
            ))
        }

        // JS: if (fileSize <= SMALL_FILE_THRESHOLD) { upload_all } else { chunked upload }
        if (fileSize <= SMALL_FILE_THRESHOLD) {
            // 小文件：使用一次上传 (upload_all)
            Log.i(TAG, "upload: using upload_all (file size $fileSize <= 15MB)")

            val result = client.uploadFile(
                fileName = fileName,
                parentType = "explorer",
                parentNode = parentNode,
                size = fileSize,
                data = fileBuffer
            )

            if (result.isFailure) {
                return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to upload file")
            }

            val data = result.getOrNull()?.getAsJsonObject("data")
            Log.i(TAG, "upload: file_token=${data?.get("file_token")}")

            return ToolResult.success(mapOf(
                "file_token" to data?.get("file_token"),
                "file_name" to fileName,
                "size" to fileSize
            ))
        } else {
            // 大文件：使用分片上传
            Log.i(TAG, "upload: using chunked upload (file size $fileSize > 15MB)")

            // Step 1: 预上传 (uploadPrepare)
            Log.i(TAG, "upload: step 1 - prepare upload")
            val prepareResult = client.uploadPrepare(
                fileName = fileName,
                parentType = "explorer",
                parentNode = parentNode,
                size = fileSize
            )

            if (prepareResult.isFailure) {
                return ToolResult.success(mapOf("error" to "pre-upload failed: ${prepareResult.exceptionOrNull()?.message}"))
            }

            val prepareData = prepareResult.getOrNull()?.getAsJsonObject("data")
                ?: return ToolResult.success(mapOf("error" to "pre-upload failed: empty response"))

            val uploadId = prepareData.get("upload_id")?.asString
                ?: return ToolResult.success(mapOf("error" to "pre-upload failed: missing upload_id"))
            val blockSize = prepareData.get("block_size")?.asInt
                ?: return ToolResult.success(mapOf("error" to "pre-upload failed: missing block_size"))
            val blockNum = prepareData.get("block_num")?.asInt
                ?: return ToolResult.success(mapOf("error" to "pre-upload failed: missing block_num"))

            Log.i(TAG, "upload: got upload_id=$uploadId, block_num=$blockNum, block_size=$blockSize")

            // Step 2: 上传分片 (uploadPart)
            Log.i(TAG, "upload: step 2 - uploading $blockNum chunks")
            for (seq in 0 until blockNum) {
                val start = seq * blockSize
                val end = minOf(start + blockSize, fileSize)
                val chunkBuffer = fileBuffer.copyOfRange(start, end)

                Log.i(TAG, "upload: uploading chunk ${seq + 1}/$blockNum (${chunkBuffer.size} bytes)")

                val partResult = client.uploadPart(
                    uploadId = uploadId,
                    seq = seq,
                    size = chunkBuffer.size,
                    data = chunkBuffer
                )

                if (partResult.isFailure) {
                    return ToolResult.error("Failed to upload chunk ${seq + 1}: ${partResult.exceptionOrNull()?.message}")
                }

                Log.i(TAG, "upload: chunk ${seq + 1}/$blockNum uploaded successfully")
            }

            // Step 3: 完成上传 (uploadFinish)
            Log.i(TAG, "upload: step 3 - finish upload")
            val finishResult = client.uploadFinish(
                uploadId = uploadId,
                blockNum = blockNum
            )

            if (finishResult.isFailure) {
                return ToolResult.error(finishResult.exceptionOrNull()?.message ?: "Failed to finish upload")
            }

            val finishData = finishResult.getOrNull()?.getAsJsonObject("data")
            Log.i(TAG, "upload: file_token=${finishData?.get("file_token")}")

            return ToolResult.success(mapOf(
                "file_token" to finishData?.get("file_token"),
                "file_name" to fileName,
                "size" to fileSize,
                "upload_method" to "chunked",
                "chunks_uploaded" to blockNum
            ))
        }
    }

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    // JS: GET /open-apis/drive/v1/files/:file_token/download
    // JS: if output_path → save to local file, else → return base64
    private suspend fun executeDownload(args: Map<String, Any?>): ToolResult {
        val fileToken = args["file_token"] as? String
            ?: return ToolResult.error("Missing required parameter: file_token")
        val outputPath = args["output_path"] as? String

        Log.i(TAG, "download: file_token=$fileToken")

        val result = client.downloadRaw("/open-apis/drive/v1/files/$fileToken/download")

        if (result.isFailure) {
            return ToolResult.error(result.exceptionOrNull()?.message ?: "Failed to download file")
        }

        val fileBuffer = result.getOrNull()
            ?: return ToolResult.error("Empty download response")

        Log.i(TAG, "download: file size=${fileBuffer.size} bytes")

        // JS: if (p.output_path) { save to file } else { return base64 }
        if (outputPath != null) {
            try {
                val file = File(outputPath)
                file.parentFile?.mkdirs()
                file.writeBytes(fileBuffer)
                Log.i(TAG, "download: saved to $outputPath")
                return ToolResult.success(mapOf(
                    "saved_path" to outputPath,
                    "size" to fileBuffer.size
                ))
            } catch (e: Exception) {
                // JS: return json({ error: `failed to save file: ${err.message}` })
                return ToolResult.success(mapOf(
                    "error" to "failed to save file: ${e.message ?: e.toString()}"
                ))
            }
        } else {
            // JS: const base64Content = fileBuffer.toString('base64'); return json(...)
            val base64Content = Base64.encodeToString(fileBuffer, Base64.NO_WRAP)
            return ToolResult.success(mapOf(
                "file_content_base64" to base64Content,
                "size" to fileBuffer.size
            ))
        }
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
                        enum = listOf("list", "get_meta", "copy", "move", "delete", "upload", "download")
                    ),
                    "file_token" to PropertySchema(
                        type = "string",
                        description = "文件 token（copy/move/delete/download 操作必填）"
                    ),
                    "folder_token" to PropertySchema(
                        type = "string",
                        description = "文件夹 token（list 操作可选，copy 操作可选指定目标文件夹，move 操作必填）"
                    ),
                    "name" to PropertySchema(
                        type = "string",
                        description = "目标文件名（copy 操作必填）"
                    ),
                    "type" to PropertySchema(
                        type = "string",
                        description = "文档类型（copy/move/delete 操作必填）",
                        enum = listOf("doc", "sheet", "file", "bitable", "docx", "folder", "mindnote", "slides")
                    ),
                    "request_docs" to PropertySchema(
                        type = "array",
                        description = "要查询的文档列表（get_meta 操作必填，批量查询，最多 50 个）。示例：[{doc_token: 'Z1FjxxxxxxxxxxxxxxxxxxxtnAc', doc_type: 'sheet'}]",
                        items = PropertySchema(
                            type = "object",
                            description = "文档查询项",
                            properties = mapOf(
                                "doc_token" to PropertySchema("string", "文档 token（从浏览器 URL 中获取，如 spreadsheet_token、doc_token 等）"),
                                "doc_type" to PropertySchema(
                                    "string",
                                    "文档类型：doc、sheet、file、bitable、docx、folder、mindnote、slides",
                                    enum = listOf("doc", "sheet", "file", "bitable", "docx", "folder", "mindnote", "slides")
                                )
                            )
                        )
                    ),
                    "parent_node" to PropertySchema(
                        type = "string",
                        description = "父节点 token（upload 操作可选）。explorer 类型填文件夹 token，bitable 类型填 app_token。不填写或填空字符串时，上传到云空间根目录"
                    ),
                    "file_path" to PropertySchema(
                        type = "string",
                        description = "本地文件路径（upload 操作，与 file_content_base64 二选一）。优先使用此参数，会自动读取文件内容、计算大小、提取文件名。"
                    ),
                    "file_content_base64" to PropertySchema(
                        type = "string",
                        description = "文件内容的 Base64 编码（upload 操作，与 file_path 二选一）。当不提供 file_path 时使用。"
                    ),
                    "file_name" to PropertySchema(
                        type = "string",
                        description = "文件名（upload 操作可选）。如果提供了 file_path，会自动从路径中提取文件名；如果使用 file_content_base64，则必须提供此参数。"
                    ),
                    "size" to PropertySchema(
                        type = "integer",
                        description = "文件大小（字节，upload 操作可选）。如果提供了 file_path，会自动计算；如果使用 file_content_base64，则必须提供此参数。"
                    ),
                    "output_path" to PropertySchema(
                        type = "string",
                        description = "本地保存的完整文件路径（download 操作可选）。必须包含文件名和扩展名，例如 '/tmp/file.pdf'。如果不提供，则返回 Base64 编码的文件内容。"
                    ),
                    "page_size" to PropertySchema(
                        type = "integer",
                        description = "分页大小（默认 200，最大 200）"
                    ),
                    "page_token" to PropertySchema(
                        type = "string",
                        description = "分页标记。首次请求无需填写"
                    ),
                    "order_by" to PropertySchema(
                        type = "string",
                        description = "排序方式：EditedTime（编辑时间）、CreatedTime（创建时间）",
                        enum = listOf("EditedTime", "CreatedTime")
                    ),
                    "direction" to PropertySchema(
                        type = "string",
                        description = "排序方向：ASC（升序）、DESC（降序）",
                        enum = listOf("ASC", "DESC")
                    )
                ),
                required = listOf("action")
            )
        )
    )
}
