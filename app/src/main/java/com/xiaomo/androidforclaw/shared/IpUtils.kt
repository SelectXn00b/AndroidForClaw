package com.xiaomo.androidforclaw.shared

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/net/ip.ts
 *
 * IP address parsing, validation, and special-use detection.
 * Replaces ipaddr.js dependency with java.net.InetAddress.
 */

/** Check if a string is a canonical dotted-decimal IPv4 (e.g. "192.168.1.1"). */
fun isCanonicalDottedDecimalIPv4(value: String): Boolean {
    val parts = value.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val n = part.toIntOrNull() ?: return false
        n in 0..255 && part == n.toString() // reject leading zeros
    }
}

/** Check if a string is an IPv4 address (canonical dotted-decimal). */
fun isIpv4Address(value: String): Boolean = isCanonicalDottedDecimalIPv4(value)

/** Check if a string is a legacy IPv4 literal (octal, hex, or fewer than 4 parts). */
fun isLegacyIpv4Literal(value: String): Boolean {
    if (isCanonicalDottedDecimalIPv4(value)) return false
    // Check for octal (leading 0), hex (0x), or fewer parts
    val parts = value.split(".")
    if (parts.isEmpty() || parts.size > 4) return false
    return parts.all { part ->
        part.startsWith("0x", ignoreCase = true) ||
            (part.length > 1 && part.startsWith("0") && part.all { it.isDigit() }) ||
            part.toLongOrNull() != null
    }
}

/** Parse a canonical IP address string. Returns null on failure. */
fun parseCanonicalIpAddress(value: String): InetAddress? {
    return try {
        InetAddress.getByName(value)
    } catch (_: Exception) {
        null
    }
}

/** Parse a loose IP address (with bracket stripping for IPv6). */
fun parseLooseIpAddress(value: String): InetAddress? {
    val trimmed = value.trim()
    val unwrapped = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        trimmed.substring(1, trimmed.length - 1)
    } else trimmed
    return parseCanonicalIpAddress(unwrapped)
}

/**
 * Extract embedded IPv4 from an IPv4-mapped IPv6 address (::ffff:a.b.c.d).
 * Returns null if not an IPv4-mapped address.
 */
fun extractEmbeddedIpv4FromIpv6(address: Inet6Address): Inet4Address? {
    val bytes = address.address
    if (bytes.size != 16) return null
    // Check for ::ffff: prefix
    val isV4Mapped = (0..9).all { bytes[it] == 0.toByte() } &&
        bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
    if (!isV4Mapped) return null
    val v4Bytes = byteArrayOf(bytes[12], bytes[13], bytes[14], bytes[15])
    return try {
        InetAddress.getByAddress(v4Bytes) as? Inet4Address
    } catch (_: Exception) {
        null
    }
}

/** Check if an IPv4 address is in a blocked special-use range (RFC 6890). */
fun isBlockedSpecialUseIpv4Address(address: Inet4Address): Boolean {
    val b = address.address
    val b0 = b[0].toInt() and 0xFF
    val b1 = b[1].toInt() and 0xFF
    val b2 = b[2].toInt() and 0xFF

    return when {
        b0 == 0 -> true                           // 0.0.0.0/8
        b0 == 10 -> true                           // 10.0.0.0/8
        b0 == 100 && b1 in 64..127 -> true         // 100.64.0.0/10
        b0 == 127 -> true                          // 127.0.0.0/8
        b0 == 169 && b1 == 254 -> true             // 169.254.0.0/16
        b0 == 172 && b1 in 16..31 -> true          // 172.16.0.0/12
        b0 == 192 && b1 == 0 && b2 == 0 -> true    // 192.0.0.0/24
        b0 == 192 && b1 == 168 -> true             // 192.168.0.0/16
        b0 == 198 && b1 in 18..19 -> true          // 198.18.0.0/15
        b0 >= 240 -> true                          // 240.0.0.0/4
        b.all { (it.toInt() and 0xFF) == 255 } -> true // 255.255.255.255
        else -> false
    }
}

/** Check if an IPv6 address is in a blocked special-use range. */
fun isBlockedSpecialUseIpv6Address(address: Inet6Address): Boolean {
    // Check for IPv4-mapped first
    val embedded = extractEmbeddedIpv4FromIpv6(address)
    if (embedded != null) return isBlockedSpecialUseIpv4Address(embedded)

    return address.isLoopbackAddress ||      // ::1
        address.isLinkLocalAddress ||        // fe80::/10
        address.isSiteLocalAddress ||        // fec0::/10 (deprecated)
        address.isAnyLocalAddress ||         // ::
        address.isMulticastAddress           // ff00::/8
}

/** Validate a dotted-decimal IPv4 input. Returns error message or null if valid. */
fun validateDottedDecimalIPv4Input(value: String?): String? {
    if (value.isNullOrBlank()) return "IP address is required for custom bind mode"
    if (isCanonicalDottedDecimalIPv4(value)) return null
    return "Invalid IPv4 address (e.g., 192.168.1.100)"
}

/** Strip URL userinfo (username:password@). */
fun stripUrlUserInfo(value: String): String {
    return try {
        val url = java.net.URL(value)
        if (url.userInfo == null) return value
        val noAuth = value.replaceFirst("${url.userInfo}@", "")
        noAuth
    } catch (_: Exception) {
        value
    }
}
