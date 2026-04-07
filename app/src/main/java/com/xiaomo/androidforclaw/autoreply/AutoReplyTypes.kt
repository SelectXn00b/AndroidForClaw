package com.xiaomo.androidforclaw.autoreply

import com.xiaomo.androidforclaw.interactive.InteractiveReply

/**
 * OpenClaw module: auto-reply
 * Source: OpenClaw/src/auto-reply/types.ts
 */

enum class TypingPolicy { AUTO, USER_MESSAGE, SYSTEM_EVENT, INTERNAL_WEBCHAT, HEARTBEAT }

data class ReplyPayload(
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaUrls: List<String>? = null,
    val interactive: InteractiveReply? = null,
    val replyToId: String? = null,
    val replyToCurrent: Boolean? = null,
    val audioAsVoice: Boolean? = null,
    val isError: Boolean? = null,
    val isReasoning: Boolean? = null,
    val isCompactionNotice: Boolean? = null,
    val channelData: Map<String, Any?>? = null
)

data class ModelSelectedContext(
    val provider: String,
    val model: String,
    val thinkLevel: String? = null
)

const val SILENT_REPLY_TOKEN = "NO_REPLY"
