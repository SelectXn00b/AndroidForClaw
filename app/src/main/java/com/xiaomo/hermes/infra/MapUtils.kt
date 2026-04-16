package com.xiaomo.hermes.infra

import kotlin.math.floor
import kotlin.math.max

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/map-size.ts + json-utf8-bytes.ts + file-identity.ts + path-safety.ts
 *
 * Small portable utilities from infra.
 */

// --- map-size.ts ---

/** Prune a LinkedHashMap to at most [maxSize] entries, removing oldest first. */
fun <K, V> pruneMapToMaxSize(map: MutableMap<K, V>, maxSize: Int) {
    val limit = max(0, floor(maxSize.toDouble()).toInt())
    if (limit <= 0) {
        map.clear()
        return
    }
    val iter = map.iterator()
    while (map.size > limit && iter.hasNext()) {
        iter.next()
        iter.remove()
    }
}

// --- json-utf8-bytes.ts ---

/** Estimate UTF-8 byte length of a JSON-serialized value. */
fun jsonUtf8Bytes(jsonString: String): Int {
    return jsonString.toByteArray(Charsets.UTF_8).size
}

// --- path-safety.ts ---

/** Check if [child] path is contained within [parent] directory. */
fun isPathInside(child: java.io.File, parent: java.io.File): Boolean {
    val canonicalChild = child.canonicalPath
    val canonicalParent = parent.canonicalPath
    return canonicalChild.startsWith(canonicalParent + java.io.File.separator) ||
        canonicalChild == canonicalParent
}

// --- pairing-token.ts ---

const val PAIRING_TOKEN_BYTES = 32

/** Generate a base64url pairing token. */
fun generatePairingToken(): String = generateSecureToken(PAIRING_TOKEN_BYTES)

/** Timing-safe verify a pairing token. */
fun verifyPairingToken(provided: String, expected: String): Boolean {
    if (provided.isBlank() || expected.isBlank()) return false
    return safeEqualSecret(provided, expected)
}
