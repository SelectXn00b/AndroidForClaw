package com.xiaomo.androidforclaw.hermes.agent

/**
 * Memory Provider - 记忆接口抽象类
 * 1:1 对齐 hermes/agent/memory_provider.py
 *
 * 定义记忆存储的抽象接口，支持多种后端实现。
 */

data class MemoryEntry(
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any>? = null
)

/**
 * 记忆提供者抽象接口
 */
interface MemoryProvider {

    /**
     * 存储一条记忆
     */
    suspend fun store(entry: MemoryEntry)

    /**
     * 根据 key 获取记忆
     */
    suspend fun recall(key: String): MemoryEntry?

    /**
     * 搜索记忆
     *
     * @param query 搜索关键词
     * @param category 可选的分类过滤
     * @param limit 最大返回数量
     * @return 匹配的记忆列表
     */
    suspend fun search(query: String, category: String? = null, limit: Int = 10): List<MemoryEntry>

    /**
     * 列出所有记忆
     *
     * @param category 可选的分类过滤
     * @param limit 最大返回数量
     * @return 记忆列表
     */
    suspend fun list(category: String? = null, limit: Int = 100): List<MemoryEntry>

    /**
     * 删除一条记忆
     */
    suspend fun delete(key: String): Boolean

    /**
     * 清空所有记忆
     */
    suspend fun clear()

    /**
     * 获取记忆总数
     */
    suspend fun count(): Int
}

/**
 * 内存中的记忆提供者实现（用于测试和 Android 本地存储）
 */
class InMemoryMemoryProvider : MemoryProvider {
    private val store = mutableMapOf<String, MemoryEntry>()

    override suspend fun store(entry: MemoryEntry) {
        store[entry.key] = entry
    }

    override suspend fun recall(key: String): MemoryEntry? = store[key]

    override suspend fun search(query: String, category: String?, limit: Int): List<MemoryEntry> {
        return store.values
            .filter { entry ->
                (category == null || entry.category == category) &&
                (entry.key.contains(query, ignoreCase = true) || entry.value.contains(query, ignoreCase = true))
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun list(category: String?, limit: Int): List<MemoryEntry> {
        return store.values
            .filter { category == null || it.category == category }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun delete(key: String): Boolean = store.remove(key) != null

    override suspend fun clear() = store.clear()

    override suspend fun count(): Int = store.size



    /** Short identifier for this provider (e.g. 'builtin', 'honcho', 'hindsight'). */
    fun name(): String {
        return ""
    }
    /** Return True if this provider is configured, has credentials, and is ready. */
    fun isAvailable(): Boolean {
        return false
    }
    /** Initialize for a session. */
    fun initialize(sessionId: String, kwargs: Any): Unit {
        // TODO: implement initialize
    }
    /** Return text to include in the system prompt. */
    fun systemPromptBlock(): String {
        return ""
    }
    /** Recall relevant context for the upcoming turn. */
    fun prefetch(query: String): String {
        return ""
    }
    /** Queue a background recall for the NEXT turn. */
    fun queuePrefetch(query: String): Unit {
        // TODO: implement queuePrefetch
    }
    /** Persist a completed turn to the backend. */
    fun syncTurn(userContent: String, assistantContent: String): Unit {
        // TODO: implement syncTurn
    }
    /** Return tool schemas this provider exposes. */
    fun getToolSchemas(): List<Map<String, Any>> {
        return emptyList()
    }
    /** Handle a tool call for one of this provider's tools. */
    fun handleToolCall(toolName: String, args: Map<String, Any>, kwargs: Any): String {
        return ""
    }
    /** Clean shutdown — flush queues, close connections. */
    fun shutdown(): Unit {
        // TODO: implement shutdown
    }
    /** Called at the start of each turn with the user message. */
    fun onTurnStart(turnNumber: Int, message: String, kwargs: Any): Unit {
        // TODO: implement onTurnStart
    }
    /** Called when a session ends (explicit exit or timeout). */
    fun onSessionEnd(messages: List<Map<String, Any>>): Unit {
        // TODO: implement onSessionEnd
    }
    /** Called before context compression discards old messages. */
    fun onPreCompress(messages: List<Map<String, Any>>): String {
        return ""
    }
    /** Called on the PARENT agent when a subagent completes. */
    fun onDelegation(task: String, result: String, kwargs: Any): Unit {
        // TODO: implement onDelegation
    }
    /** Return config fields this provider needs for setup. */
    fun getConfigSchema(): List<Map<String, Any>> {
        return emptyList()
    }
    /** Write non-secret config to the provider's native location. */
    fun saveConfig(values: Map<String, Any>, hermesHome: String): Unit {
        // TODO: implement saveConfig
    }
    /** Called when the built-in memory tool writes an entry. */
    fun onMemoryWrite(action: String, target: String, content: String): Unit {
        // TODO: implement onMemoryWrite
    }

}
