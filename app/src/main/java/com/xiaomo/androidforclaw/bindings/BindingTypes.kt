package com.xiaomo.androidforclaw.bindings

/**
 * OpenClaw module: bindings
 * Source: OpenClaw/src/bindings/records.ts
 *
 * Types for session-binding records that associate conversations with sessions.
 */

data class ConversationRef(
    val channel: String,
    val accountId: String,
    val conversationId: String
)

data class SessionBindingRecord(
    val bindingId: String,
    val sessionKey: String,
    val conversation: ConversationRef,
    val createdAt: Long,
    val lastTouchedAt: Long
)

data class SessionBindingCapabilities(
    val canBind: Boolean,
    val canUnbind: Boolean
)
