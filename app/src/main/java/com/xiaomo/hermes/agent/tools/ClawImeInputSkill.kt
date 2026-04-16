package com.xiaomo.hermes.agent.tools

/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */


import android.content.Context
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.FunctionDefinition
import com.xiaomo.hermes.providers.ParametersSchema
import com.xiaomo.hermes.providers.PropertySchema
import com.xiaomo.hermes.providers.ToolDefinition
import com.xiaomo.hermes.service.ClawIMEManager
import com.xiaomo.hermes.service.ClipboardInputHelper

/**
 * 文本输入工具
 * 优先通过剪切板粘贴输入文本，无障碍不可用时兜底到 ClawIME 键盘
 *
 * 工作原理:
 * 1. 用户先用 tap() 点击输入框，让其获得焦点
 * 2. 优先路径：剪切板写入 → 无障碍 ACTION_PASTE
 * 3. 兜底路径：ClawIME 输入法 commitText
 */
class ClawImeInputSkill(private val context: Context) : Skill {
    companion object {
        private const val TAG = "ClawImeInputSkill"
    }

    override val name = "claw_ime_input"
    override val description: String
        get() {
            val clipboardOk = ClipboardInputHelper.isPasteAvailable() && ClipboardInputHelper.isClipboardAvailable(context)
            val imeOk = ClawIMEManager.isClawImeEnabled(context) && ClawIMEManager.isConnected()
            val statusNote = when {
                clipboardOk -> " ✅ 剪切板输入已就绪"
                imeOk -> " ✅ ClawIME 键盘已就绪（剪切板不可用）"
                else -> " ⚠️ **不可用** - 需要开启无障碍服务或 ClawIME 输入法"
            }
            return "Input text via clipboard paste (preferred) or ClawIME keyboard (fallback)$statusNote"
        }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ParametersSchema(
                    type = "object",
                    properties = mapOf(
                        "text" to PropertySchema("string", "要输入的文本内容"),
                        "action" to PropertySchema(
                            "string",
                            "操作类型: 'input'(输入文本,默认) | 'send'(输入后发送) | 'clear'(清空输入框)",
                            enum = listOf("input", "send", "clear")
                        )
                    ),
                    required = listOf("text")
                )
            )
        )
    }

    override suspend fun execute(args: Map<String, Any?>): SkillResult {
        val text = args["text"] as? String
        val action = args["action"] as? String ?: "input"

        if (text == null && action != "clear") {
            return SkillResult.error("Missing required parameter: text")
        }

        val clipboardOk = ClipboardInputHelper.isPasteAvailable() && ClipboardInputHelper.isClipboardAvailable(context)
        val imeOk = ClawIMEManager.isClawImeEnabled(context) && ClawIMEManager.isConnected()

        if (!clipboardOk && !imeOk) {
            return SkillResult.error(
                "输入不可用。请开启无障碍服务（推荐，支持剪切板粘贴），或切换到 ClawIME 输入法"
            )
        }

        return try {
            when (action) {
                "clear" -> {
                    val success = if (imeOk) {
                        ClawIMEManager.clearText()
                    } else {
                        // 无障碍方式清空：选中全部 → 删除
                        false
                    }
                    if (success) {
                        kotlinx.coroutines.delay(100)
                        SkillResult.success("已清空输入框")
                    } else {
                        SkillResult.error("清空输入框失败")
                    }
                }
                "send" -> {
                    // 输入文本
                    val (inputSuccess, method) = inputTextWithFallback(text!!, clipboardOk, imeOk)
                    if (!inputSuccess) {
                        return SkillResult.error("输入文本失败")
                    }
                    kotlinx.coroutines.delay(500)

                    // 发送：优先 ClawIME 的 sendMessage，兜底回车键
                    val sendSuccess = if (imeOk) {
                        ClawIMEManager.sendMessage()
                    } else {
                        // 通过 shell 发送回车键
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent 66")).waitFor() == 0
                    }
                    if (!sendSuccess) {
                        return SkillResult.error("发送消息失败")
                    }
                    kotlinx.coroutines.delay(1000)

                    SkillResult.success(
                        "已输入并发送: $text (${text.length} chars, via $method)",
                        mapOf(
                            "text" to text,
                            "length" to text.length,
                            "action" to "send",
                            "method" to method
                        )
                    )
                }
                else -> {
                    val (success, method) = inputTextWithFallback(text!!, clipboardOk, imeOk)
                    if (!success) {
                        return SkillResult.error("输入文本失败")
                    }

                    val waitTime = (100L + (text.length * 5L).coerceAtMost(300L)).coerceAtLeast(500L)
                    kotlinx.coroutines.delay(waitTime)

                    SkillResult.success(
                        "已输入: $text (${text.length} chars, via $method)",
                        mapOf(
                            "text" to text,
                            "length" to text.length,
                            "method" to method,
                            "wait_time_ms" to waitTime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Input failed", e)
            SkillResult.error("输入失败: ${e.message}")
        }
    }

    /**
     * 优先剪切板，兜底 ClawIME
     * @return Pair(是否成功, 使用的方法名)
     */
    private fun inputTextWithFallback(text: String, clipboardOk: Boolean, imeOk: Boolean): Pair<Boolean, String> {
        if (clipboardOk) {
            val success = ClipboardInputHelper.inputTextViaClipboard(context, text)
            if (success) return Pair(true, "clipboard")
            Log.w(TAG, "Clipboard input failed, trying ClawIME fallback")
        }
        if (imeOk) {
            val success = ClawIMEManager.inputText(text)
            if (success) return Pair(true, "clawime")
        }
        return Pair(false, "none")
    }
}
