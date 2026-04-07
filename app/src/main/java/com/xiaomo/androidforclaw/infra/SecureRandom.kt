package com.xiaomo.androidforclaw.infra

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/secure-random.ts
 */

private val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
private val rng = java.security.SecureRandom()

fun generateSecureToken(length: Int = 32): String {
    return buildString(length) {
        repeat(length) {
            append(ALPHANUMERIC[rng.nextInt(ALPHANUMERIC.length)])
        }
    }
}

fun generateSecureHex(byteCount: Int = 16): String {
    val bytes = ByteArray(byteCount)
    rng.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun generateNumericCode(digits: Int = 6): String {
    require(digits in 1..9) { "digits must be 1..9, got $digits" }
    var bound = 1
    repeat(digits) { bound *= 10 }
    return rng.nextInt(bound).toString().padStart(digits, '0')
}
