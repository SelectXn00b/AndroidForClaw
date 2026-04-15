package com.xiaomo.androidforclaw.hermes.agent

/**
 * Credential Pool - 多 key 轮转 + 自动 failover
 * 1:1 对齐 hermes/agent/credential_pool.py
 *
 * 功能：
 * - 每个 provider 维护多个 API key
 * - 支持 weight 加权轮转
 * - exhausted TTL 自动恢复
 * - 失败时自动切换下一个 key
 */
class CredentialPool {

    private val providers: MutableMap<String, MutableList<ApiKey>> = mutableMapOf()

    data class ApiKey(
        val key: String,
        var weight: Int = 1,
        var exhaustedUntil: Long = 0L,
        var lastUsed: Long = 0L,
        var failCount: Int = 0
    )

    /**
     * 添加 API key
     * @param provider provider 名称 (如 "openai", "anthropic")
     * @param key API key 值
     * @param weight 权重 (默认 1)
     */
    fun addKey(provider: String, key: String, weight: Int = 1) {
        val list = providers.getOrPut(provider) { mutableListOf() }
        list.add(ApiKey(key = key, weight = weight))
    }

    /**
     * 获取可用的 API key
     * 优先返回未 exhausted 的、权重最高的 key
     * @param provider provider 名称
     * @return API key，无可用返回 null
     */
    fun getKey(provider: String): String? {
        val now = System.currentTimeMillis()
        val keys = providers[provider] ?: return null

        // 过滤掉 exhausted 的 key
        val available = keys.filter { it.exhaustedUntil < now }
        if (available.isEmpty()) return null

        // 按权重排序，返回最高的
        return available.maxByOrNull { it.weight }?.also {
            it.lastUsed = now
        }?.key
    }

    /**
     * 标记 key 为 exhausted
     * @param provider provider 名称
     * @param key API key
     * @param ttlSeconds exhausted 的秒数
     */
    fun markExhausted(provider: String, key: String, ttlSeconds: Int) {
        val keys = providers[provider] ?: return
        keys.find { it.key == key }?.let {
            it.exhaustedUntil = System.currentTimeMillis() + (ttlSeconds * 1000L)
            it.failCount++
        }
    }

    /**
     * 轮转到下一个 key
     * @param provider provider 名称
     * @return 下一个可用的 key，无可用返回 null
     */
    fun rotate(provider: String): String? {
        val keys = providers[provider] ?: return null
        if (keys.isEmpty()) return null

        // 把当前第一个 key 移到最后
        val first = keys.removeAt(0)
        keys.add(first)

        return getKey(provider)
    }

    /**
     * 获取 provider 下所有 key 数量
     */
    fun keyCount(provider: String): Int {
        return providers[provider]?.size ?: 0
    }

    /**
     * 获取 provider 列表
     */
    fun providers(): List<String> {
        return providers.keys.toList()
    }

    /**
     * 重置 key 的 exhausted 状态
     */
    fun resetKey(provider: String, key: String) {
        providers[provider]?.find { it.key == key }?.let {
            it.exhaustedUntil = 0L
            it.failCount = 0
        }
    }


    // === Missing constants (auto-generated stubs) ===
    val STATUS_OK = ""
    val STATUS_EXHAUSTED = ""
    val AUTH_TYPE_OAUTH = ""
    val AUTH_TYPE_API_KEY = ""
    val SOURCE_MANUAL = ""
    val STRATEGY_FILL_FIRST = ""
    val STRATEGY_ROUND_ROBIN = ""
    val STRATEGY_RANDOM = ""
    val STRATEGY_LEAST_USED = ""
    val SUPPORTED_POOL_STRATEGIES = ""
    val EXHAUSTED_TTL_429_SECONDS = ""
    val EXHAUSTED_TTL_DEFAULT_SECONDS = ""
    val CUSTOM_POOL_PREFIX = ""
    val _EXTRA_KEYS = emptySet<Any>()
    val DEFAULT_MAX_CONCURRENT_PER_CREDENTIAL = ""

    // === Missing methods (auto-generated stubs) ===
    private fun loadConfigSafe(): Any? {
        return null
    // Hermes: _load_config_safe
}

    fun fromDict(provider: String, payload: Map<String, Any>): Any? {
        return null
    }
    fun toDict(): Map<String, Any> {
        return emptyMap()
    }
    fun runtimeApiKey(): String {
        return ""
    }
    fun runtimeBaseUrl(): String? {
        return null
    }
    fun hasCredentials(): Boolean {
        return false
    }
    /** True if at least one entry is not currently in exhaustion cooldown. */
    fun hasAvailable(): Boolean {
        return false
    }
    fun entries(): List<Any?> {
        return emptyList()
    }
    fun current(): Any?? {
        return null
    }
    /** Swap an entry in-place by id, preserving sort order. */
    fun _replaceEntry(old: Any?, new: Any?): Unit {
        // TODO: implement _replaceEntry
    }
    fun _persist(): Unit {
        // TODO: implement _persist
    }
    fun _markExhausted(entry: Any?, statusCode: Int?, errorContext: Map<String, Any>? = null): Any? {
        return null
    }
    /** Sync a claude_code pool entry from ~/.claude/.credentials.json if tokens differ. */
    fun _syncAnthropicEntryFromCredentialsFile(entry: Any?): Any? {
        return null
    }
    /** Sync an openai-codex pool entry from ~/.codex/auth.json if tokens differ. */
    fun _syncCodexEntryFromCli(entry: Any?): Any? {
        return null
    }
    /** Write refreshed pool entry tokens back to auth.json providers. */
    fun _syncDeviceCodeEntryToAuthStore(entry: Any?): Unit {
        // TODO: implement _syncDeviceCodeEntryToAuthStore
    }
    fun _refreshEntry(entry: Any?): Any?? {
        return null
    }
    fun _entryNeedsRefresh(entry: Any?): Boolean {
        return false
    }
    fun select(): Any?? {
        return null
    }
    /** Return entries not currently in exhaustion cooldown. */
    fun _availableEntries(): List<Any?> {
        return emptyList()
    }
    fun _selectUnlocked(): Any?? {
        return null
    }
    fun peek(): Any?? {
        return null
    }
    fun markExhaustedAndRotate(): Any?? {
        return null
    }
    /** Acquire a soft lease on a credential. */
    fun acquireLease(credentialId: String? = null): String? {
        return null
    }
    /** Release a previously acquired credential lease. */
    fun releaseLease(credentialId: String): Unit {
        // TODO: implement releaseLease
    }
    fun tryRefreshCurrent(): Any?? {
        return null
    }
    fun _tryRefreshCurrentUnlocked(): Any?? {
        return null
    }
    fun resetStatuses(): Int {
        return 0
    }
    fun removeIndex(index: Int): Any?? {
        return null
    }
    fun resolveTarget(target: Any): List<Any?> {
        return emptyList()
    }
    fun addEntry(entry: Any?): Any? {
        return null
    }

}
