package com.xiaomo.androidforclaw.hermes.gateway

/**
 * Session store — manages session lifecycle, persistence, and lookup.
 *
 * Ported from gateway/session.py (1086 lines)
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ── Helpers ─────────────────────────────────────────────────────────

private const val TAG = "SessionStore"

private fun now(): String = Instant.now().toString()

/** Hash a value for PII-safe logging. */
private fun hashId(value: String): String {
    if (value.isEmpty()) return ""
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(value.toByteArray())
    return hash.take(8).joinToString("") { "%02x".format(it) }
}

/** Hash a sender id for PII-safe logging. */
private fun hashSenderId(value: String): String = hashId(value)

/** Hash a chat id for PII-safe logging. */
private fun hashChatId(value: String): String = hashId(value)

private val PII_SAFE_PLATFORMS = setOf("api_server", "webhook")

// ── Data classes ────────────────────────────────────────────────────

/** Where a session was loaded from. */
enum class SessionSource {
    NEW,
    PERSISTED,
    RECOVERED;

    /** Human-readable description. */
    val description: String get() = when (this) {
        NEW -> "fresh session"
        PERSISTED -> "restored from disk"
        RECOVERED -> "recovered after restart"
    }

    fun toDict(): Map<String, Any> = mapOf("source" to name)

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionSource {
            val name = (data["source"] as? String) ?: "NEW"
            return try { valueOf(name) } catch (_unused: Exception) { NEW }
        }
    }
}

/**
 * A single session record.
 */
data class SessionContext(
    val sessionKey: String,
    val platform: String,
    val chatId: String,
    val userId: String,
    var chatName: String = "",
    var userName: String = "",
    val chatType: String = "dm",
    val createdAt: String = now(),
    var lastMessageAt: String = now(),
    val messageCount: AtomicInteger = AtomicInteger(0),
    val turnCount: AtomicInteger = AtomicInteger(0),
    val inputTokens: AtomicLong = AtomicLong(0),
    val outputTokens: AtomicLong = AtomicLong(0),
    var modelOverride: String? = null,
    var systemPromptOverride: String? = null,
    @Volatile var isProcessing: Boolean = false,
    var processingStartedAt: Long = 0L,
    var parentSessionKey: String? = null,
    val source: SessionSource = SessionSource.NEW,
) {
    fun recordMessage() {
        messageCount.incrementAndGet()
        lastMessageAt = now()
    }

    fun recordTurn() {
        turnCount.incrementAndGet()
    }

    fun recordTokens(input: Long, output: Long) {
        inputTokens.addAndGet(input)
        outputTokens.addAndGet(output)
    }

    fun markProcessing() {
        isProcessing = true
        processingStartedAt = System.currentTimeMillis()
    }

    fun markIdle() {
        isProcessing = false
        processingStartedAt = 0L
    }

    fun label(): String = "$platform:$chatId (user=$userId)"

    fun toDict(): Map<String, Any?> = buildMap {
        put("session_key", sessionKey)
        put("platform", platform)
        put("chat_id", chatId)
        put("user_id", userId)
        put("chat_name", chatName)
        put("user_name", userName)
        put("chat_type", chatType)
        put("created_at", createdAt)
        put("last_message_at", lastMessageAt)
        put("message_count", messageCount.get())
        put("turn_count", turnCount.get())
        put("input_tokens", inputTokens.get())
        put("output_tokens", outputTokens.get())
        modelOverride?.let { put("model_override", it) }
        systemPromptOverride?.let { put("system_prompt_override", it) }
        put("is_processing", isProcessing)
        put("processing_started_at", processingStartedAt)
        parentSessionKey?.let { put("parent_session_key", it) }
        put("source", source.name)
    }

    fun toJson(): JSONObject = JSONObject(toDict())

    companion object {
        fun fromDict(data: Map<String, Any?>): SessionContext = SessionContext(
            sessionKey = data["session_key"] as? String ?: "",
            platform = data["platform"] as? String ?: "",
            chatId = data["chat_id"] as? String ?: "",
            userId = data["user_id"] as? String ?: "",
            chatName = data["chat_name"] as? String ?: "",
            userName = data["user_name"] as? String ?: "",
            chatType = data["chat_type"] as? String ?: "dm",
            createdAt = data["created_at"] as? String ?: now(),
            lastMessageAt = data["last_message_at"] as? String ?: now(),
            source = try { SessionSource.valueOf(data["source"] as? String ?: "NEW") } catch (_unused: Exception) { SessionSource.NEW },
        ).apply {
            messageCount.set((data["message_count"] as? Number)?.toInt() ?: 0)
            turnCount.set((data["turn_count"] as? Number)?.toInt() ?: 0)
            inputTokens.set((data["input_tokens"] as? Number)?.toLong() ?: 0)
            outputTokens.set((data["output_tokens"] as? Number)?.toLong() ?: 0)
            modelOverride = data["model_override"] as? String
            systemPromptOverride = data["system_prompt_override"] as? String
            isProcessing = data["is_processing"] as? Boolean ?: false
            processingStartedAt = (data["processing_started_at"] as? Number)?.toLong() ?: 0
            parentSessionKey = data["parent_session_key"] as? String
        }

        fun fromJson(json: JSONObject): SessionContext {
            val map = mutableMapOf<String, Any?>()
            json.keys().forEach { key -> map[key] = json.opt(key) }
            return fromDict(map)
        }
    }
}

// ── Helper functions ────────────────────────────────────────────────

/** Build a session key from platform + chat id + user id. */
fun buildSessionKey(platform: String, chatId: String, userId: String): String =
    "$platform:$chatId:$userId"

/** Build a session context from platform adapter metadata. */
fun buildSessionContext(
    sessionKey: String,
    platform: String,
    chatId: String,
    userId: String,
    chatName: String = "",
    userName: String = "",
    chatType: String = "dm",
): SessionContext = SessionContext(
    sessionKey = sessionKey,
    platform = platform,
    chatId = chatId,
    userId = userId,
    chatName = chatName,
    userName = userName,
    chatType = chatType,
    source = SessionSource.NEW,
)

/** Build a system-prompt fragment for a session. */
fun buildSessionContextPrompt(session: SessionContext, redactPii: Boolean = false): String = buildString {
    appendLine("# Session Context")
    appendLine("- Platform: ${session.platform}")
    appendLine("- Chat: ${session.chatName.ifEmpty { session.chatId }}")
    appendLine("- User: ${session.userName.ifEmpty { session.userId }}")
    appendLine("- Chat type: ${session.chatType}")
    appendLine("- Messages: ${session.messageCount.get()}")
    appendLine("- Turns: ${session.turnCount.get()}")
    if (session.modelOverride != null) {
        appendLine("- Model override: ${session.modelOverride}")
    }
}

// ── SessionStore ────────────────────────────────────────────────────

/**
 * Session store — manages session lifecycle and persistence.
 * Thread-safe. Sessions are persisted to disk periodically.
 */
class SessionStore(
    private val persistDir: File? = null,
) {
    private val sessions: ConcurrentHashMap<String, SessionContext> = ConcurrentHashMap()

    /** Get or create a session. */
    fun getOrCreate(
        sessionKey: String,
        platform: String,
        chatId: String,
        userId: String,
        chatName: String = "",
        userName: String = "",
        chatType: String = "dm",
    ): SessionContext = sessions.getOrPut(sessionKey) {
        buildSessionContext(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    /** Get or create session from a source map (Python-compatible). */
    fun getOrCreateSession(source: Map<String, Any?>, forceNew: Boolean = false): SessionContext {
        val platform = source["platform"] as? String ?: "unknown"
        val chatId = source["chat_id"] as? String ?: ""
        val userId = source["user_id"] as? String ?: ""
        val chatName = source["chat_name"] as? String ?: ""
        val userName = source["user_name"] as? String ?: ""
        val chatType = source["chat_type"] as? String ?: "dm"
        val sessionKey = buildSessionKey(platform, chatId, userId)

        if (forceNew) {
            sessions.remove(sessionKey)
        }

        return getOrCreate(sessionKey, platform, chatId, userId, chatName, userName, chatType)
    }

    fun get(sessionKey: String): SessionContext? = sessions[sessionKey]
    fun remove(sessionKey: String) { sessions.remove(sessionKey) }
    val keys: Set<String> get() = sessions.keys.toSet()
    val all: Collection<SessionContext> get() = sessions.values
    val size: Int get() = sessions.size
    val processingCount: Int get() = sessions.values.count { it.isProcessing }
    val hasAnySessions: Boolean get() = sessions.isNotEmpty()

    fun clear() { sessions.clear() }

    /** Check if a session is expired (idle timeout). */
    fun isSessionExpired(entry: SessionContext, idleMinutes: Int = 1440): Boolean {
        val last = Instant.parse(entry.lastMessageAt)
        val elapsed = java.time.Duration.between(last, Instant.now())
        return elapsed.toMinutes() > idleMinutes
    }

    /** Check if session should be reset (daily boundary or idle). */
    fun shouldReset(entry: SessionContext, resetHour: Int = 4): String? {
        // Daily reset check
        val now = java.time.ZonedDateTime.now()
        val lastMsg = java.time.ZonedDateTime.parse(entry.lastMessageAt)
        if (now.toLocalDate() != lastMsg.toLocalDate() && now.hour >= resetHour) {
            return "daily"
        }
        // Idle reset check
        if (isSessionExpired(entry)) {
            return "idle"
        }
        return null
    }

    /** Update session with latest token counts. */
    fun updateSession(sessionKey: String, promptTokens: Int = 0, completionTokens: Int = 0) {
        val entry = sessions[sessionKey] ?: return
        entry.recordTurn()
        entry.recordTokens(promptTokens.toLong(), completionTokens.toLong())
        entry.lastMessageAt = now()
    }

    /** Suspend a session (mark as not processing). */
    fun suspendSession(sessionKey: String): Boolean {
        val entry = sessions[sessionKey] ?: return false
        entry.markIdle()
        return true
    }

    /** Suspend all recently-active sessions. */
    fun suspendRecentlyActive(maxAgeSeconds: Int = 120): Int {
        val cutoff = System.currentTimeMillis() - maxAgeSeconds * 1000
        var count = 0
        sessions.values.filter { it.isProcessing && it.processingStartedAt < cutoff }.forEach {
            it.markIdle()
            count++
        }
        return count
    }

    /** Reset a session (clear conversation state). */
    fun resetSession(sessionKey: String): SessionContext? {
        val old = sessions[sessionKey] ?: return null
        val newEntry = SessionContext(
            sessionKey = old.sessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType,
            source = old.source,
        )
        sessions[sessionKey] = newEntry
        return newEntry
    }

    /** List sessions, optionally filtered by activity. */
    fun listSessions(activeMinutes: Int? = null): List<SessionContext> {
        val all = sessions.values.toList()
        if (activeMinutes == null) return all
        val cutoff = Instant.now().minus(java.time.Duration.ofMinutes(activeMinutes.toLong()))
        return all.filter { Instant.parse(it.lastMessageAt).isAfter(cutoff) }
    }

    /** Get transcript file path for a session. */
    fun getTranscriptPath(sessionId: String): File? {
        val dir = persistDir ?: return null
        return File(dir, "transcripts/$sessionId.jsonl")
    }

    /** Append a message to the session transcript. */
    fun appendToTranscript(sessionId: String, message: Map<String, Any?>) {
        val path = getTranscriptPath(sessionId) ?: return
        path.parentFile?.mkdirs()
        val json = JSONObject(message)
        path.appendText(json.toString() + "\n", Charsets.UTF_8)
    }

    /** Rewrite the entire transcript for a session. */
    fun rewriteTranscript(sessionId: String, messages: List<Map<String, Any?>>) {
        val path = getTranscriptPath(sessionId) ?: return
        path.parentFile?.mkdirs()
        val content = messages.joinToString("\n") { JSONObject(it).toString() }
        path.writeText(content, Charsets.UTF_8)
    }

    /** Load transcript for a session. */
    fun loadTranscript(sessionId: String): List<Map<String, Any?>> {
        val path = getTranscriptPath(sessionId) ?: return emptyList()
        if (!path.exists()) return emptyList()
        return try {
            path.readLines(Charsets.UTF_8)
                .filter { it.isNotBlank() }
                .map { line ->
                    val json = JSONObject(line)
                    val map = mutableMapOf<String, Any?>()
                    json.keys().forEach { key -> map[key] = json.opt(key) }
                    map.toMap()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load transcript for $sessionId: ${e.message}")
            emptyList()
        }
    }

    /** Persist all sessions to disk. */
    fun persist() {
        val dir = persistDir ?: return
        dir.mkdirs()
        val sessionsJson = JSONArray()
        sessions.values.forEach { sessionsJson.put(it.toJson()) }
        val file = File(dir, "sessions.json")
        file.writeText(sessionsJson.toString(2), Charsets.UTF_8)
        Log.d(TAG, "Persisted ${sessions.size} sessions to ${file.absolutePath}")
    }

    /** Load sessions from disk. */
    fun load() {
        val dir = persistDir ?: return
        val file = File(dir, "sessions.json")
        if (!file.exists()) return
        try {
            val json = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until json.length()) {
                val session = SessionContext.fromJson(json.getJSONObject(i))
                sessions[session.sessionKey] = session
            }
            Log.i(TAG, "Loaded ${sessions.size} sessions from ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sessions: ${e.message}")
        }
    }

    /** Switch a session key to point at a different session (for /resume). */
    fun switchSession(sessionKey: String, targetSessionKey: String): SessionContext? {
        val old = sessions[sessionKey] ?: return null
        if (sessionKey == targetSessionKey) return old

        val newEntry = SessionContext(
            sessionKey = targetSessionKey,
            platform = old.platform,
            chatId = old.chatId,
            userId = old.userId,
            chatName = old.chatName,
            userName = old.userName,
            chatType = old.chatType,
            source = old.source,
        )
        sessions[targetSessionKey] = newEntry
        return newEntry
    }
    fun getSessionCount(): Int = sessions.size

    /** Check if a session exists. */
    fun hasSession(sessionKey: String): Boolean = sessions.containsKey(sessionKey)

    /** Get all session keys. */
    fun getSessionKeys(): List<String> = sessions.keys.toList()

    /** Remove a session by key. */
    fun removeSession(sessionKey: String): SessionContext? = sessions.remove(sessionKey)

    /** Remove all sessions. */
    fun clearAllSessions() { sessions.clear() }

    /** Get sessions filtered by platform. */
    fun getSessionsByPlatform(platform: String): List<SessionContext> {
        return sessions.values.filter { it.platform == platform }
    }

    /** Get the last active session key. */
    fun getLastActiveSession(): String? {
        return sessions.entries.maxByOrNull { it.value.createdAt }?.key
    }

    /** Update the last active timestamp for a session. */
    fun touchSession(sessionKey: String) {
        // SessionContext has no lastActive field; createdAt is immutable
    }

    /** Get the total message count across all sessions. */
    fun getTotalMessageCount(): Int {
        return sessions.size
    }

}
// ── SessionManager (ACP) ───────────────────────────────────────────

/**
 * Higher-level session manager for ACP protocol sessions.
 * Simplified Android port — manages agent sessions.
 */
class SessionManager(
    private val sessionsDir: File,
) {
    private val sessions = ConcurrentHashMap<String, Map<String, Any?>>()
    private val taskCwds = ConcurrentHashMap<String, String>()

    fun createSession(cwd: String): Map<String, Any?> {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = mapOf<String, Any?>(
            "session_id" to sessionId,
            "cwd" to cwd,
            "created_at" to now(),
        )
        sessions[sessionId] = session
        taskCwds[sessionId] = cwd
        return session
    }

    fun getSession(sessionId: String): Map<String, Any?>? = sessions[sessionId]

    


    fun forkSession(sessionId: String, cwd: String): Map<String, Any?> {
        val newId = java.util.UUID.randomUUID().toString()
        val original = sessions[sessionId] ?: emptyMap()
        val forked = original.toMutableMap().apply {
            put("session_id", newId)
            put("cwd", cwd)
            put("forked_from", sessionId)
            put("created_at", now())
        }
        sessions[newId] = forked
        taskCwds[newId] = cwd
        return forked
    }

    fun updateCwd(sessionId: String, cwd: String) {
        taskCwds[sessionId] = cwd
    }

    fun cleanup() {
        sessions.clear()
        taskCwds.clear()
    }

    fun saveSession(sessionId: String) {
        val session = sessions[sessionId] ?: return
        val dir = File(sessionsDir, "acp")
        dir.mkdirs()
        val file = File(dir, "$sessionId.json")
        file.writeText(JSONObject(session).toString(2), Charsets.UTF_8)
    }

    /** Switch a session key to point at a different session (for /resume). */

    private fun registerTaskCwd(taskId: String, cwd: String) { taskCwds[taskId] = cwd }
    private fun clearTaskCwd(taskId: String) { taskCwds.remove(taskId) }


    


    


    


    


    



    /** Human-readable description of the source. */
    fun description(): String {
        return ""
    }
    /** Load sessions index from disk if not already loaded. */
    fun _ensureLoaded(): Unit {
        // TODO: implement _ensureLoaded
    }
    /** Load sessions index from disk. Must be called with self._lock held. */
    fun _ensureLoadedLocked(): Unit {
        // TODO: implement _ensureLoadedLocked
    }
    /** Save sessions index to disk (kept for session key -> ID mapping). */
    fun _save(): Unit {
        // TODO: implement _save
    }
    /** Generate a session key from a source. */
    fun _generateSessionKey(source: SessionSource): String {
        return ""
    }
    /** Check if a session has expired based on its reset policy. */
    fun _isSessionExpired(entry: Any?): Boolean {
        return false
    }
    /** Check if a session should be reset based on policy. */
    fun _shouldReset(entry: Any?, source: SessionSource): String? {
        return null
    }
    /** Check if any sessions have ever been created (across all platforms). */
    fun hasAnySessions(): Boolean {
        return false
    }


    /** Add a message to the local cache. */
    fun addMessage(role: String, content: String, kwargs: Any): Unit {
        // TODO: implement addMessage
    }
    /** Get message history for LLM context. */
    fun getHistory(maxMessages: Int = 50): List<Map<String, Any>> {
        return emptyList()
    }
    /** Get the Honcho client, initializing if needed. */
    fun honcho(): Any? {
        return null
    }
    /** Get or create a Honcho peer. */
    fun _getOrCreatePeer(peerId: String): Any {
        throw NotImplementedError("_getOrCreatePeer")
    }
    /** Get or create a Honcho session with peers configured. */
    fun _getOrCreateHonchoSession(sessionId: String, userPeer: Any, assistantPeer: Any): Pair<Any, Any?> {
        throw NotImplementedError("_getOrCreateHonchoSession")
    }
    /** Sanitize an ID to match Honcho's pattern: ^[a-zA-Z0-9_-]+ */
    fun _sanitizeId(idStr: String): String {
        return ""
    }
    /** Internal: write unsynced messages to Honcho synchronously. */
    fun _flushSession(session: Any?): Boolean {
        return false
    }
    /** Background daemon thread: drains the async write queue. */
    fun _asyncWriterLoop(): Unit {
        // TODO: implement _asyncWriterLoop
    }
    /** Save messages to Honcho, respecting write_frequency. */
    fun save(session: Any?): Unit {
        // TODO: implement save
    }
    /** Flush all pending unsynced messages for all cached sessions. */
    fun flushAll(): Unit {
        // TODO: implement flushAll
    }
    /** Gracefully shut down the async writer thread. */
    fun shutdown(): Unit {
        // TODO: implement shutdown
    }
    /** Delete a session from local cache. */
    fun delete(key: String): Boolean {
        return false
    }
    /** Create a new session, preserving the old one for user modeling. */
    fun newSession(key: String): Any? {
        return null
    }
    /** Pick a reasoning level for a dialectic query. */
    fun _dynamicReasoningLevel(query: String): String {
        return ""
    }
    /** Query Honcho's dialectic endpoint about a peer. */
    fun dialecticQuery(sessionKey: String, query: String, reasoningLevel: Any? = null, peer: String = "user"): String {
        return ""
    }
    /** Fire a dialectic_query in a background thread, caching the result. */
    fun prefetchDialectic(sessionKey: String, query: String): Unit {
        // TODO: implement prefetchDialectic
    }
    /** Store a prefetched dialectic result in a thread-safe way. */
    fun setDialecticResult(sessionKey: String, result: String): Unit {
        // TODO: implement setDialecticResult
    }
    /** Return and clear the cached dialectic result for this session. */
    fun popDialecticResult(sessionKey: String): String {
        return ""
    }
    /** Fire get_prefetch_context in a background thread, caching the result. */
    fun prefetchContext(sessionKey: String, userMessage: Any? = null): Unit {
        // TODO: implement prefetchContext
    }
    /** Store a prefetched context result in a thread-safe way. */
    fun setContextResult(sessionKey: String, result: Map<String, String>): Unit {
        // TODO: implement setContextResult
    }
    /** Return and clear the cached context result for this session. */
    fun popContextResult(sessionKey: String): Map<String, String> {
        return emptyMap()
    }
    /** Pre-fetch user and AI peer context from Honcho. */
    fun getPrefetchContext(sessionKey: String, userMessage: Any? = null): Map<String, String> {
        return emptyMap()
    }
    /** Upload local session history to Honcho as a file. */
    fun migrateLocalHistory(sessionKey: String, messages: List<Map<String, Any>>): Boolean {
        return false
    }
    /** Format local messages as an XML transcript for Honcho file upload. */
    fun _formatMigrationTranscript(sessionKey: String, messages: List<Map<String, Any>>): ByteArray {
        throw NotImplementedError("_formatMigrationTranscript")
    }
    /** Upload MEMORY.md and USER.md to Honcho as files. */
    fun migrateMemoryFiles(sessionKey: String, memoryDir: String): Boolean {
        return false
    }
    /** Normalize Honcho card payloads into a plain list of strings. */
    fun _normalizeCard(card: Any): List<String> {
        return emptyList()
    }
    /** Fetch a peer card directly from the peer object. */
    fun _fetchPeerCard(peerId: String): List<String> {
        return emptyList()
    }
    /** Fetch representation + peer card directly from a peer object. */
    fun _fetchPeerContext(peerId: String, searchQuery: Any? = null): Map<String, Any> {
        return emptyMap()
    }
    /** Fetch the user peer's card — a curated list of key facts. */
    fun getPeerCard(sessionKey: String): List<String> {
        return emptyList()
    }
    /** Semantic search over Honcho session context. */
    fun searchContext(sessionKey: String, query: String, maxTokens: Int = 800): String {
        return ""
    }
    /** Write a conclusion about the user back to Honcho. */
    fun createConclusion(sessionKey: String, content: String): Boolean {
        return false
    }
    /** Seed the AI peer's Honcho representation from text content. */
    fun seedAiIdentity(sessionKey: String, content: String, source: String = "manual"): Boolean {
        return false
    }
    /** Fetch the AI peer's current Honcho representation. */
    fun getAiRepresentation(sessionKey: String): Map<String, String> {
        return emptyMap()
    }

}
