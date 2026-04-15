package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class _CryptoStateStore(
    val client_state_store: String,
    val joined_rooms: String
) {
    suspend fun isEncrypted(room_id: String): Boolean {
        return false
    // Hermes: is_encrypted
}
}
