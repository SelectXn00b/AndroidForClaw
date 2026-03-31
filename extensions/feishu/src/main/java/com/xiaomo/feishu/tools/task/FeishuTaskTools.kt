package com.xiaomo.feishu.tools.task

/**
 * Feishu Task tool set.
 * Line-by-line translation from @larksuite/openclaw-lark:
 *   - task/task.js
 *   - task/tasklist.js
 *   - task/subtask.js
 *   - task/comment.js
 *   - helpers.js (parseTimeToTimestamp, parseTimeToTimestampMs, unixTimestampToISO8601, pad2)
 */

import android.util.Log
import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
// Aggregator
// ─────────────────────────────────────────────────────────────

class FeishuTaskTools(config: FeishuConfig, client: FeishuClient) {
    private val taskTool = FeishuTaskTaskTool(config, client)
    private val tasklistTool = FeishuTaskTasklistTool(config, client)
    private val subtaskTool = FeishuTaskSubtaskTool(config, client)
    private val commentTool = FeishuTaskCommentTool(config, client)

    fun getAllTools(): List<FeishuToolBase> {
        return listOf(taskTool, tasklistTool, subtaskTool, commentTool)
    }

    fun getToolDefinitions(): List<ToolDefinition> {
        return getAllTools().filter { it.isEnabled() }.map { it.getToolDefinition() }
    }
}

// ─────────────────────────────────────────────────────────────
// Helper: build query string from param pairs
// ─────────────────────────────────────────────────────────────

private fun buildQuery(vararg pairs: Pair<String, Any?>): String {
    val parts = pairs.mapNotNull { (k, v) ->
        if (v != null) "$k=$v" else null
    }
    return if (parts.isNotEmpty()) "?" + parts.joinToString("&") else ""
}

// ---------------------------------------------------------------------------
// Shared helpers — line-by-line from helpers.js
// ---------------------------------------------------------------------------

private const val SHANGHAI_UTC_OFFSET_HOURS = 8
private const val SHANGHAI_OFFSET_SUFFIX = "+08:00"

/**
 * Pad a number to 2 digits.
 * @aligned openclaw-lark v2026.3.30 — line-by-line
 */
private fun pad2(value: Int): String {
    return value.toString().padStart(2, '0')
}

/**
 * Parse time string to Unix timestamp (seconds).
 *
 * Supports:
 * 1. ISO 8601 / RFC 3339 (with timezone): "2024-01-01T00:00:00+08:00"
 * 2. Without timezone (defaults to Beijing UTC+8):
 *    - "2026-02-25 14:30"
 *    - "2026-02-25 14:30:00"
 *    - "2026-02-25T14:30:00"
 *
 * @aligned openclaw-lark v2026.3.30 — line-by-line
 */
private fun parseTimeToTimestamp(input: String): String? {
    return try {
        val trimmed = input.trim()

        // 检查是否包含时区信息（Z 或 +/- 偏移）
        val hasTimezone = Regex("[Zz]$|[+-]\\d{2}:\\d{2}$").containsMatchIn(trimmed)

        if (hasTimezone) {
            // 有时区信息，直接解析
            val date = parseISO8601(trimmed) ?: return null
            (date / 1000).toString()
        } else {
            // 没有时区信息，当作北京时间处理
            val normalized = trimmed.replace('T', ' ')
            val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?$")
                .find(normalized)

            if (match == null) {
                // 尝试直接解析（可能是其他 ISO 8601 格式）
                val date = parseISO8601(trimmed) ?: return null
                (date / 1000).toString()
            } else {
                val (year, month, day, hour, minute) = match.destructured
                val second = match.groupValues[6].ifEmpty { "0" }
                // 当作北京时间（UTC+8），转换为 UTC
                val utcMs = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
                    set(year.toInt(), month.toInt() - 1, day.toInt(),
                        hour.toInt() - 8, minute.toInt(), second.toInt())
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                (utcMs / 1000).toString()
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse time string to Unix timestamp (milliseconds).
 *
 * Same formats as parseTimeToTimestamp, but returns milliseconds.
 *
 * @aligned openclaw-lark v2026.3.30 — line-by-line
 */
private fun parseTimeToTimestampMs(input: String): String? {
    return try {
        val trimmed = input.trim()

        // 检查是否包含时区信息（Z 或 +/- 偏移）
        val hasTimezone = Regex("[Zz]$|[+-]\\d{2}:\\d{2}$").containsMatchIn(trimmed)

        if (hasTimezone) {
            // 有时区信息，直接解析
            val date = parseISO8601(trimmed) ?: return null
            date.toString()
        } else {
            // 没有时区信息，当作北京时间处理
            val normalized = trimmed.replace('T', ' ')
            val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?$")
                .find(normalized)

            if (match == null) {
                // 尝试直接解析（可能是其他 ISO 8601 格式）
                val date = parseISO8601(trimmed) ?: return null
                date.toString()
            } else {
                val (year, month, day, hour, minute) = match.destructured
                val second = match.groupValues[6].ifEmpty { "0" }
                // 当作北京时间（UTC+8），转换为 UTC
                val utcMs = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
                    set(year.toInt(), month.toInt() - 1, day.toInt(),
                        hour.toInt() - 8, minute.toInt(), second.toInt())
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                utcMs.toString()
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Convert a Unix timestamp (seconds or milliseconds) to ISO 8601 string
 * in the Asia/Shanghai timezone.
 *
 * Auto-detects seconds vs milliseconds based on magnitude.
 *
 * @returns e.g. "2026-02-25T14:30:00+08:00", or null on invalid input
 * @aligned openclaw-lark v2026.3.30 — line-by-line
 */
private fun unixTimestampToISO8601(raw: Any?): String? {
    if (raw == null) return null
    val text = when (raw) {
        is Number -> raw.toString()
        else -> raw.toString().trim()
    }
    if (!Regex("^-?\\d+$").matches(text)) return null
    val num = text.toLongOrNull() ?: return null

    val utcMs = if (kotlin.math.abs(num) >= 1_000_000_000_000L) num else num * 1000

    val beijingMs = utcMs + SHANGHAI_UTC_OFFSET_HOURS * 60 * 60 * 1000L
    val cal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = beijingMs
    }

    val year = cal.get(java.util.Calendar.YEAR)
    val month = pad2(cal.get(java.util.Calendar.MONTH) + 1)
    val day = pad2(cal.get(java.util.Calendar.DAY_OF_MONTH))
    val hour = pad2(cal.get(java.util.Calendar.HOUR_OF_DAY))
    val minute = pad2(cal.get(java.util.Calendar.MINUTE))
    val second = pad2(cal.get(java.util.Calendar.SECOND))

    return "$year-$month-${day}T$hour:$minute:$second$SHANGHAI_OFFSET_SUFFIX"
}

/**
 * Helper to parse ISO 8601 / RFC 3339 strings to epoch milliseconds.
 * Returns null on parse failure.
 */
private fun parseISO8601(input: String): Long? {
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                // Formats without timezone info: default to Beijing time
                if (!fmt.contains("X") && !fmt.endsWith("Z")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                }
                val date = sdf.parse(input) ?: continue
                return date.time
            } catch (_: Exception) {
                // try next
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

// ─────────────────────────────────────────────────────────────
// Helper: parse due/start object from args
// due/start are OBJECTS: {timestamp: string, is_all_day?: boolean}
// parse timestamp via parseTimeToTimestampMs
// @aligned openclaw-lark v2026.3.30 — line-by-line
// ─────────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun parseDueStartObject(raw: Any?): Map<String, Any>? {
    if (raw == null) return null
    // If it's a structured object with timestamp field
    if (raw is Map<*, *>) {
        val tsRaw = raw["timestamp"] as? String ?: return null
        val tsMs = parseTimeToTimestampMs(tsRaw) ?: return null
        val isAllDay = raw["is_all_day"] as? Boolean ?: false
        return mapOf("timestamp" to tsMs, "is_all_day" to isAllDay)
    }
    // If it's a plain string (legacy format), convert to object
    if (raw is String) {
        val tsMs = parseTimeToTimestampMs(raw) ?: return null
        return mapOf("timestamp" to tsMs, "is_all_day" to false)
    }
    return null
}

// ─────────────────────────────────────────────────────────────
// 1. FeishuTaskTaskTool — feishu_task_task
// Translated from: task/task.js
// Actions: create, get, list, patch
// ─────────────────────────────────────────────────────────────

class FeishuTaskTaskTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_task"
    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description =
        "【以用户身份】飞书任务管理工具。用于创建、查询、更新任务。" +
        "Actions: create（创建任务）, get（获取任务详情）, list（查询任务列表，仅返回我负责的任务）, patch（更新任务）。" +
        "时间参数使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            val basePath = "/open-apis/task/v2/tasks"

            when (action) {
                // -----------------------------------------------------------------
                // CREATE TASK
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "create" -> {
                    val summary = args["summary"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: summary")
                    Log.i(TAG, "create: summary=$summary")

                    val taskData = mutableMapOf<String, Any>("summary" to summary)

                    val description = args["description"] as? String
                    if (description != null) taskData["description"] = description

                    // Handle due time conversion
                    // due is OBJECT: {timestamp: string, is_all_day?: boolean}
                    if (args["due"] != null) {
                        val dueObj = parseDueStartObject(args["due"])
                        if (dueObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "due 时间格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'，例如 '2026-02-25 18:00'。",
                                "received" to args["due"]
                            ))
                        }
                        taskData["due"] = dueObj
                        Log.i(TAG, "create: due time converted")
                    }

                    // Handle start time conversion
                    if (args["start"] != null) {
                        val startObj = parseDueStartObject(args["start"])
                        if (startObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "start 时间格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。",
                                "received" to args["start"]
                            ))
                        }
                        taskData["start"] = startObj
                    }

                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    if (members != null) taskData["members"] = members

                    val repeatRule = args["repeat_rule"] as? String
                    if (repeatRule != null) taskData["repeat_rule"] = repeatRule

                    @Suppress("UNCHECKED_CAST")
                    val tasklists = args["tasklists"] as? List<Map<String, Any?>>
                    if (tasklists != null) taskData["tasklists"] = tasklists

                    val userIdType = args["user_id_type"] as? String ?: "open_id"
                    val query = buildQuery("user_id_type" to userIdType)
                    val result = client.post("$basePath$query", taskData)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    val taskGuid = data?.getAsJsonObject("data")?.getAsJsonObject("task")?.get("guid")?.asString
                    Log.i(TAG, "create: task created: task_guid=$taskGuid")
                    ToolResult.success(mapOf("task" to data?.getAsJsonObject("data")?.getAsJsonObject("task")))
                }

                // -----------------------------------------------------------------
                // GET TASK
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "get" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    Log.i(TAG, "get: task_guid=$taskGuid")

                    val userIdType = args["user_id_type"] as? String ?: "open_id"
                    val query = buildQuery("user_id_type" to userIdType)
                    val result = client.get("$basePath/$taskGuid$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.i(TAG, "get: retrieved task $taskGuid")
                    ToolResult.success(mapOf("task" to data?.getAsJsonObject("data")?.getAsJsonObject("task")))
                }

                // -----------------------------------------------------------------
                // LIST TASKS
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    val completed = args["completed"] as? Boolean
                    val userIdType = args["user_id_type"] as? String ?: "open_id"
                    Log.i(TAG, "list: page_size=${pageSize ?: 50}, completed=${completed ?: false}")

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "completed" to completed,
                        "user_id_type" to userIdType
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val data = json?.getAsJsonObject("data")
                    val items = data?.getAsJsonArray("items")
                    Log.i(TAG, "list: returned ${items?.size() ?: 0} tasks")
                    ToolResult.success(mapOf(
                        "tasks" to items,
                        "has_more" to (data?.get("has_more")?.asBoolean ?: false),
                        "page_token" to data?.get("page_token")?.asString
                    ))
                }

                // -----------------------------------------------------------------
                // PATCH TASK
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // CRITICAL: patch wraps body as {task: updateData, update_fields: [...]}
                // -----------------------------------------------------------------
                "patch" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    Log.i(TAG, "patch: task_guid=$taskGuid")

                    val updateData = mutableMapOf<String, Any>()

                    val summary = args["summary"] as? String
                    if (summary != null) updateData["summary"] = summary

                    // description !== undefined check (JS uses !== undefined, not null check)
                    if (args.containsKey("description")) {
                        val desc = args["description"]
                        if (desc != null) updateData["description"] = desc
                    }

                    // Handle due time conversion
                    if (args["due"] != null) {
                        val dueObj = parseDueStartObject(args["due"])
                        if (dueObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "due 时间格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。",
                                "received" to args["due"]
                            ))
                        }
                        updateData["due"] = dueObj
                    }

                    // Handle start time conversion
                    if (args["start"] != null) {
                        val startObj = parseDueStartObject(args["start"])
                        if (startObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "start 时间格式错误！必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'。",
                                "received" to args["start"]
                            ))
                        }
                        updateData["start"] = startObj
                    }

                    // Handle completed_at conversion
                    val completedAt = args["completed_at"] as? String
                    if (completedAt != null) {
                        when {
                            // 特殊值：反完成（设为未完成）
                            completedAt == "0" -> {
                                updateData["completed_at"] = "0"
                            }
                            // 数字字符串时间戳（直通）
                            Regex("^\\d+$").matches(completedAt) -> {
                                updateData["completed_at"] = completedAt
                            }
                            // 时间格式字符串（需要转换）
                            else -> {
                                val completedTs = parseTimeToTimestampMs(completedAt)
                                if (completedTs == null) {
                                    return@withContext ToolResult.success(mapOf(
                                        "error" to "completed_at 格式错误！支持：1) ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'；2) '0'（反完成）；3) 毫秒时间戳字符串。",
                                        "received" to completedAt
                                    ))
                                }
                                updateData["completed_at"] = completedTs
                            }
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    if (members != null) updateData["members"] = members

                    val repeatRule = args["repeat_rule"] as? String
                    if (repeatRule != null) updateData["repeat_rule"] = repeatRule

                    // Build update_fields list (required by Task API)
                    val updateFields = updateData.keys.toList()

                    val userIdType = args["user_id_type"] as? String ?: "open_id"
                    val body = mapOf(
                        "task" to updateData,
                        "update_fields" to updateFields
                    )
                    val query = buildQuery("user_id_type" to userIdType)
                    val result = client.patch("$basePath/$taskGuid$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val data = result.getOrNull()
                    Log.i(TAG, "patch: task $taskGuid updated")
                    ToolResult.success(mapOf("task" to data?.getAsJsonObject("data")?.getAsJsonObject("task")))
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, patch")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_task_task failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "get", "list", "patch")
                    ),
                    "task_guid" to PropertySchema("string", "Task GUID（get/patch 必填）"),
                    "summary" to PropertySchema("string", "任务标题（create 必填，patch 可选）"),
                    "current_user_id" to PropertySchema("string", "当前用户的 open_id（强烈建议，从消息上下文的 SenderId 获取）。如果 members 中不包含此用户，工具会自动添加为 follower，确保创建者可以编辑任务。"),
                    "description" to PropertySchema("string", "任务描述（可选）"),
                    "due" to PropertySchema(
                        "object", "截止时间对象",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "截止时间（ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'）"),
                            "is_all_day" to PropertySchema("boolean", "是否为全天任务")
                        )
                    ),
                    "start" to PropertySchema(
                        "object", "开始时间对象",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "开始时间（ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'）"),
                            "is_all_day" to PropertySchema("boolean", "是否为全天")
                        )
                    ),
                    "completed_at" to PropertySchema("string", "完成时间。支持三种格式：1) ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'（设为已完成）；2) '0'（反完成，任务变为未完成）；3) 毫秒时间戳字符串。"),
                    "completed" to PropertySchema("boolean", "是否筛选已完成任务（list 可选）"),
                    "members" to PropertySchema(
                        "array", "任务成员列表（assignee=负责人，follower=关注人）",
                        items = PropertySchema("object", "成员对象 {id: open_id, role?: 'assignee'|'follower'}")
                    ),
                    "repeat_rule" to PropertySchema("string", "重复规则（RRULE 格式）"),
                    "tasklists" to PropertySchema(
                        "array", "任务所属清单列表",
                        items = PropertySchema("object", "清单对象 {tasklist_guid, section_guid?}")
                    ),
                    "user_id_type" to PropertySchema("string", "用户 ID 类型", enum = listOf("open_id", "union_id", "user_id")),
                    "page_size" to PropertySchema("number", "每页数量（默认 50，最大 100）"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("action")
            )
        )
    )

    companion object {
        private const val TAG = "FeishuTaskTask"
    }
}

// ─────────────────────────────────────────────────────────────
// 2. FeishuTaskTasklistTool — feishu_task_tasklist
// Translated from: task/tasklist.js
// Actions: create, get, list, tasks, patch, add_members
// ─────────────────────────────────────────────────────────────

class FeishuTaskTasklistTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_tasklist"
    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description =
        "【以用户身份】飞书任务清单管理工具。当用户要求创建/查询/管理清单、查看清单内的任务时使用。" +
        "Actions: create（创建清单）, get（获取清单详情）, list（列出所有可读取的清单，包括我创建的和他人共享给我的）, " +
        "tasks（列出清单内的任务）, patch（更新清单）, add_members（添加成员）。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            val basePath = "/open-apis/task/v2/tasklists"

            when (action) {
                // -----------------------------------------------------------------
                // CREATE
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // Transform members: add type='user', default role='editor'
                // -----------------------------------------------------------------
                "create" -> {
                    val name = args["name"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: name")
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    Log.i(TAG, "create: name=$name, members_count=${members?.size ?: 0}")

                    val data = mutableMapOf<String, Any>("name" to name)

                    // 转换成员格式
                    if (members != null && members.isNotEmpty()) {
                        data["members"] = members.map { m ->
                            mapOf(
                                "id" to (m["id"] ?: ""),
                                "type" to "user",
                                "role" to (m["role"] ?: "editor")
                            )
                        }
                    }

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath$query", data)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val tasklist = json?.getAsJsonObject("data")?.getAsJsonObject("tasklist")
                    Log.i(TAG, "create: created tasklist ${tasklist?.get("guid")?.asString}")
                    ToolResult.success(mapOf("tasklist" to tasklist))
                }

                // -----------------------------------------------------------------
                // GET
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "get" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    Log.i(TAG, "get: tasklist_guid=$tasklistGuid")

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.get("$basePath/$tasklistGuid$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    Log.i(TAG, "get: returned tasklist $tasklistGuid")
                    ToolResult.success(mapOf("tasklist" to json?.getAsJsonObject("data")?.getAsJsonObject("tasklist")))
                }

                // -----------------------------------------------------------------
                // LIST
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "list" -> {
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    Log.i(TAG, "list: page_size=${pageSize ?: 50}")

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val data = json?.getAsJsonObject("data")
                    val items = data?.getAsJsonArray("items")
                    Log.i(TAG, "list: returned ${items?.size() ?: 0} tasklists")
                    ToolResult.success(mapOf(
                        "tasklists" to items,
                        "has_more" to (data?.get("has_more")?.asBoolean ?: false),
                        "page_token" to data?.get("page_token")?.asString
                    ))
                }

                // -----------------------------------------------------------------
                // TASKS - 列出清单内的任务
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // completed is boolean
                // -----------------------------------------------------------------
                "tasks" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    val completedRaw = args["completed"]
                    val completed: Boolean? = when (completedRaw) {
                        is Boolean -> completedRaw
                        is String -> completedRaw.toBooleanStrictOrNull()
                        else -> null
                    }
                    Log.i(TAG, "tasks: tasklist_guid=$tasklistGuid, completed=${completed ?: "all"}")

                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "completed" to completed,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath/$tasklistGuid/tasks$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val data = json?.getAsJsonObject("data")
                    val items = data?.getAsJsonArray("items")
                    Log.i(TAG, "tasks: returned ${items?.size() ?: 0} tasks")
                    ToolResult.success(mapOf(
                        "tasks" to items,
                        "has_more" to (data?.get("has_more")?.asBoolean ?: false),
                        "page_token" to data?.get("page_token")?.asString
                    ))
                }

                // -----------------------------------------------------------------
                // PATCH
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // wraps as {tasklist: {name}, update_fields: ['name']}
                // -----------------------------------------------------------------
                "patch" -> {
                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    val name = args["name"] as? String
                    Log.i(TAG, "patch: tasklist_guid=$tasklistGuid, name=$name")

                    // 飞书 Task API 要求特殊的更新格式
                    val tasklistData = mutableMapOf<String, Any>()
                    val updateFields = mutableListOf<String>()

                    if (name != null) {
                        tasklistData["name"] = name
                        updateFields.add("name")
                    }

                    if (updateFields.isEmpty()) {
                        return@withContext ToolResult.success(mapOf("error" to "No fields to update"))
                    }

                    val body = mapOf(
                        "tasklist" to tasklistData,
                        "update_fields" to updateFields
                    )

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.patch("$basePath/$tasklistGuid$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    Log.i(TAG, "patch: updated tasklist $tasklistGuid")
                    ToolResult.success(mapOf("tasklist" to json?.getAsJsonObject("data")?.getAsJsonObject("tasklist")))
                }

                // -----------------------------------------------------------------
                // ADD_MEMBERS
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // endpoint: /open-apis/task/v2/tasklists/:guid/add_members
                // Transform members: add type='user', default role='editor'
                // -----------------------------------------------------------------
                "add_members" -> {
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    if (members == null || members.isEmpty()) {
                        return@withContext ToolResult.success(mapOf("error" to "members is required and cannot be empty"))
                    }

                    val tasklistGuid = args["tasklist_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: tasklist_guid")
                    Log.i(TAG, "add_members: tasklist_guid=$tasklistGuid, members_count=${members.size}")

                    val memberData = members.map { m ->
                        mapOf(
                            "id" to (m["id"] ?: ""),
                            "type" to "user",
                            "role" to (m["role"] ?: "editor")
                        )
                    }

                    val body = mapOf("members" to memberData)
                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath/$tasklistGuid/add_members$query", body)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    Log.i(TAG, "add_members: added ${members.size} members to tasklist $tasklistGuid")
                    ToolResult.success(mapOf("tasklist" to json?.getAsJsonObject("data")?.getAsJsonObject("tasklist")))
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, get, list, tasks, patch, add_members")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_task_tasklist failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "get", "list", "tasks", "patch", "add_members")
                    ),
                    "tasklist_guid" to PropertySchema("string", "清单 GUID（get/tasks/patch/add_members 必填）"),
                    "name" to PropertySchema("string", "清单名称（create 必填，patch 可选）"),
                    "members" to PropertySchema(
                        "array", "清单成员列表（editor=可编辑，viewer=可查看）。注意：创建人自动成为 owner",
                        items = PropertySchema("object", "成员对象 {id: open_id, role?: 'editor'|'viewer'}")
                    ),
                    "completed" to PropertySchema("boolean", "是否只返回已完成的任务（tasks 可选，默认返回所有）"),
                    "page_size" to PropertySchema("number", "每页数量，默认 50，最大 100"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("action")
            )
        )
    )

    companion object {
        private const val TAG = "FeishuTaskTasklist"
    }
}

// ─────────────────────────────────────────────────────────────
// 3. FeishuTaskSubtaskTool — feishu_task_subtask
// Translated from: task/subtask.js
// Actions: create, list
// ─────────────────────────────────────────────────────────────

class FeishuTaskSubtaskTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_subtask"
    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description =
        "【以用户身份】飞书任务的子任务管理工具。当用户要求创建子任务、查询任务的子任务列表时使用。" +
        "Actions: create（创建子任务）, list（列出任务的所有子任务）。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                // -----------------------------------------------------------------
                // CREATE
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // Members: add type='user', default role='assignee'
                // due/start same object structure as task.js
                // -----------------------------------------------------------------
                "create" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    val summary = args["summary"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: summary")
                    Log.i(TAG, "create: task_guid=$taskGuid, summary=$summary")

                    val data = mutableMapOf<String, Any>("summary" to summary)

                    val description = args["description"] as? String
                    if (description != null) data["description"] = description

                    // 转换截止时间
                    if (args["due"] != null) {
                        val dueObj = parseDueStartObject(args["due"])
                        if (dueObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "时间格式错误！due.timestamp 必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'，当前值：${(args["due"] as? Map<*, *>)?.get("timestamp")}"
                            ))
                        }
                        data["due"] = dueObj
                    }

                    // 转换开始时间
                    if (args["start"] != null) {
                        val startObj = parseDueStartObject(args["start"])
                        if (startObj == null) {
                            return@withContext ToolResult.success(mapOf(
                                "error" to "时间格式错误！start.timestamp 必须使用ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'，当前值：${(args["start"] as? Map<*, *>)?.get("timestamp")}"
                            ))
                        }
                        data["start"] = startObj
                    }

                    // 转换成员格式: add type='user', default role='assignee'
                    @Suppress("UNCHECKED_CAST")
                    val members = args["members"] as? List<Map<String, Any?>>
                    if (members != null && members.isNotEmpty()) {
                        data["members"] = members.map { m ->
                            mapOf(
                                "id" to (m["id"] ?: ""),
                                "type" to "user",
                                "role" to (m["role"] ?: "assignee")
                            )
                        }
                    }

                    val basePath = "/open-apis/task/v2/tasks/$taskGuid/subtasks"
                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("$basePath$query", data)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val subtask = json?.getAsJsonObject("data")?.getAsJsonObject("subtask")
                    Log.i(TAG, "create: created subtask ${subtask?.get("guid")?.asString ?: "unknown"}")
                    ToolResult.success(mapOf("subtask" to subtask))
                }

                // -----------------------------------------------------------------
                // LIST
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "list" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    Log.i(TAG, "list: task_guid=$taskGuid, page_size=${pageSize ?: 50}")

                    val basePath = "/open-apis/task/v2/tasks/$taskGuid/subtasks"
                    val query = buildQuery(
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("$basePath$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val data = json?.getAsJsonObject("data")
                    val items = data?.getAsJsonArray("items")
                    Log.i(TAG, "list: returned ${items?.size() ?: 0} subtasks")
                    ToolResult.success(mapOf(
                        "subtasks" to items,
                        "has_more" to (data?.get("has_more")?.asBoolean ?: false),
                        "page_token" to data?.get("page_token")?.asString
                    ))
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_task_subtask failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "list")
                    ),
                    "task_guid" to PropertySchema("string", "父任务 GUID"),
                    "summary" to PropertySchema("string", "子任务标题（create 必填）"),
                    "description" to PropertySchema("string", "子任务描述（create 可选）"),
                    "due" to PropertySchema(
                        "object", "截止时间对象",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "截止时间（ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'）"),
                            "is_all_day" to PropertySchema("boolean", "是否为全天任务")
                        )
                    ),
                    "start" to PropertySchema(
                        "object", "开始时间对象",
                        properties = mapOf(
                            "timestamp" to PropertySchema("string", "开始时间（ISO 8601 / RFC 3339 格式（包含时区），例如 '2024-01-01T00:00:00+08:00'）"),
                            "is_all_day" to PropertySchema("boolean", "是否为全天")
                        )
                    ),
                    "members" to PropertySchema(
                        "array", "子任务成员列表（assignee=负责人，follower=关注人）",
                        items = PropertySchema("object", "成员对象 {id: open_id, role?: 'assignee'|'follower'}")
                    ),
                    "page_size" to PropertySchema("number", "每页数量，默认 50，最大 100"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("action", "task_guid")
            )
        )
    )

    companion object {
        private const val TAG = "FeishuTaskSubtask"
    }
}

// ─────────────────────────────────────────────────────────────
// 4. FeishuTaskCommentTool — feishu_task_comment
// Translated from: task/comment.js
// Actions: create, list, get
// ─────────────────────────────────────────────────────────────

class FeishuTaskCommentTool(
    config: FeishuConfig,
    client: FeishuClient
) : FeishuToolBase(config, client) {

    override val name = "feishu_task_comment"
    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override val description =
        "【以用户身份】飞书任务评论管理工具。当用户要求添加/查询任务评论、回复评论时使用。" +
        "Actions: create（添加评论）, list（列出任务的所有评论）, get（获取单个评论详情）。"

    override fun isEnabled() = config.enableTaskTools

    // @aligned openclaw-lark v2026.3.30 — line-by-line
    override suspend fun execute(args: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val action = args["action"] as? String
                ?: return@withContext ToolResult.error("Missing required parameter: action")

            when (action) {
                // -----------------------------------------------------------------
                // CREATE
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // CRITICAL: POST /open-apis/task/v2/comments with resource_type='task', resource_id in body
                // -----------------------------------------------------------------
                "create" -> {
                    val taskGuid = args["task_guid"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: task_guid")
                    val content = args["content"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: content")
                    val replyToCommentId = args["reply_to_comment_id"] as? String
                    Log.i(TAG, "create: task_guid=$taskGuid, reply_to=${replyToCommentId ?: "none"}")

                    val data = mutableMapOf<String, Any>(
                        "content" to content,
                        "resource_type" to "task",
                        "resource_id" to taskGuid
                    )
                    if (replyToCommentId != null) {
                        data["reply_to_comment_id"] = replyToCommentId
                    }

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.post("/open-apis/task/v2/comments$query", data)
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val comment = json?.getAsJsonObject("data")?.getAsJsonObject("comment")
                    Log.i(TAG, "create: created comment ${comment?.get("id")?.asString}")
                    ToolResult.success(mapOf("comment" to comment))
                }

                // -----------------------------------------------------------------
                // LIST
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // CRITICAL: GET /open-apis/task/v2/comments with resource_type=task&resource_id=... as query params
                // -----------------------------------------------------------------
                "list" -> {
                    val resourceId = args["resource_id"] as? String
                        ?: (args["task_guid"] as? String)
                        ?: return@withContext ToolResult.error("Missing required parameter: resource_id or task_guid")
                    val direction = args["direction"] as? String
                    val pageSize = (args["page_size"] as? Number)?.toInt()
                    val pageToken = args["page_token"] as? String
                    Log.i(TAG, "list: resource_id=$resourceId, direction=${direction ?: "asc"}, page_size=${pageSize ?: 50}")

                    val query = buildQuery(
                        "resource_type" to "task",
                        "resource_id" to resourceId,
                        "direction" to direction,
                        "page_size" to pageSize,
                        "page_token" to pageToken,
                        "user_id_type" to "open_id"
                    )
                    val result = client.get("/open-apis/task/v2/comments$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    val data = json?.getAsJsonObject("data")
                    val items = data?.getAsJsonArray("items")
                    Log.i(TAG, "list: returned ${items?.size() ?: 0} comments")
                    ToolResult.success(mapOf(
                        "comments" to items,
                        "has_more" to (data?.get("has_more")?.asBoolean ?: false),
                        "page_token" to data?.get("page_token")?.asString
                    ))
                }

                // -----------------------------------------------------------------
                // GET
                // @aligned openclaw-lark v2026.3.30 — line-by-line
                // -----------------------------------------------------------------
                "get" -> {
                    val commentId = args["comment_id"] as? String
                        ?: return@withContext ToolResult.error("Missing required parameter: comment_id")
                    Log.i(TAG, "get: comment_id=$commentId")

                    val query = buildQuery("user_id_type" to "open_id")
                    val result = client.get("/open-apis/task/v2/comments/$commentId$query")
                    if (result.isFailure) return@withContext ToolResult.error(result.exceptionOrNull()?.message ?: "Failed")

                    val json = result.getOrNull()
                    Log.i(TAG, "get: returned comment $commentId")
                    ToolResult.success(mapOf("comment" to json?.getAsJsonObject("data")?.getAsJsonObject("comment")))
                }

                else -> ToolResult.error("Unknown action: $action. Supported: create, list, get")
            }
        } catch (e: Exception) {
            Log.e(TAG, "feishu_task_comment failed", e)
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
                    "action" to PropertySchema(
                        "string", "操作类型",
                        enum = listOf("create", "list", "get")
                    ),
                    "task_guid" to PropertySchema("string", "任务 GUID（create 必填）"),
                    "resource_id" to PropertySchema("string", "要获取评论的资源 ID（任务 GUID）（list 必填）"),
                    "comment_id" to PropertySchema("string", "评论 ID（get 必填）"),
                    "content" to PropertySchema("string", "评论内容（纯文本，最长 3000 字符）（create 必填）"),
                    "reply_to_comment_id" to PropertySchema("string", "要回复的评论 ID（用于回复评论）（create 可选）"),
                    "direction" to PropertySchema("string", "排序方式（asc=从旧到新，desc=从新到旧，默认 asc）",
                        enum = listOf("asc", "desc")),
                    "page_size" to PropertySchema("number", "每页数量，默认 50，最大 100"),
                    "page_token" to PropertySchema("string", "分页标记")
                ),
                required = listOf("action")
            )
        )
    )

    companion object {
        private const val TAG = "FeishuTaskComment"
    }
}
