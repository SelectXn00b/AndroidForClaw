package com.xiaomo.androidforclaw.bindings

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: bindings
 * Source: OpenClaw/src/bindings/records.ts
 *
 * CRUD helpers for session-binding records that associate conversations with sessions.
 */
object BindingRecords {

    /** In-memory store keyed by bindingId */
    private val store = ConcurrentHashMap<String, SessionBindingRecord>()

    suspend fun createConversationBindingRecord(
        sessionKey: String,
        conversation: ConversationRef
    ): SessionBindingRecord {
        val now = System.currentTimeMillis()
        val record = SessionBindingRecord(
            bindingId = UUID.randomUUID().toString(),
            sessionKey = sessionKey,
            conversation = conversation,
            createdAt = now,
            lastTouchedAt = now
        )
        store[record.bindingId] = record
        return record
    }

    fun getConversationBindingCapabilities(
        channel: String,
        accountId: String
    ): SessionBindingCapabilities {
        // All channels support bind/unbind in the Android runtime
        return SessionBindingCapabilities(canBind = true, canUnbind = true)
    }

    fun listSessionBindingRecords(targetSessionKey: String): List<SessionBindingRecord> {
        return store.values.filter { it.sessionKey == targetSessionKey }
    }

    fun resolveConversationBindingRecord(conversation: ConversationRef): SessionBindingRecord? {
        return store.values.find {
            it.conversation.channel == conversation.channel &&
                it.conversation.accountId == conversation.accountId &&
                it.conversation.conversationId == conversation.conversationId
        }
    }

    fun touchConversationBindingRecord(bindingId: String, at: Long = System.currentTimeMillis()) {
        store.computeIfPresent(bindingId) { _, record ->
            record.copy(lastTouchedAt = at)
        }
    }

    suspend fun unbindConversationBindingRecord(
        bindingId: String? = null,
        conversation: ConversationRef? = null
    ): List<SessionBindingRecord> {
        val removed = mutableListOf<SessionBindingRecord>()
        if (bindingId != null) {
            store.remove(bindingId)?.let { removed.add(it) }
        }
        if (conversation != null) {
            val iter = store.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val conv = entry.value.conversation
                if (conv.channel == conversation.channel &&
                    conv.accountId == conversation.accountId &&
                    conv.conversationId == conversation.conversationId
                ) {
                    removed.add(entry.value)
                    iter.remove()
                }
            }
        }
        return removed
    }
}
