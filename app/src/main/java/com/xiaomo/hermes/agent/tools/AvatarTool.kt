package com.xiaomo.hermes.agent.tools

/**
 * Avatar Tool — Live2D avatar control for agent embodiment.
 *
 * OpenClaw Source Reference:
 * - Android 平台特有，无直接 OpenClaw 对应
 */
import ai.openclaw.app.avatar.AvatarStateHolder
import ai.openclaw.app.avatar.FloatingAvatarService
import android.util.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition

class AvatarTool : Tool {

    override val name = "body"
    override val description =
        "Control YOUR body — a Live2D avatar that represents you visually on screen. " +
        "This is your physical embodiment: use it to express emotions, react to conversations, and show personality. " +
        "IMPORTANT: Always call 'status' first to read your current body state, then use 'pose' to choreograph the next expression/posture based on current values. " +
        "Use 'pose' to control facial expressions and body language by setting parameters (eyes, mouth, head angle, etc.). " +
        "Use 'trigger' for quick reactive motions. " +
        "Use 'stop' to freeze. " +
        "Use 'reset' to return to natural idle breathing. " +
        "You should actively use this during conversations to make yourself feel alive."

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
                            "string",
                            "'status' read current body state, 'start' show your body on screen, 'stop' freeze in place, 'pose' control facial expression & body language, 'trigger' quick reactive motion, 'reset' return to natural idle",
                            enum = listOf("status", "start", "stop", "pose", "trigger", "reset")
                        ),
                        "params" to PropertySchema(
                            "object",
                            "Parameter overrides for 'pose' action. Only set the params you want to change. Omit to clear all overrides.",
                            properties = mapOf(
                                "ParamAngleX" to PropertySchema("number", "Head rotation left(-30)~right(30)"),
                                "ParamAngleY" to PropertySchema("number", "Head tilt up(30)~down(-30)"),
                                "ParamAngleZ" to PropertySchema("number", "Head roll left(-30)~right(30)"),
                                "ParamEyeLOpen" to PropertySchema("number", "Left eye open(1)~closed(0)"),
                                "ParamEyeROpen" to PropertySchema("number", "Right eye open(1)~closed(0)"),
                                "ParamEyeLSmile" to PropertySchema("number", "Left eye smile squint 0~1"),
                                "ParamEyeRSmile" to PropertySchema("number", "Right eye smile squint 0~1"),
                                "ParamEyeBallX" to PropertySchema("number", "Gaze direction left(-1)~right(1)"),
                                "ParamEyeBallY" to PropertySchema("number", "Gaze direction down(-1)~up(1)"),
                                "ParamBrowLY" to PropertySchema("number", "Left brow down(-1)~up(1)"),
                                "ParamBrowRY" to PropertySchema("number", "Right brow down(-1)~up(1)"),
                                "ParamBrowLAngle" to PropertySchema("number", "Left brow angle sad(-1)~angry(1)"),
                                "ParamBrowRAngle" to PropertySchema("number", "Right brow angle sad(-1)~angry(1)"),
                                "ParamMouthForm" to PropertySchema("number", "Mouth shape sad(-1)~smile(1)"),
                                "ParamMouthOpenY" to PropertySchema("number", "Mouth open 0~1"),
                                "ParamCheek" to PropertySchema("number", "Blush intensity 0~1"),
                                "ParamBodyAngleX" to PropertySchema("number", "Body lean left(-10)~right(10)"),
                            )
                        ),
                        "expression" to PropertySchema(
                            "string",
                            "Expression name for trigger action"
                        ),
                    ),
                    required = listOf("action")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing required parameter: action")

        Log.d(TAG, "AvatarTool execute: action=$action, args=$args")

        return when (action) {
            "status" -> {
                val current = AvatarStateHolder.currentParams.value
                val overrides = AvatarStateHolder.paramOverrides.value
                val paused = AvatarStateHolder.paused.value
                val running = FloatingAvatarService.isRunning
                val result = buildString {
                    appendLine("Body: ${if (!running) "HIDDEN" else if (paused) "FROZEN" else "ACTIVE"}")
                    if (overrides.isNotEmpty()) {
                        appendLine("Overrides: ${overrides.entries.joinToString { "${it.key}=${"%.1f".format(it.value)}" }}")
                    }
                    if (current.isNotEmpty()) {
                        appendLine("Current params:")
                        current.entries.forEach { (k, v) ->
                            append("  $k=${"%.2f".format(v)}")
                        }
                    }
                }
                ToolResult.success(result)
            }
            "start" -> {
                val ctx = appContext ?: return ToolResult.error("App context not available")
                if (!FloatingAvatarService.isRunning) {
                    FloatingAvatarService.start(ctx)
                }
                ctx.getSharedPreferences("forclaw_avatar", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", true).apply()
                ToolResult.success("Avatar floating window started")
            }
            "stop" -> {
                Log.d(TAG, "Setting paused=true")
                AvatarStateHolder.setPaused(true)
                ToolResult.success("Avatar animation paused (frozen in place)")
            }
            "pose" -> {
                @Suppress("UNCHECKED_CAST")
                val raw = args["params"] as? Map<*, *>
                val params = raw?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = (v as? Number)?.toFloat() ?: return@mapNotNull null
                    key to value
                }?.toMap() ?: emptyMap()
                if (params.isEmpty()) {
                    AvatarStateHolder.clearParamOverrides()
                    ToolResult.success("Parameter overrides cleared, back to auto animation")
                } else {
                    AvatarStateHolder.setParamOverrides(params)
                    ToolResult.success("Pose set: ${params.entries.joinToString { "${it.key}=${it.value}" }}")
                }
            }
            "trigger" -> {
                val expression = args["expression"] as? String
                    ?: return ToolResult.error("Missing 'expression' for trigger action")
                AvatarStateHolder.fireTrigger(expression)
                ToolResult.success("Avatar triggered: $expression")
            }
            "reset" -> {
                AvatarStateHolder.setPaused(false)
                AvatarStateHolder.clearParamOverrides()
                ToolResult.success("Avatar reset to automatic mode")
            }
            else -> ToolResult.error("Unknown action: $action")
        }
    }

    companion object {
        private const val TAG = "AvatarTool"
        var appContext: android.content.Context? = null
    }
}
