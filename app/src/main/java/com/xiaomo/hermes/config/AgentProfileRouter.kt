package com.xiaomo.hermes.config

import com.xiaomo.hermes.logging.Log

/**
 * Agent Profile 路由器
 *
 * 根据消息内容、发送者、群组等信息，选择匹配的 AgentProfile。
 * 匹配逻辑：按 priority 降序检查，第一个匹配的 profile 被选中。
 */
class AgentProfileRouter(private val config: MultiAgentConfig) {

    companion object {
        private const val TAG = "AgentProfileRouter"
    }

    private val compiledPatterns = mutableMapOf<String, List<Regex>>()

    init {
        // 预编译正则
        for (profile in config.profiles) {
            val patterns = profile.routing.patterns.mapNotNull { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 无效正则 '${pattern}' in profile '${profile.name}': ${e.message}")
                    null
                }
            }
            compiledPatterns[profile.name] = patterns
        }
    }

    /**
     * 路由结果
     */
    data class RouteResult(
        val profile: AgentProfile?,
        val matched: Boolean,
        val matchReason: String? = null
    )

    /**
     * 根据消息选择匹配的 AgentProfile
     *
     * @param message 用户消息
     * @param senderId 发送者 ID（可选）
     * @param chatId 群组/聊天 ID（可选）
     * @return RouteResult
     */
    fun route(message: String, senderId: String? = null, chatId: String? = null): RouteResult {
        if (!config.enabled || config.profiles.isEmpty()) {
            return RouteResult(null, false, "multi-agent disabled or no profiles")
        }

        // 按优先级降序排序
        val sortedProfiles = config.profiles
            .filter { it.enabled }
            .sortedByDescending { it.priority }

        for (profile in sortedProfiles) {
            val reason = matchProfile(profile, message, senderId, chatId)
            if (reason != null) {
                Log.i(TAG, "✅ 命中 agent profile: ${profile.name} ($reason)")
                return RouteResult(profile, true, reason)
            }
        }

        // 未命中任何 profile，使用 defaultProfile
        if (config.defaultProfile != null) {
            val default = config.profiles.find { it.name == config.defaultProfile && it.enabled }
            if (default != null) {
                Log.i(TAG, "📋 使用默认 agent profile: ${default.name}")
                return RouteResult(default, true, "default profile")
            }
        }

        return RouteResult(null, false, "no match")
    }

    private fun matchProfile(
        profile: AgentProfile,
        message: String,
        senderId: String?,
        chatId: String?
    ): String? {
        val routing = profile.routing

        // 发送者过滤
        if (routing.senderIds.isNotEmpty() && senderId != null && senderId !in routing.senderIds) {
            return null
        }
        // 群组过滤
        if (routing.chatIds.isNotEmpty() && chatId != null && chatId !in routing.chatIds) {
            return null
        }

        // 前缀匹配
        if (routing.prefix != null && message.trimStart().startsWith(routing.prefix, ignoreCase = true)) {
            return "prefix: ${routing.prefix}"
        }

        // 关键词匹配
        for (keyword in routing.keywords) {
            if (message.contains(keyword, ignoreCase = true)) {
                return "keyword: $keyword"
            }
        }

        // 正则匹配
        val patterns = compiledPatterns[profile.name] ?: emptyList()
        for (pattern in patterns) {
            if (pattern.containsMatchIn(message)) {
                return "pattern: ${pattern.pattern}"
            }
        }

        return null
    }

    /**
     * 列出所有已启用的 profile 名称
     */
    fun listProfiles(): List<String> {
        return config.profiles.filter { it.enabled }.map { it.name }
    }

    /**
     * 获取指定名称的 profile
     */
    fun getProfile(name: String): AgentProfile? {
        return config.profiles.find { it.name == name && it.enabled }
    }
}
