package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class HomeAssistantAdapter(
    val config: Map<String, Any>
) {
    private fun nextId(): Unit {
    // Hermes: _next_id
        // Hermes: nextId
    }
    suspend fun connect(): Unit {
    // Hermes: connect
        // Hermes: connect
    }
    private suspend fun wsConnect(): Unit {
    // Hermes: _ws_connect
        // Hermes: wsConnect
    }
    private suspend fun cleanupWs(): Unit {
    // Hermes: _cleanup_ws
        // Hermes: cleanupWs
    }
    suspend fun disconnect(): Unit {
    // Hermes: disconnect
        // Hermes: disconnect
    }
    private suspend fun listenLoop(): Unit {
    // Hermes: _listen_loop
        // Hermes: listenLoop
    }
    private suspend fun readEvents(): Unit {
    // Hermes: _read_events
        // Hermes: readEvents
    }
    private suspend fun handleHaEvent(event: String): Unit {
    // Hermes: _handle_ha_event
        // Hermes: handleHaEvent
    }
    private fun formatStateChange(entity_id: String, old_state: String, new_state: String): Unit {
    // Hermes: _format_state_change
        // Hermes: formatStateChange
    }
    suspend fun send(chat_id: String, content: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send
        // Hermes: send
    }
    suspend fun sendTyping(chat_id: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_typing
        // Hermes: sendTyping
    }
    suspend fun getChatInfo(chat_id: String): Any? {
    // Hermes: get_chat_info
        return null
        // Hermes: getChatInfo
        return null
    }
}
