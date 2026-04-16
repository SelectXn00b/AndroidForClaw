package com.xiaomo.hermes.infra

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/secure-random.ts
 *
 * Cryptographically secure random generation utilities.
 * 1:1 aligned with OpenClaw secure-random.ts API surface.
 */

private val rng = java.security.SecureRandom()

/** Generate a cryptographically secure UUID v4 string. */
fun generateSecureUuid(): String = UUID.randomUUID().toString()

/** Generate a base64url-encoded token of the given byte length. */
fun generateSecureToken(bytes: Int = 16): String {
    val buf = ByteArray(bytes)
    rng.nextBytes(buf)
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
}

/** Generate a hex-encoded random string of the given byte length. */
fun generateSecureHex(byteCount: Int = 16): String {
    val bytes = ByteArray(byteCount)
    rng.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

/** Returns a cryptographically secure fraction in [0, 1). */
fun generateSecureFraction(): Double {
    val bytes = ByteArray(4)
    rng.nextBytes(bytes)
    val uint = ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
    return (uint.toLong() and 0xFFFFFFFFL).toDouble() / 0x1_0000_0000L.toDouble()
}

/** Generate a cryptographically secure integer in [0, maxExclusive). */
fun generateSecureInt(maxExclusive: Int): Int {
    require(maxExclusive > 0) { "maxExclusive must be > 0" }
    return rng.nextInt(maxExclusive)
}

/** Generate a cryptographically secure integer in [minInclusive, maxExclusive). */
fun generateSecureInt(minInclusive: Int, maxExclusive: Int): Int {
    require(maxExclusive > minInclusive) { "maxExclusive must be > minInclusive" }
    return minInclusive + rng.nextInt(maxExclusive - minInclusive)
}

/** Generate a zero-padded numeric code of the given digit count. */
fun generateNumericCode(digits: Int = 6): String {
    require(digits in 1..9) { "digits must be 1..9, got $digits" }
    var bound = 1
    repeat(digits) { bound *= 10 }
    return rng.nextInt(bound).toString().padStart(digits, '0')
}

// --- Hashing utilities (consolidates scattered SHA-256/HMAC usage) ---

/** Compute SHA-256 hex digest. */
fun sha256Hex(data: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }
}

/** Compute SHA-256 hex digest of a UTF-8 string. */
fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

/** Compute SHA-256 raw bytes. */
fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

/** Compute SHA-256 as base64 string (for SRI integrity). */
fun sha256Base64(data: ByteArray): String {
    return java.util.Base64.getEncoder().encodeToString(sha256(data))
}

/** Compute HMAC-SHA256. */
fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

/** Compute HMAC-SHA256 hex digest. */
fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
    return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
}

/** Timing-safe comparison of two byte arrays (constant time). */
fun safeEqual(a: ByteArray, b: ByteArray): Boolean {
    return MessageDigest.isEqual(a, b)
}

/** Timing-safe comparison of two strings. */
fun safeEqualSecret(a: String, b: String): Boolean {
    return safeEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}

// --- Base64url encoding (aligned with TS base64UrlEncode/Decode) ---

fun base64UrlEncode(data: ByteArray): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data)

fun base64UrlDecode(input: String): ByteArray =
    java.util.Base64.getUrlDecoder().decode(input)
