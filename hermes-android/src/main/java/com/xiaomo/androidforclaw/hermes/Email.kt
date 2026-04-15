package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class EmailAdapter(
    val config: Map<String, Any>
) {
    private fun trimSeenUids(): Unit {
    // Hermes: _trim_seen_uids
        // Hermes: trimSeenUids
    }
    suspend fun connect(): Unit {
    // Hermes: connect
        // Hermes: connect
    }
    suspend fun disconnect(): Unit {
    // Hermes: disconnect
        // Hermes: disconnect
    }
    private suspend fun pollLoop(): Unit {
    // Hermes: _poll_loop
        // Hermes: pollLoop
    }
    private suspend fun checkInbox(): Unit {
    // Hermes: _check_inbox
        // Hermes: checkInbox
    }
    private fun fetchNewMessages(): Any? {
    // Hermes: _fetch_new_messages
        return null
        // Hermes: fetchNewMessages
        return null
    }
    private suspend fun dispatchMessage(msg_data: Map<String, Any>): Unit {
    // Hermes: _dispatch_message
        // Hermes: dispatchMessage
    }
    suspend fun send(chat_id: String, content: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send
        // Hermes: send
    }
    private fun sendEmail(to_addr: String, body: String, reply_to_msg_id: String): Unit {
    // Hermes: _send_email
        // Hermes: sendEmail
    }
    suspend fun sendTyping(chat_id: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_typing
        // Hermes: sendTyping
    }
    suspend fun sendImage(chat_id: String, image_url: String, caption: String, reply_to: String): Unit {
    // Hermes: send_image
        // Hermes: sendImage
    }
    suspend fun sendDocument(chat_id: String, file_path: String, caption: String, file_name: String, reply_to: String): Unit {
    // Hermes: send_document
        // Hermes: sendDocument
    }
    private fun sendEmailWithAttachment(to_addr: String, body: String, file_path: String, file_name: String): Unit {
    // Hermes: _send_email_with_attachment
        // Hermes: sendEmailWithAttachment
    }
    suspend fun getChatInfo(chat_id: String): Any? {
    // Hermes: get_chat_info
        return null
        // Hermes: getChatInfo
        return null
    }
}
