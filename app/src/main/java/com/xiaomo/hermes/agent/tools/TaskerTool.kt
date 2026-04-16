package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 *
 * Tasker Tool — Agent 通过 Tasker 操作手机
 *
 * 发送 Intent 给 Tasker 预设任务，实现 UI 操作。
 * 需要 Tasker App 安装并预设对应 Profile。
 *
 * Tasker 配置方式（每个操作）：
 *   1. 新建 Profile → Event → System → Intent Received
 *   2. Action: com.xiaomo.hermes.tasker.XXX
 *   3. 新建 Task → 添加对应操作（AutoInput / Launch App 等）
 *
 * 支持的操作：open_app, click, input, swipe, screenshot, back, home, scroll, wait, run_task
 */

import android.content.Context
import android.content.Intent
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

class TaskerTool(private val context: Context) : Skill {
    companion object {
        private const val TAG = "TaskerTool"
        private const val ACTION_PREFIX = "com.xiaomo.hermes.tasker."
    }

    override val name = "tasker"
    override val description = "通过 Tasker 操作手机 UI。支持：打开App、点击坐标、输入文字、滑动、截图、返回、Home、滚动、等待、执行Tasker自定义任务。需要Tasker App已安装并预设对应Profile。"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "action" to PropertySchema("string",
                            "操作类型",
                            enum = listOf("open_app", "click", "input", "swipe", "screenshot", "back", "home", "scroll", "wait", "run_task")
                        ),
                        "package" to PropertySchema("string", "App包名（open_app时必填）"),
                        "x" to PropertySchema("integer", "X坐标（click时必填）"),
                        "y" to PropertySchema("integer", "Y坐标（click时必填）"),
                        "text" to PropertySchema("string", "输入文字（input时必填）"),
                        "x1" to PropertySchema("integer", "滑动起始X（swipe时必填）"),
                        "y1" to PropertySchema("integer", "滑动起始Y（swipe时必填）"),
                        "x2" to PropertySchema("integer", "滑动结束X（swipe时必填）"),
                        "y2" to PropertySchema("integer", "滑动结束Y（swipe时必填）"),
                        "path" to PropertySchema("string", "截图保存路径（screenshot时选填，默认/sdcard/AFC/screenshot.png）"),
                        "direction" to PropertySchema("string", "滚动方向（scroll时必填）",
                            enum = listOf("up", "down", "left", "right")
                        ),
                        "milliseconds" to PropertySchema("integer", "等待毫秒数（wait时必填）"),
                        "task_name" to PropertySchema("string", "Tasker任务名（run_task时必填）"),
                        "task_extras" to PropertySchema("string", "Tasker任务参数JSON（run_task时选填）")
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val action = args["action"] as? String
            ?: return SkillResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "open_app" -> {
                    val pkg = args["package"] as? String
                        ?: return SkillResult.error("Missing 'package' for open_app")
                    sendIntent("${ACTION_PREFIX}OPEN_APP", mapOf("package" to pkg))
                    SkillResult.success("已发送打开App指令: $pkg")
                }
                "click" -> {
                    val x = args["x"]?.toString()?.toIntOrNull()
                        ?: return SkillResult.error("Missing 'x' for click")
                    val y = args["y"]?.toString()?.toIntOrNull()
                        ?: return SkillResult.error("Missing 'y' for click")
                    sendIntent("${ACTION_PREFIX}CLICK", mapOf("x" to x.toString(), "y" to y.toString()))
                    SkillResult.success("已发送点击指令: ($x, $y)")
                }
                "input" -> {
                    val text = args["text"] as? String
                        ?: return SkillResult.error("Missing 'text' for input")
                    sendIntent("${ACTION_PREFIX}INPUT", mapOf("text" to text))
                    SkillResult.success("已发送输入指令: $text")
                }
                "swipe" -> {
                    val x1 = args["x1"]?.toString()?.toIntOrNull() ?: return SkillResult.error("Missing 'x1'")
                    val y1 = args["y1"]?.toString()?.toIntOrNull() ?: return SkillResult.error("Missing 'y1'")
                    val x2 = args["x2"]?.toString()?.toIntOrNull() ?: return SkillResult.error("Missing 'x2'")
                    val y2 = args["y2"]?.toString()?.toIntOrNull() ?: return SkillResult.error("Missing 'y2'")
                    sendIntent("${ACTION_PREFIX}SWIPE", mapOf(
                        "x1" to x1.toString(), "y1" to y1.toString(),
                        "x2" to x2.toString(), "y2" to y2.toString()
                    ))
                    SkillResult.success("已发送滑动指令: ($x1,$y1) → ($x2,$y2)")
                }
                "screenshot" -> {
                    val path = args["path"] as? String ?: "/sdcard/AFC/screenshot.png"
                    sendIntent("${ACTION_PREFIX}SCREENSHOT", mapOf("path" to path))
                    SkillResult.success("已发送截图指令: $path")
                }
                "back" -> {
                    sendIntent("${ACTION_PREFIX}BACK", emptyMap())
                    SkillResult.success("已发送返回指令")
                }
                "home" -> {
                    sendIntent("${ACTION_PREFIX}HOME", emptyMap())
                    SkillResult.success("已发送Home指令")
                }
                "scroll" -> {
                    val dir = args["direction"] as? String
                        ?: return SkillResult.error("Missing 'direction' (up/down/left/right)")
                    sendIntent("${ACTION_PREFIX}SCROLL", mapOf("direction" to dir))
                    SkillResult.success("已发送滚动指令: $dir")
                }
                "wait" -> {
                    val ms = args["milliseconds"]?.toString()?.toIntOrNull()
                        ?: return SkillResult.error("Missing 'milliseconds'")
                    sendIntent("${ACTION_PREFIX}WAIT", mapOf("milliseconds" to ms.toString()))
                    SkillResult.success("已发送等待指令: ${ms}ms")
                }
                "run_task" -> {
                    val taskName = args["task_name"] as? String
                        ?: return SkillResult.error("Missing 'task_name'")
                    val extras = mutableMapOf("task_name" to taskName)
                    (args["task_extras"] as? String)?.let { extras["task_extras"] = it }
                    sendIntent("${ACTION_PREFIX}RUN_TASK", extras)
                    SkillResult.success("已发送执行Tasker任务指令: $taskName")
                }
                else -> SkillResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Tasker action failed: $action", e)
            SkillResult.error("Tasker操作失败: ${e.message}")
        }
    }

    private fun sendIntent(action: String, extras: Map<String, String>) {
        val intent = Intent(action).apply {
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        context.sendBroadcast(intent)
        Log.e(TAG, "✅ Sent: $action extras=$extras")
    }
}
