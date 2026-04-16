package com.xiaomo.hermes.config

import org.json.JSONObject

/**
 * 多 Agent Profile 数据模型
 *
 * 每个 agent profile 定义了一个独立的 agent，拥有自己的：
 * - model: 使用的模型（provider/model-id 格式）
 * - apiKey: 独立的 API Key（可选，不填则用 provider 默认 key）
 * - systemPrompt: 独立的系统提示词（追加到基础 system prompt）
 * - tools: 可用工具列表（空 = 全部可用）
 * - routing: 路由规则（关键词/正则匹配）
 */
data class AgentProfile(
    val name: String,
    val displayName: String = name,
    val model: String? = null,           // e.g. "anthropic/claude-opus-4-6"
    val apiKey: String? = null,           // 独立 API key
    val systemPrompt: String? = null,     // 追加到基础 system prompt
    val tools: List<String> = emptyList(), // 空 = 全部可用
    val enabled: Boolean = true,
    val routing: AgentRoutingRule = AgentRoutingRule(),
    val priority: Int = 0                 // 优先级，越大越优先匹配
)

/**
 * 路由规则
 */
data class AgentRoutingRule(
    /** 关键词列表 — 消息包含任一关键词即匹配 */
    val keywords: List<String> = emptyList(),
    /** 正则表达式列表 — 消息匹配任一正则即匹配 */
    val patterns: List<String> = emptyList(),
    /** 触发前缀 — 消息以该前缀开头即匹配（如 "/code "） */
    val prefix: String? = null,
    /** 发送者 ID 列表 — 只匹配这些发送者 */
    val senderIds: List<String> = emptyList(),
    /** 群组 ID 列表 — 只匹配这些群组 */
    val chatIds: List<String> = emptyList()
) {
    fun matches(message: String, senderId: String? = null, chatId: String? = null): Boolean {
        // 发送者过滤
        if (senderIds.isNotEmpty() && senderId != null && senderId !in senderIds) return false
        // 群组过滤
        if (chatIds.isNotEmpty() && chatId != null && chatId !in chatIds) return false
        // 前缀匹配
        if (prefix != null && message.trimStart().startsWith(prefix, ignoreCase = true)) return true
        // 关键词匹配
        if (keywords.any { keyword -> message.contains(keyword, ignoreCase = true) }) return true
        // 正则匹配
        if (patterns.any { pattern ->
            try {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(message)
            } catch (e: Exception) {
                false
            }
        }) return true
        return false
    }
}

/**
 * 多 Agent 配置
 */
data class MultiAgentConfig(
    val enabled: Boolean = false,
    val profiles: List<AgentProfile> = emptyList(),
    val defaultProfile: String? = null  // 未匹配时使用的 profile name，null = 不使用 agent profile（走默认流程）
)
