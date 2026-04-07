package com.xiaomo.androidforclaw.infra

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/net/ssrf.ts + hostname.ts
 *
 * Network security utilities: SSRF protection, hostname normalization, blocked IP detection.
 * Aligned with TS ssrf protection logic.
 */

object NetUtils {

    // --- Hostname normalization (aligned with net/hostname.ts) ---

    /** Normalize a hostname: trim, lowercase, strip trailing dot, unwrap brackets. */
    fun normalizeHostname(hostname: String): String {
        val normalized = hostname.trim().lowercase().trimEnd('.')
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length - 1)
        }
        return normalized
    }

    // --- SSRF protection (aligned with net/ssrf.ts) ---

    /** Hostnames that are always blocked for SSRF protection. */
    private val BLOCKED_HOSTNAMES = setOf(
        "localhost",
        "metadata.google.internal",
        "metadata.google",
        "metadata",
        "instance-data",       // AWS EC2
        "kubernetes.default",  // K8s
        "kubernetes.default.svc",
        "kubernetes.default.svc.cluster.local"
    )

    /** Check if a hostname is blocked for outbound requests. */
    fun isBlockedHostname(hostname: String): Boolean {
        val normalized = normalizeHostname(hostname)
        if (normalized in BLOCKED_HOSTNAMES) return true
        // Block .internal TLD (cloud metadata endpoints)
        if (normalized.endsWith(".internal")) return true
        // Block link-local hostname pattern
        if (normalized.endsWith(".metadata")) return true
        return false
    }

    /** Check if an IP address is a private/loopback/link-local address (SSRF-blocked). */
    fun isBlockedIpAddress(address: InetAddress): Boolean {
        return address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            isCloudMetadataAddress(address)
    }

    /** Check specifically for the AWS/GCP/Azure metadata IP 169.254.169.254. */
    fun isCloudMetadataAddress(address: InetAddress): Boolean {
        if (address is Inet4Address) {
            val bytes = address.address
            // 169.254.169.254
            return bytes[0] == 169.toByte() && bytes[1] == 254.toByte() &&
                bytes[2] == 169.toByte() && bytes[3] == 254.toByte()
        }
        if (address is Inet6Address) {
            // Check for IPv4-mapped IPv6: ::ffff:169.254.169.254
            val bytes = address.address
            if (bytes.size == 16) {
                // Check if it's an IPv4-mapped IPv6 address
                val allZero = (0..9).all { bytes[it] == 0.toByte() }
                val ffff = bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
                if (allZero && ffff) {
                    return bytes[12] == 169.toByte() && bytes[13] == 254.toByte() &&
                        bytes[14] == 169.toByte() && bytes[15] == 254.toByte()
                }
            }
        }
        return false
    }

    /**
     * Validate a URL for SSRF safety: check hostname and resolved IP.
     * Returns null if safe, or an error message if blocked.
     */
    fun validateUrlForSsrf(url: String): String? {
        val parsed = try {
            java.net.URL(url)
        } catch (_: Exception) {
            return "Invalid URL: $url"
        }

        val hostname = parsed.host ?: return "No hostname in URL"
        if (isBlockedHostname(hostname)) {
            return "Blocked hostname: $hostname"
        }

        // Resolve and check IP
        return try {
            val addresses = InetAddress.getAllByName(hostname)
            val blocked = addresses.find { isBlockedIpAddress(it) }
            if (blocked != null) {
                "Hostname $hostname resolves to blocked IP: ${blocked.hostAddress}"
            } else {
                null // safe
            }
        } catch (e: Exception) {
            "DNS resolution failed for $hostname: ${e.message}"
        }
    }

    // --- IPv4 special-use detection (aligned with shared/net/ip.ts) ---

    /** Check if an IPv4 address is in a special-use block (RFC 6890). */
    fun isBlockedSpecialUseIpv4(address: Inet4Address): Boolean {
        val b = address.address
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF

        // 0.0.0.0/8 - this host
        if (b0 == 0) return true
        // 10.0.0.0/8 - private
        if (b0 == 10) return true
        // 100.64.0.0/10 - shared address (CGNAT)
        if (b0 == 100 && b1 in 64..127) return true
        // 127.0.0.0/8 - loopback
        if (b0 == 127) return true
        // 169.254.0.0/16 - link-local
        if (b0 == 169 && b1 == 254) return true
        // 172.16.0.0/12 - private
        if (b0 == 172 && b1 in 16..31) return true
        // 192.0.0.0/24 - IETF protocol
        if (b0 == 192 && b1 == 0 && (b[2].toInt() and 0xFF) == 0) return true
        // 192.168.0.0/16 - private
        if (b0 == 192 && b1 == 168) return true
        // 198.18.0.0/15 - benchmarking
        if (b0 == 198 && b1 in 18..19) return true
        // 240.0.0.0/4 - reserved
        if (b0 >= 240) return true
        // 255.255.255.255 - broadcast
        if (b.all { (it.toInt() and 0xFF) == 255 }) return true

        return false
    }

    /** Parse a hostname or IP string to InetAddress, returns null on failure. */
    fun parseAddress(host: String): InetAddress? {
        return try {
            InetAddress.getByName(normalizeHostname(host))
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * TLS fingerprint normalization.
 * Aligned with OpenClaw/src/infra/tls/fingerprint.ts
 */
fun normalizeFingerprint(input: String): String {
    val trimmed = input.trim()
    val withoutPrefix = trimmed.replace(Regex("^sha-?256\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
    return withoutPrefix.replace(Regex("[^a-fA-F0-9]"), "").lowercase()
}
