package com.ai.assistance.operit.hermes.gateway

import java.util.concurrent.ConcurrentHashMap

/**
 * R-GW-STREAMING-002: per-chatId outbound dispatcher registry.
 *
 * Bridges the `send_message` tool executor (running inside the
 * HermesAgentLoop on `Dispatchers.IO`) with the per-turn dispatchOutgoing
 * lambda owned by `HermesGatewayController`. Keyed by `historyChatId`
 * (the gateway-tagged chatId, e.g. `gw:weixin:wxid_xxx`) so concurrent
 * gateway runs for different chats can't bleed dispatchers into each
 * other.
 *
 * Lifecycle (owned by `HermesGatewayController.runHermesAgent`):
 *   try {
 *     register(historyChatId, dispatchFn)
 *     core.sendUserMessage(...)
 *   } finally {
 *     unregister(historyChatId)
 *   }
 *
 * The `send_message` tool executor calls `dispatch(chatId, text)` —
 * returns the boolean result of the underlying dispatch (true if the
 * IM gateway accepted the text).
 */
object GatewayOutboundRegistry {

    private val dispatchers = ConcurrentHashMap<String, suspend (String) -> Boolean>()

    fun register(chatId: String, dispatcher: suspend (String) -> Boolean) {
        dispatchers[chatId] = dispatcher
    }

    fun unregister(chatId: String) {
        dispatchers.remove(chatId)
    }

    suspend fun dispatch(chatId: String, text: String): Boolean {
        val fn = dispatchers[chatId] ?: return false
        return fn(text)
    }

    /** Test-only: snapshot of currently registered chatIds. */
    fun registeredChatIds(): Set<String> = dispatchers.keys.toSet()
}
