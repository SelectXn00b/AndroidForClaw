package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class MattermostAdapter(
    val config: Map<String, Any>
) {
    private fun headers(): Unit {
    // Hermes: _headers
        // Hermes: headers
    }
    private suspend fun apiGet(path: String): Unit {
    // Hermes: _api_get
        // Hermes: apiGet
    }
    private suspend fun apiPost(path: String, payload: String): Unit {
    // Hermes: _api_post
        // Hermes: apiPost
    }
    private suspend fun apiPut(path: String, payload: String): Unit {
    // Hermes: _api_put
        // Hermes: apiPut
    }
    private suspend fun uploadFile(channel_id: String, file_data: Map<String, Any>, filename: String, content_type: String): Unit {
    // Hermes: _upload_file
        // Hermes: uploadFile
    }
    suspend fun connect(): Unit {
    // Hermes: connect
        // Hermes: connect
    }
    suspend fun disconnect(): Unit {
    // Hermes: disconnect
        // Hermes: disconnect
    }
    suspend fun send(chat_id: String, content: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send
        // Hermes: send
    }
    suspend fun getChatInfo(chat_id: String): Any? {
    // Hermes: get_chat_info
        return null
        // Hermes: getChatInfo
        return null
    }
    suspend fun sendTyping(chat_id: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_typing
        // Hermes: sendTyping
    }
    suspend fun editMessage(chat_id: String, message_id: String, content: String): Unit {
    // Hermes: edit_message
        // Hermes: editMessage
    }
    suspend fun sendImage(chat_id: String, image_url: String, caption: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_image
        // Hermes: sendImage
    }
    suspend fun sendImageFile(chat_id: String, image_path: String, caption: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_image_file
        // Hermes: sendImageFile
    }
    suspend fun sendDocument(chat_id: String, file_path: String, caption: String, file_name: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_document
        // Hermes: sendDocument
    }
    suspend fun sendVoice(chat_id: String, audio_path: String, caption: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_voice
        // Hermes: sendVoice
    }
    suspend fun sendVideo(chat_id: String, video_path: String, caption: String, reply_to: String, metadata: Map<String, Any>): Unit {
    // Hermes: send_video
        // Hermes: sendVideo
    }
    fun formatMessage(content: String): Unit {
    // Hermes: format_message
        // Hermes: formatMessage
    }
    private suspend fun sendUrlAsFile(chat_id: String, url: String, caption: String, reply_to: String, kind: String): Unit {
    // Hermes: _send_url_as_file
        // Hermes: sendUrlAsFile
    }
    private suspend fun sendLocalFile(chat_id: String, file_path: String, caption: String, reply_to: String, file_name: String): Unit {
    // Hermes: _send_local_file
        // Hermes: sendLocalFile
    }
    private suspend fun wsLoop(): Unit {
    // Hermes: _ws_loop
        // Hermes: wsLoop
    }
    private suspend fun wsConnectAndListen(): Unit {
    // Hermes: _ws_connect_and_listen
        // Hermes: wsConnectAndListen
    }
    private suspend fun handleWsEvent(event: String): Unit {
    // Hermes: _handle_ws_event
        // Hermes: handleWsEvent
    }
}
