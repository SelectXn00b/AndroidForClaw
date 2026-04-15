package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class WeComCryptoError {
    // Hermes: WeComCryptoError
}

class SignatureError {
    // Hermes: SignatureError
}

class DecryptError {
    // Hermes: DecryptError
}

class EncryptError {
    // Hermes: EncryptError
}

class PKCS7Encoder {
    // Hermes: PKCS7Encoder
    fun encode(text: String): Unit {
    // Hermes: encode
        // Hermes: encode
    }
    fun decode(decrypted: String): Unit {
    // Hermes: decode
        // Hermes: decode
    }
}

class WXBizMsgCrypt(
    val token: String,
    val encoding_aes_key: String,
    val receive_id: String
) {
    fun verifyUrl(msg_signature: String, timestamp: Long, nonce: String, echostr: String): Unit {
    // Hermes: verify_url
        // Hermes: verifyUrl
    }
    fun decrypt(msg_signature: String, timestamp: Long, nonce: String, encrypt: String): Unit {
    // Hermes: decrypt
        // Hermes: decrypt
    }
    fun encrypt(plaintext: String, nonce: String, timestamp: Long): Unit {
    // Hermes: encrypt
        // Hermes: encrypt
    }
    private fun encryptBytes(raw: String): Unit {
    // Hermes: _encrypt_bytes
        // Hermes: encryptBytes
    }
    private fun randomNonce(length: Int): Unit {
    // Hermes: _random_nonce
        // Hermes: randomNonce
    }
}
