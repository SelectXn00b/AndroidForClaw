package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class DingTalkAdapter(
    val config: Map<String, Any>
) {
    suspend fun connect(): Unit {
    // Hermes: connect
        // Hermes: connect
    }
    private suspend fun runStream(): Unit {
    // Hermes: _run_stream
        // Hermes: runStream
    }
    suspend fun disconnect(): Unit {
    // Hermes: disconnect
        // Hermes: disconnect
    }
    private suspend fun onMessage(message: String): Unit {
    // Hermes: _on_message
        // Hermes: onMessage
    }
    private fun extractText(message: String): Any? {
    // Hermes: _extract_text
        return null
        // Hermes: extractText
        return null
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

class _IncomingHandler(
    val adapter: String,
    val loop: String
) {
    fun process(message: String): Unit {
    // Hermes: process
        // Hermes: process
    }
}
