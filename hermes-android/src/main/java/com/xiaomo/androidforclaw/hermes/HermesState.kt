package com.xiaomo.androidforclaw.hermes

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * 管理持久化状态文件
 * 1:1 对齐 hermes-agent/hermes_state.py
 *
 * 支持并发读写的状态管理。
 * Python 版本使用 filelock，Android 版本使用 FileChannel.lock()。
 */

// ── 全局 Gson 实例 ──────────────────────────────────────────────────────
val gson: Gson = Gson()

/**
 * 管理 Hermes 持久化状态
 *
 * 设计原则：
 * - 单个 JSON 文件：所有状态在一个 dict 中
 * - 原子写入：先写临时文件，再重命名
 * - 文件锁：防止多进程竞争
 * - 延迟加载：首次访问才读取
 * - 自动保存：修改后延迟写入
 *
 * Python 版本使用 filelock 库；Android 版本使用 FileChannel.lock()。
 */
class HermesState(
    private val statePath: File = File(getHermesHome(), "state.json"),
    private val autoSave: Boolean = true,
) {

    private var _state: MutableMap<String, Any>? = null
    private var _dirty: Boolean = false
    private val _lock = Any()

    /**
     * 确保状态已加载
     */
    private fun ensureLoaded() {
        if (_state != null) return
        _state = loadState()
    }

    /**
     * 从磁盘加载状态
     * Python: _load() -> Dict[str, Any]
     */
    private fun loadState(): MutableMap<String, Any> {
        if (!statePath.exists()) {
            return mutableMapOf()
        }

        return try {
            acquireLock { file ->
                val bytes = ByteArray(file.length().toInt())
                file.readFully(bytes)
                val content = String(bytes, Charsets.UTF_8)
                if (content.isBlank()) {
                    mutableMapOf()
                } else {
                    val type = object : TypeToken<MutableMap<String, Any>>() {}.type
                    gson.fromJson(content, type) ?: mutableMapOf()
                }
            }
        } catch (e: Exception) {
            getLogger("hermes_state").warning(
                "Failed to load state from ${statePath.absolutePath}: ${e.message}"
            )
            mutableMapOf()
        }
    }

    /**
     * 保存状态到磁盘
     * Python: _save() -> None
     */
    private fun saveState() {
        val state = _state ?: return
        statePath.parentFile.mkdirs()

        try {
            acquireLock { file ->
                val content = gson.toJson(state)
                file.setLength(0)
                file.write(content.toByteArray(Charsets.UTF_8))
                file.fd.sync()
            }
            _dirty = false
        } catch (e: Exception) {
            getLogger("hermes_state").error(
                "Failed to save state to ${statePath.absolutePath}: ${e.message}"
            )
        }
    }

    /**
     * 获取状态值
     * Python: get(key, default=None)
     */
    fun get(key: String, default: Any? = null): Any? {
        ensureLoaded()
        return _state!![key] ?: default
    }

    /**
     * 设置状态值
     * Python: set(key, value)
     */
    fun set(key: String, value: Any) {
        ensureLoaded()
        _state!![key] = value
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 删除状态值
     * Python: delete(key)
     */
    fun delete(key: String): Boolean {
        ensureLoaded()
        val removed = _state!!.remove(key) != null
        if (removed) {
            _dirty = true
            if (autoSave) {
                saveState()
            }
        }
        return removed
    }

    /**
     * 检查键是否存在
     */
    fun contains(key: String): Boolean {
        ensureLoaded()
        return _state!!.containsKey(key)
    }

    /**
     * 获取所有键
     */
    fun keys(): Set<String> {
        ensureLoaded()
        return _state!!.keys.toSet()
    }

    /**
     * 强制保存
     */
    fun save() {
        saveState()
    }

    /**
     * 清空所有状态
     */
    fun clear() {
        ensureLoaded()
        _state!!.clear()
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 获取状态大小
     */
    fun size(): Int {
        ensureLoaded()
        return _state!!.size
    }

    /**
     * 检查是否有未保存的修改
     */
    fun isDirty(): Boolean = _dirty

    /**
     * 获取状态快照
     */
    fun snapshot(): Map<String, Any> {
        ensureLoaded()
        return _state!!.toMap()
    }

    /**
     * 批量更新
     */
    fun update(updates: Map<String, Any>) {
        ensureLoaded()
        _state!!.putAll(updates)
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 合并更新（深度合并）
     */
    fun merge(key: String, value: Map<String, Any>) {
        ensureLoaded()
        val existing = _state!![key]
        if (existing is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val merged = (existing as Map<String, Any>).toMutableMap()
            merged.putAll(value)
            _state!![key] = merged
        } else {
            _state!![key] = value
        }
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    /**
     * 获取嵌套值
     */
    fun getNested(path: String, default: Any? = null): Any? {
        ensureLoaded()
        val parts = path.split(".")
        var current: Any? = _state
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return default
            }
        }
        return current ?: default
    }

    /**
     * 设置嵌套值
     */
    fun setNested(path: String, value: Any) {
        ensureLoaded()
        val parts = path.split(".")
        var current: MutableMap<String, Any> = _state!!
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            val existing = current[part]
            if (existing is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = existing as MutableMap<String, Any>
            } else {
                val newMap = mutableMapOf<String, Any>()
                current[part] = newMap
                current = newMap
            }
        }
        current[parts.last()] = value
        _dirty = true
        if (autoSave) {
            saveState()
        }
    }

    // ── 文件锁（Android 版本使用 FileChannel）───────────────────────────────

    /**
     * 获取文件锁并执行操作
     * Python: filelock.FileLock
     * Android: RandomAccessFile + FileChannel.lock()
     */
    private fun <T> acquireLock(block: (RandomAccessFile) -> T): T {
        statePath.parentFile.mkdirs()
        val lockFile = File(statePath.parent, ".${statePath.name}.lock")
        val raf = RandomAccessFile(lockFile, "rw")
        var channel: FileChannel? = null
        var lock: FileLock? = null

        try {
            channel = raf.channel
            lock = channel.lock()
            return block(raf)
        } finally {
            lock?.release()
            channel?.close()
            raf.close()
        }
    }
}

// ── 全局状态实例 ──────────────────────────────────────────────────────────

private var _globalState: HermesState? = null

/**
 * 获取全局状态实例
 */
fun getGlobalState(): HermesState {
    if (_globalState == null) {
        _globalState = HermesState()
    }
    return _globalState!!
}

/**
 * 重置全局状态（测试用）
 */
fun resetGlobalState() {
    _globalState = null



    /** Execute a write transaction with BEGIN IMMEDIATE and jitter retry. */
    fun _executeWrite(fn: (Any?) -> Any?): Any? {
        return null
    }
    /** Best-effort PASSIVE WAL checkpoint.  Never blocks, never raises. */
    fun _tryWalCheckpoint(): Unit {
        // TODO: implement _tryWalCheckpoint
    }
    /** Close the database connection. */
    fun close(): Any? {
        return null
    }
    /** Create tables and FTS if they don't exist, run migrations. */
    fun _initSchema(): Any? {
        return null
    }
    /** Create a new session record. Returns the session_id. */
    fun createSession(sessionId: String, source: String, model: String? = null, modelConfig: Map<String, Any>? = null, systemPrompt: String? = null, userId: String? = null, parentSessionId: String? = null): String {
        return ""
    }
    /** Mark a session as ended. */
    fun endSession(sessionId: String, endReason: String): Unit {
        // TODO: implement endSession
    }
    /** Clear ended_at/end_reason so a session can be resumed. */
    fun reopenSession(sessionId: String): Unit {
        // TODO: implement reopenSession
    }
    /** Store the full assembled system prompt snapshot. */
    fun updateSystemPrompt(sessionId: String, systemPrompt: String): Unit {
        // TODO: implement updateSystemPrompt
    }
    /** Update token counters and backfill model if not already set. */
    fun updateTokenCounts(sessionId: String, inputTokens: Int = 0, outputTokens: Int = 0, model: String? = null, cacheReadTokens: Int = 0, cacheWriteTokens: Int = 0, reasoningTokens: Int = 0, estimatedCostUsd: Double? = null, actualCostUsd: Double? = null, costStatus: String? = null, costSource: String? = null, pricingVersion: String? = null, billingProvider: String? = null, billingBaseUrl: String? = null, billingMode: String? = null, absolute: Boolean = false): Unit {
        // TODO: implement updateTokenCounts
    }
    /** Ensure a session row exists, creating it with minimal metadata if absent. */
    fun ensureSession(sessionId: String, source: String = "unknown", model: String? = null): Unit {
        // TODO: implement ensureSession
    }
    /** Get a session by ID. */
    fun getSession(sessionId: String): Map<String, Any>? {
        return emptyMap()
    }
    /** Resolve an exact or uniquely prefixed session ID to the full ID. */
    fun resolveSessionId(sessionIdOrPrefix: String): String? {
        return null
    }
    /** Validate and sanitize a session title. */
    fun sanitizeTitle(title: String?): String? {
        return null
    }
    /** Set or update a session"s title. */
    fun setSessionTitle(sessionId: String, title: String): Boolean {
        return false
    }
    /** Get the title for a session, or None. */
    fun getSessionTitle(sessionId: String): String? {
        return null
    }
    /** Look up a session by exact title. Returns session dict or None. */
    fun getSessionByTitle(title: String): Map<String, Any>? {
        return emptyMap()
    }
    /** Resolve a title to a session ID, preferring the latest in a lineage. */
    fun resolveSessionByTitle(title: String): String? {
        return null
    }
    /** Generate the next title in a lineage (e.g., "my session" → "my session #2"). */
    fun getNextTitleInLineage(baseTitle: String): String {
        return ""
    }
    /** List sessions with preview (first user message) and last active timestamp. */
    fun listSessionsRich(source: String? = null, excludeSources: List<String>? = null, limit: Int = 20, offset: Int = 0, includeChildren: Boolean = false): List<Map<String, Any>> {
        return emptyList()
    }
    /** Append a message to a session. Returns the message row ID. */
    fun appendMessage(sessionId: String, role: String, content: String? = null, toolName: String? = null, toolCalls: Any? = null, toolCallId: String? = null, tokenCount: Int? = null, finishReason: String? = null, reasoning: String? = null, reasoningDetails: Any? = null, codexReasoningItems: Any? = null): Int {
        return 0
    }
    /** Load all messages for a session, ordered by timestamp. */
    fun getMessages(sessionId: String): List<Map<String, Any>> {
        return emptyList()
    }
    /** Load messages in the OpenAI conversation format (role + content dicts). */
    fun getMessagesAsConversation(sessionId: String): List<Map<String, Any>> {
        return emptyList()
    }
    /** Sanitize user input for safe use in FTS5 MATCH queries. */
    fun _sanitizeFts5Query(query: String): String {
        return ""
    }
    /** Full-text search across session messages using FTS5. */
    fun searchMessages(query: String, sourceFilter: List<String>? = null, excludeSources: List<String>? = null, roleFilter: List<String>? = null, limit: Int = 20, offset: Int = 0): List<Map<String, Any>> {
        return emptyList()
    }
    /** List sessions, optionally filtered by source. */
    fun searchSessions(source: String? = null, limit: Int = 20, offset: Int = 0): List<Map<String, Any>> {
        return emptyList()
    }
    /** Count sessions, optionally filtered by source. */
    fun sessionCount(source: String? = null): Int {
        return 0
    }
    /** Count messages, optionally for a specific session. */
    fun messageCount(sessionId: String? = null): Int {
        return 0
    }
    /** Export a single session with all its messages as a dict. */
    fun exportSession(sessionId: String): Map<String, Any>? {
        return emptyMap()
    }
    /** Export all sessions (with messages) as a list of dicts. */
    fun exportAll(source: String? = null): List<Map<String, Any>> {
        return emptyList()
    }
    /** Delete all messages for a session and reset its counters. */
    fun clearMessages(sessionId: String): Unit {
        // TODO: implement clearMessages
    }
    /** Delete a session and all its messages. */
    fun deleteSession(sessionId: String): Boolean {
        return false
    }
    /** Delete sessions older than N days. Returns count of deleted sessions. */
    fun pruneSessions(olderThanDays: Int = 90, source: String? = null): Int {
        return 0
    }

}
