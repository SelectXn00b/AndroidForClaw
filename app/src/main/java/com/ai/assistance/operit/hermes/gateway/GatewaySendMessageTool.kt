package com.ai.assistance.operit.hermes.gateway

import android.util.Log
import com.google.gson.Gson

/**
 * R-GW-STREAMING-002: gateway-only `send_message` tool.
 *
 * Provides a per-turn `send_message(text)` tool that lets the agent push
 * a single chunk to the IM gateway *during* the agent loop (instead of
 * waiting for all turns to finish and dumping one big blob). Each call =
 * one IM bubble = visible progress on the user's WeChat.
 *
 * Android v1 simplification vs Python upstream `tools/send_message_tool.py`:
 *   - Python upstream supports `action` / `target` for cross-channel push.
 *   - Android v1 has a single `text` param; `target` is implicitly the
 *     current gateway chatId (resolved via `GatewayOutboundRegistry`).
 *   - The tool is gated by `isSubTask && chatId.startsWith("gw:")` in
 *     `EnhancedAIService.runAgentLoopViaHermes` — APP-UI path never sees it.
 *
 * Tool description is bilingual (EN + 中文) because the hint in
 * `MULTI_MESSAGE_HINT` also crosses both languages.
 */
object GatewaySendMessageTool {

    const val GATEWAY_SEND_MESSAGE_TOOL_NAME = "send_message"

    private const val TAG = "HermesBridge/SendMsg"

    private val gson = Gson()

    private val TOOL_DESCRIPTION = """
        Send a single chunk of your reply to the user's IM chat immediately, as a separate bubble.
        Call this tool whenever you want to push progress (e.g. "I'm checking the weather...",
        "Found it: rain today", "Suggest bringing an umbrella") so the user sees you working,
        rather than waiting for one giant blob at the end.
        Each call produces one bubble in the IM app. You may still return a final summary at the
        end of the agent loop — calls made via this tool do not replace your final reply.

        立即向当前 IM 聊天发送一段回复，作为一条独立的气泡。
        当你想分步骤展示进度时（例如「我去查一下天气...」「查到了：今天有雨」「建议带伞」），
        请主动调用本工具，每次调用 = 用户那边一条独立的气泡。
        最终 turn 仍可正常返回一条总结性回复，本工具的多次调用不会替代你的最终回复。
    """.trimIndent()

    /**
     * Build the OpenAI-spec function schema for this tool. Identical shape
     * to `OpenAiToolSchema.toOpenAiSchema()` output so it can be appended
     * directly to `openAiToolSchemas`.
     */
    fun buildSchema(): Map<String, Any?> {
        val properties = linkedMapOf<String, Any?>(
            "text" to linkedMapOf<String, Any?>(
                "type" to "string",
                "description" to "The chunk of text to send to the IM chat as one bubble. Required."
            )
        )
        val paramsSchema = linkedMapOf<String, Any?>(
            "type" to "object",
            "properties" to properties,
            "required" to listOf("text")
        )
        return linkedMapOf(
            "type" to "function",
            "function" to linkedMapOf<String, Any?>(
                "name" to GATEWAY_SEND_MESSAGE_TOOL_NAME,
                "description" to TOOL_DESCRIPTION,
                "parameters" to paramsSchema
            )
        )
    }

    /**
     * Build an executor that resolves the per-chatId dispatcher via
     * `GatewayOutboundRegistry.dispatch(historyChatId, text)` and returns
     * a JSON `{success,result,error}` envelope (parallel to
     * `OperitToolDispatcher`'s normal-path return).
     */
    fun buildExecutor(historyChatId: String): suspend (Map<String, Any?>) -> String = { args ->
        val text = (args["text"] as? String)?.trim().orEmpty()
        if (text.isEmpty()) {
            Log.w(TAG, "send_message called with empty text for chatId=$historyChatId")
            GatewayFileLogger.w(TAG, "send_message empty text chatId=$historyChatId")
            gson.toJson(mapOf(
                "success" to false,
                "result" to "",
                "error" to "text is required and must be non-empty"
            ))
        } else {
            val startNs = System.nanoTime()
            val ok = runCatching {
                GatewayOutboundRegistry.dispatch(historyChatId, text)
            }.getOrElse { err ->
                Log.w(TAG, "send_message dispatch threw for chatId=$historyChatId", err)
                GatewayFileLogger.w(TAG, "send_message tool dispatch threw chatId=$historyChatId err=${err.message}")
                false
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            GatewayFileLogger.d(TAG, "send_message tool dispatch chatId=$historyChatId ok=$ok ms=$elapsedMs textLen=${text.length}")
            gson.toJson(mapOf(
                "success" to ok,
                "result" to if (ok) "delivered" else "no dispatcher registered for chat",
                "error" to if (ok) null else "dispatcher missing or rejected"
            ))
        }
    }
}
