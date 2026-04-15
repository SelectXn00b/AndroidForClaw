package com.xiaomo.androidforclaw.hermes.agent

/**
 * Context Engine - ContextEngine 抽象基类
 * 1:1 对齐 hermes/agent/context_engine.py
 *
 * 定义上下文管理的抽象接口，包括消息历史、上下文窗口管理等。
 */

data class ContextWindow(
    val messages: List<Map<String, Any>>,
    val totalTokens: Int,
    val systemPromptTokens: Int,
    val toolsTokens: Int,
    val availableTokens: Int
)

/**
 * 上下文引擎抽象基类
 */
abstract class ContextEngine {

    /**
     * 获取当前上下文窗口
     *
     * @param model 模型 ID
     * @param systemPrompt system prompt 文本
     * @param tools 工具定义列表
     * @return 上下文窗口信息
     */
    abstract suspend fun getContextWindow(
        model: String,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList()
    ): ContextWindow

    /**
     * 估算文本的 token 数
     *
     * @param text 文本
     * @return token 数
     */
    abstract fun estimateTokens(text: String): Int

    /**
     * 估算消息列表的 token 数
     *
     * @param messages 消息列表
     * @return token 数
     */
    abstract fun estimateMessagesTokens(messages: List<Map<String, Any>>): Int

    /**
     * 检查上下文是否即将溢出
     *
     * @param model 模型 ID
     * @param messages 消息列表
     * @param systemPrompt system prompt
     * @param tools 工具定义
     * @param threshold 触发阈值（0.0 - 1.0），默认 0.9
     * @return 是否即将溢出
     */
    abstract suspend fun isNearOverflow(
        model: String,
        messages: List<Map<String, Any>>,
        systemPrompt: String = "",
        tools: List<Map<String, Any>> = emptyList(),
        threshold: Double = 0.9
    ): Boolean

    /**
     * 获取模型的 context length
     *
     * @param model 模型 ID
     * @return context length
     */
    abstract fun getContextLength(model: String): Int



    /** Short identifier (e.g. 'compressor', 'lcm'). */
    fun name(): String {
        return ""
    }
    /** Update tracked token usage from an API response. */
    fun updateFromResponse(usage: Map<String, Any>): Unit {
        // TODO: implement updateFromResponse
    }
    /** Return True if compaction should fire this turn. */
    fun shouldCompress(promptTokens: Int? = null): Boolean {
        return false
    }
    /** Compact the message list and return the new message list. */
    fun compress(messages: List<Map<String, Any>>, currentTokens: Int? = null): List<Map<String, Any>> {
        return emptyList()
    }
    /** Quick rough check before the API call (no real token count yet). */
    fun shouldCompressPreflight(messages: List<Map<String, Any>>): Boolean {
        return false
    }
    /** Called when a new conversation session begins. */
    fun onSessionStart(sessionId: String, kwargs: Any): Unit {
        // TODO: implement onSessionStart
    }
    /** Called at real session boundaries (CLI exit, /reset, gateway expiry). */
    fun onSessionEnd(sessionId: String, messages: List<Map<String, Any>>): Unit {
        // TODO: implement onSessionEnd
    }
    /** Called on /new or /reset. Reset per-session state. */
    fun onSessionReset(): Unit {
        // TODO: implement onSessionReset
    }
    /** Return tool schemas this engine provides to the agent. */
    fun getToolSchemas(): List<Map<String, Any>> {
        return emptyList()
    }
    /** Handle a tool call from the agent. */
    fun handleToolCall(name: String, args: Map<String, Any>, kwargs: Any): String {
        return ""
    }
    /** Return status dict for display/logging. */
    fun getStatus(): Map<String, Any> {
        return emptyMap()
    }
    /** Called when the user switches models or on fallback activation. */
    fun updateModel(model: String, contextLength: Int, baseUrl: String = "", apiKey: String = "", provider: String = ""): Unit {
        // TODO: implement updateModel
    }

}
